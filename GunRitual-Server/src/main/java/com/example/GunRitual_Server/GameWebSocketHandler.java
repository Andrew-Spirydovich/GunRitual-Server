package com.example.GunRitual_Server;

import com.example.GunRitual_Server.Dto.BulletDto;
import com.example.GunRitual_Server.Dto.GameMessage;
import com.example.GunRitual_Server.Dto.PlayerDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.Console;
import java.io.IOException;
import java.util.*;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionManager sessionManager;
    private final BulletManager bulletManager;
    private final SpawnService spawnService;

    public GameWebSocketHandler(SessionManager sessionManager, BulletManager bulletManager, SpawnService spawnService) {
        this.sessionManager = sessionManager;
        this.bulletManager = bulletManager;
        this.spawnService = spawnService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String playerId = UUID.randomUUID().toString();
        session.getAttributes().put("playerId", playerId);

        logger.info("Игрок подключился: {}", session.getId());
        sendMessage(session, new GameMessage("CONNECTED", playerId, null));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        final GameMessage msg = objectMapper.readValue(message.getPayload(), GameMessage.class);

        switch (msg.type) {
            case "JOIN" -> handleJoin(session, msg);
            case "MOVE" -> handleMove(session, msg);
            case "LEAVE" -> handleLeave(session, msg);
            case "STATE_CHANGED" -> handleStateChanged(session, msg);
            case "SHOOT" -> handleShoot(session, msg);
            case "BULLET_HIT" -> handleBulletHit(session, msg);
            case "RESPAWN_REQUEST" -> handleRespawn(session, msg);
            default -> logger.warn("Неизвестный тип сообщения: {}", msg.type);
        }
    }

    private void handleRespawn(WebSocketSession session, GameMessage msg) {
        String roomId = msg.roomId;
        String playerId = msg.playerId;

        synchronized (sessionManager.getRoomLock(roomId)) {
            PlayerDto player = sessionManager.getPlayer(roomId, playerId);
            if (player == null)
                return;

            // защита от читов
            if (!player.isDead)
                return;

            // получаем точку респавна
            float[] spawn = spawnService.getNextSpawn(roomId);

            player.x = spawn[0];
            player.y = spawn[1];
            player.health = 100f;
            player.isDead = false;

            GameMessage respawnMsg = new GameMessage();
            respawnMsg.type = "PLAYER_RESPAWN";
            respawnMsg.roomId = roomId;
            respawnMsg.player = player;

            broadcastToRoom(roomId, respawnMsg, null);
        }
    }



    private void handleShoot(WebSocketSession session, GameMessage msg) throws IOException {
        String roomId = msg.roomId;
        String playerId = msg.playerId;
        String targetId = msg.bullet.targetId;
        synchronized (sessionManager.getRoomLock(roomId)) {

            // Создаём уникальный id пули
            BulletDto bullet = msg.bullet;
            bullet.id = UUID.randomUUID().toString();

            bulletManager.addBullet(roomId, bullet);

            GameMessage response = new GameMessage("BULLET_SPAWN", targetId, roomId);
            response.bullet = bullet;

            broadcastToRoom(roomId, response, null);
        }
    }

    private void handleBulletHit(WebSocketSession session, GameMessage msg) throws IOException {
        String roomId = msg.roomId;

        synchronized (sessionManager.getRoomLock(roomId)) {

            String bulletId = msg.bullet.id;
            bulletManager.removeBullet(roomId, bulletId);

            String targetId = msg.bullet.targetId;
            PlayerDto player = sessionManager.getPlayer(roomId, targetId);
            if (player == null)
                return;

            // Уменьшаем HP
            player.health -= msg.dmg;

            // Проверяем смерть
            if (player.health <= 0 && !player.isDead) {
                player.isDead = true;
                player.health = 0;
                logger.info("Игрок {} умер", targetId);
            }

            // Создаём новый объект BulletDto для DAMAGE
            BulletDto bulletDamage = new BulletDto();
            bulletDamage.id = bulletId;
            bulletDamage.ownerId = msg.bullet.ownerId;
            bulletDamage.targetId = targetId;

            // Отправляем DAMAGE всем игрокам
            GameMessage dmg = new GameMessage("DAMAGE", targetId, roomId, msg.dmg);
            dmg.bullet = bulletDamage;
            dmg.player = player; // теперь клиент знает новый HP и isDead
            broadcastToRoom(roomId, dmg, null);

            // Уведомляем всех об удалении пули
            GameMessage notify = new GameMessage("BULLET_REMOVE", msg.playerId, roomId);
            notify.bullet = msg.bullet;
            broadcastToRoom(roomId, notify, null);
        }
    }



    private void handleStateChanged(WebSocketSession session, GameMessage msg) throws IOException {
        synchronized (sessionManager.getRoomLock(msg.roomId)) {
            broadcastToRoom(msg.roomId, msg, session);
        }
    }
    private void handleJoin(WebSocketSession session, GameMessage msg) throws IOException {
        String playerId = (String) session.getAttributes().get("playerId");
        logger.info("JOIN: Назначен playerId {}", playerId);

        synchronized (sessionManager.getRoomLock(msg.roomId)) {
            sessionManager.addToRoom(msg.roomId, session);

            // Получаем индекс игрока в комнате
            int playerIndex = sessionManager.getPlayers(msg.roomId).size(); // до добавления

            // Получаем координаты из spawnService
            float[] spawn = spawnService.getSpawnByIndex(msg.roomId, playerIndex);

            // Создаём PlayerDto для нового игрока
            PlayerDto localPlayer = new PlayerDto(
                    playerId,
                    spawn[0],
                    spawn[1],
                    0,
                    0,
                    "Idle",
                    (msg.player != null) ? msg.player.nickname : "Player"
            );

            // Сохраняем игрока
            sessionManager.addPlayer(msg.roomId, localPlayer);

            // ACK новому игроку
            GameMessage ack = new GameMessage("JOIN_ACK", playerId, msg.roomId);
            ack.player = localPlayer;

            // Берем остальных игроков
            ack.existingPlayers = new ArrayList<>(sessionManager.getPlayers(msg.roomId));
            ack.existingPlayers.removeIf(p -> p.id.equals(playerId));

            ack.existingBullets = bulletManager.getBullets(msg.roomId).stream().toList();
            sendMessage(session, ack);

            // Уведомляем остальных
            GameMessage joinNotify = new GameMessage("JOIN", playerId, msg.roomId);
            joinNotify.player = localPlayer;

            broadcastToRoom(msg.roomId, joinNotify, session);
        }
    }


    private void handleMove(WebSocketSession session, GameMessage msg) throws IOException {
        synchronized (sessionManager.getRoomLock(msg.roomId)) {
            broadcastToRoom(msg.roomId, msg, session);
        }
    }

    private void handleLeave(WebSocketSession session, GameMessage msg) throws IOException {
        logger.info("LEAVE: Принят пакет от {}", msg.playerId);
        synchronized (sessionManager.getRoomLock(msg.roomId)) {

            String playerId = msg.playerId;
            sessionManager.removePlayer(msg.roomId, playerId);
            sessionManager.removeFromRoom(msg.roomId, session);

            broadcastToRoom(msg.roomId, msg, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Ошибка в сессии {}: {}", session.getId(), exception.getMessage(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = sessionManager.getRoomIdBySession(session);
        if (roomId == null)
            return;

        String playerId = (String) session.getAttributes().get("playerId");

        synchronized (sessionManager.getRoomLock(roomId)) {
            sessionManager.removePlayer(roomId, playerId);
            sessionManager.removeFromRoom(roomId, session);
            broadcastToRoom(roomId, new GameMessage("LEAVE", playerId, roomId), null);
        }

        logger.info("Сессия {} закрыта. Статус: {}", session.getId(), status);
    }

//    private List<PlayerDto> getExistingPlayers(WebSocketSession current, String roomId) {
//        List<PlayerDto> players = new ArrayList<>();
//        for (WebSocketSession s : sessionManager.getRoomSession(roomId)) {
//            if (s == current)
//                continue;
//            String otherId = (String) s.getAttributes().get("playerId");
//            players.add(new PlayerDto(otherId, 440, 544, 0, 0, "Idle", "Player"));
//        }
//        return players;
//    }


    private void broadcastToRoom(String roomId, GameMessage msg, WebSocketSession exclude) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            logger.warn( "Не удалось сериализовать {}", e.getMessage());
            return;
        }

        Set<WebSocketSession> sessions = new HashSet<>(sessionManager.getRoomSession(roomId));
        for (WebSocketSession s : sessions) {
            if (s == exclude) continue;

            try {
                synchronized (s) {
                    if (s.isOpen()) {
                        s.sendMessage(new TextMessage(json));
                    } else {
                        sessionManager.removeFromRoom(roomId, s);
                    }
                }
            } catch (IOException | IllegalStateException e) {
                // Сессия закрыта или ошибка отправки → удаляем
                sessionManager.removeFromRoom(roomId, s);
                logger.warn("Игрок {} отключился во время рассылки: {}", s.getId(), e.getMessage());
            }
        }
    }


    private void sendMessage(WebSocketSession session, GameMessage msg) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
    }

}
