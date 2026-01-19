package com.example.GunRitual_Server;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GameTimerService {

    private final SessionManager sessionManager;
    private final GameWebSocketHandler socketHandler;

    public GameTimerService(SessionManager sessionManager,
                            GameWebSocketHandler socketHandler) {
        this.sessionManager = sessionManager;
        this.socketHandler = socketHandler;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        for (String roomId : sessionManager.getRoomIds()) {
            GameRoomState state = sessionManager.getRoomState(roomId);
            if (state == null || state.finished)
                continue;

            long elapsed =
                    (System.currentTimeMillis() - state.startTimeMillis) / 1000;

            int remaining = state.getRemainingSeconds();

            if (remaining <= 0) {
                state.finished = true;
                socketHandler.sendGameFinished(roomId, state);
            } else {
                socketHandler.sendGameTime(roomId, remaining);
            }
        }
    }
}