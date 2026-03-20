package com.example.dto;

public class RankingEntry {
    private final Long userId;
    private final String username;
    private final int points;

    public RankingEntry(Long userId, String username, int points) {
        this.userId = userId;
        this.username = username;
        this.points = points;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }
}

