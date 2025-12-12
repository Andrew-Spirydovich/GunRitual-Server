package com.example.GunRitual_Server;

import com.example.GunRitual_Server.Dto.BulletDto;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class BulletManager {

    private final Map<String, Map<String, BulletDto>> bulletsByRoom = new HashMap<>();

    public void addBullet(String roomId, BulletDto bullet) {
        bulletsByRoom
                .computeIfAbsent(roomId, id -> new HashMap<>())
                .put(bullet.id, bullet);
    }

    public Collection<BulletDto> getBullets(String roomId) {
        return bulletsByRoom.getOrDefault(roomId, Collections.emptyMap()).values();
    }

    public void removeBullet(String roomId, String bulletId) {
        Map<String, BulletDto> bullets = bulletsByRoom.get(roomId);
        if (bullets != null) bullets.remove(bulletId);
    }
}
