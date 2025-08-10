package com.example.GunRitual_Server;

import com.example.GunRitual_Server.Dto.GameMessage;
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
import java.util.UUID;

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
        System.out.println("Игрок подключился: " + session.getId());
        // Отправляем клиенту его ID
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"player_id\":\"" + playerId + "\"}"));
    }

    private void broadcastToRoom(String roomId, GameMessage msg, WebSocketSession exclude) throws IOException {
        String json;
        try {
            json = objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            logger.warn("Не удалось сериализовать {}", e.getMessage());
            return; // Прерываем, если не удалось сериализовать
        }

//        var sessions = sessionManager.getRoomSession(roomId);
//        sessions.forEach( session -> {
//            logger.warn("Сессия открыта {}", session.isOpen());
//            logger.warn("Ceccия == exclude {}", session == exclude);
//        });


        sessionManager.getRoomSession(roomId).stream()
                .filter(s -> s.isOpen() && s != exclude)
                .forEach(s -> {
                    try {

                        s.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        logger.warn("Не удалось отправить пакет на сессию {}: {}", s.getId(), e.getMessage());

                    }
                });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = sessionManager.getRoomIdBySession(session);

        if (roomId != null) {
            sessionManager.removeFromRoom(roomId, session);
            GameMessage leaveMsg = new GameMessage();
            leaveMsg.type = "leave";
            leaveMsg.playerId = "unknown"; // ты можешь хранить playerId в attributes
            leaveMsg.roomId = roomId;

            try {
                broadcastToRoom(roomId, leaveMsg, session);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        GameMessage msg = objectMapper.readValue(message.getPayload(), GameMessage.class);

        switch (msg.type) {
            case "JOIN":
                logger.info("JOIN: Принят пакет от {}",msg.playerId);
                sessionManager.addToRoom(msg.roomId, session);
                broadcastToRoom(msg.roomId, msg, session);
                break;
            case "MOVE":
                logger.info("MOVE: Принят пакет от {} сделал ход X:{}, Y:{}",msg.playerId, msg.x, msg.y);
                broadcastToRoom(msg.roomId, msg, session);
                break;
            case "LEAVE":
                logger.info("LEAVE: Принят пакет от {}",msg.playerId);
                sessionManager.removeFromRoom(msg.roomId, session);
                broadcastToRoom(msg.roomId, msg, session);
                break;
        }
    }
}
