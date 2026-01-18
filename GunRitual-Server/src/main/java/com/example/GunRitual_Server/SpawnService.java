package com.example.GunRitual_Server;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SpawnService {

    // фиксированные точки
    private final List<float[]> spawnPoints = List.of(
            new float[]{260f, 540f},
            new float[]{80f, 540f},
            new float[]{670f, 540f},
            new float[]{940f, 540f},
            new float[]{1100f, 540f}
    );

    // чтобы игроки респавнились по порядку, используем индекс на комнату
    private final Map<String, Integer> roomSpawnIndex = new HashMap<>();

    public float[] getNextSpawn(String roomId) {
        int idx = roomSpawnIndex.getOrDefault(roomId, 0);
        float[] pos = spawnPoints.get(idx);

        // увеличиваем индекс для следующего респавна
        idx = (idx + 1) % spawnPoints.size();
        roomSpawnIndex.put(roomId, idx);

        return pos;
    }

    public float[] getSpawnByIndex(String roomId, int index) {
        return spawnPoints.get(index % spawnPoints.size());
    }

}
