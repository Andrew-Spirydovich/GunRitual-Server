package com.example.GunRitual_Server;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, Object> roomLocks = new ConcurrentHashMap<>();

    public Object getRoomLock(String roomId) {
        return roomLocks.computeIfAbsent(roomId, id -> new Object());
    }

    public void addToRoom(String roomId, WebSocketSession session) {
        synchronized (getRoomLock(roomId)) {
            roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    public void removeFromRoom(String roomId, WebSocketSession session) {
        synchronized (getRoomLock(roomId)) {
            Set<WebSocketSession> sessions = roomSessions.get(roomId);

            if (sessions != null) {
                sessions.remove(session);

                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                    roomLocks.remove(roomId);
                }
            }
        }
    }

    public Set<WebSocketSession> getRoomSession(String roomId) {
        synchronized (getRoomLock(roomId)) {
            return new HashSet<>(roomSessions.getOrDefault(roomId, Collections.emptySet()));
        }
    }

    public String getRoomIdBySession(WebSocketSession session) {
        // пробегаем по копии, чтобы не падало при изменении
        for (Map.Entry<String, Set<WebSocketSession>> entry : new HashMap<>(roomSessions).entrySet()) {
            if (entry.getValue().contains(session)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
