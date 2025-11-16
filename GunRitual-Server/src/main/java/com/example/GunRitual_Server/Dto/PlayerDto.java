package com.example.GunRitual_Server.Dto;

public class PlayerDto {
    public String id;
    public String nickname;
    public float x;
    public float y;
    public String currentState;
    public float dirX;
    public float dirY;

    public PlayerDto() {}

    public PlayerDto(String id, float x, float y, float dirX, float dirY, String currentState, String nickname) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.dirX = dirX;
        this.dirY = dirY;
        this.currentState = currentState;
        this.nickname = nickname;
    }
}
