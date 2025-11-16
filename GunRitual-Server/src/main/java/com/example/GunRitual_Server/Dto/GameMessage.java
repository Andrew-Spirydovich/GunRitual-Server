package com.example.GunRitual_Server.Dto;

import java.util.List;

public class GameMessage {
    public String type;
    public String playerId;
    public String roomId;

    public PlayerDto player;
    public BulletDto bullet;

    public List<PlayerDto> existingPlayers;
    public List<BulletDto> existingBullets;

    public GameMessage() {}

    public GameMessage(String type, String playerId, String roomId) {
        this.type = type;
        this.playerId = playerId;
        this.roomId = roomId;
    }
}
