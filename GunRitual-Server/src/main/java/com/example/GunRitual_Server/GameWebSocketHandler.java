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
        System.out.println("Игрок подключился: " + session.getId());
    }

    private void broadcastToRoom(String roomId, GameMessage msg, WebSocketSession exclude) throws IOException {
        String json;
        try {
            json = objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return; // Прерываем, если не удалось сериализовать
        }

        sessionManager.getRoomSession(roomId).stream()
                .filter(s -> s.isOpen() && s != exclude)
                .forEach(s -> {
                    try {
                        s.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        e.printStackTrace(); // можно заменить на логгер
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
                sessionManager.addToRoom(msg.roomId, session);
                broadcastToRoom(msg.roomId, msg, session);
                break;
            case "MOVE":
                logger.info("Игрок {} сделал ход X:{}, Y:{}",msg.playerId, msg.x, msg.y);
                broadcastToRoom(msg.roomId, msg, session);
                break;
            case "LEAVE":
                sessionManager.removeFromRoom(msg.roomId, session);
                broadcastToRoom(msg.roomId, msg, session);
                break;
        }
    }
}
