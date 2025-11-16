package com.example.GunRitual_Server;

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

import java.io.IOException;
import java.util.*;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionManager sessionManager;

    public GameWebSocketHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
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
            default -> logger.warn("Неизвестный тип сообщения: {}", msg.type);
        }
    }
    private void handleStateChanged(WebSocketSession session, GameMessage msg) throws IOException
    {
        synchronized (sessionManager.getRoomLock(msg.roomId)) {
            broadcastToRoom(msg.roomId, msg, session);
        }
    }

    private void handleJoin(WebSocketSession session, GameMessage msg) throws IOException {
        String playerId = (String) session.getAttributes().get("playerId");
        logger.info("JOIN: Назначен playerId {}", playerId);

        synchronized (sessionManager.getRoomLock(msg.roomId)) {
            sessionManager.addToRoom(msg.roomId, session);

            // Создаём PlayerDto для нового игрока
            PlayerDto localPlayer = new PlayerDto(playerId, 440, 544,0,0, "Idle",
                    (msg.player != null) ? msg.player.nickname : "Player");

            // ACK новому игроку
            GameMessage ack = new GameMessage("JOIN_ACK", playerId, msg.roomId);
            ack.player = localPlayer;
            ack.existingPlayers = getExistingPlayers(session, msg.roomId);
            ack.existingBullets = new ArrayList<>();
            sendMessage(session, ack);

            // JOIN всем остальным
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
        if (roomId == null) return;

        synchronized (sessionManager.getRoomLock(roomId)) {
            sessionManager.removeFromRoom(roomId, session);

            String playerId = (String) session.getAttributes().get("playerId");
            broadcastToRoom(roomId, new GameMessage("LEAVE", playerId, roomId), session);
        }

        logger.info("Сессия {} закрыта. Статус: {}", session.getId(), status);
    }

    private List<PlayerDto> getExistingPlayers(WebSocketSession current, String roomId) {
        List<PlayerDto> players = new ArrayList<>();
        for (WebSocketSession s : sessionManager.getRoomSession(roomId)) {
            if (s == current)
                continue;
            String otherId = (String) s.getAttributes().get("playerId");
            players.add(new PlayerDto(otherId, 440, 544, 0, 0, "Idle", "Player"));
        }
        return players;
    }


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
