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
    public String displayName;
    public List<GameMessage> existingPlayers;
}
