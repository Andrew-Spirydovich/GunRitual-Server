package com.example.GunRitual_Server.Dto;

import java.util.List;

public class GameMessage {
    public String type;
    public String playerId;
    public String roomId;
    public float x;
    public float y;
    public float dirX;
    public float dirY;
    public float velX;
    public float velY;
    public String currentState;
    public String displayName;
    public List<GameMessage> existingPlayers;

    public GameMessage() {}

    public GameMessage(String type, String playerId, String roomId) {
        this.type = type;
        this.playerId = playerId;
        this.roomId = roomId;
    }

    public GameMessage(String type, String playerId, String roomId, float x, float y) {
        this(type, playerId, roomId);
        this.x = x;
        this.y = y;
    }
}
