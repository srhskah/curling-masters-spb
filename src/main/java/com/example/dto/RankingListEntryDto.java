package com.example.dto;

public class RankingListEntryDto {
    private int rank;
    private String username;
    private Long userId;
    private int points;

    public RankingListEntryDto() {
    }

    public RankingListEntryDto(int rank, Long userId, String username, int points) {
        this.rank = rank;
        this.userId = userId;
        this.username = username;
        this.points = points;
    }

    public int getRank() {
        return rank;
    }

    public String getUsername() {
        return username;
    }

    public Long getUserId() {
        return userId;
    }

    public int getPoints() {
        return points;
    }
}

