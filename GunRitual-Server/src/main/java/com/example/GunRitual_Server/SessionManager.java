package com.example.GunRitual_Server;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public void addToRoom(String roomId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void removeFromRoom(String roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);

        if(sessions != null) {
            sessions.remove(session);

            if(sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
    }

    public Set<WebSocketSession> getRoomSession(String roomId) {
        return roomSessions.getOrDefault(roomId, Collections.emptySet());
    }

    public String getRoomIdBySession(WebSocketSession session) {
        return roomSessions.entrySet().stream()
                .filter(entry -> entry.getValue().contains(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
