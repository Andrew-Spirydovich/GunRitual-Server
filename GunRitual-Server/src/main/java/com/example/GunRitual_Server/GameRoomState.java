package com.example.GunRitual_Server;

import ch.qos.logback.core.testUtil.DelayingListAppender;
import org.springframework.boot.origin.SystemEnvironmentOrigin;

import java.util.HashMap;
import java.util.Map;

public class GameRoomState {
    public long startTimeMillis;
    public int matchDurationSeconds = 300; // 5 минут
    public boolean finished = false;

    public Map<String, Integer> scores = new HashMap<>();

    public int getRemainingSeconds() {
        long elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000;
        int remaining = matchDurationSeconds - (int) elapsed;
        return Math.max(remaining, 0);
    }
}