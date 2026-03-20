package com.example.dto;

public class RankingListEntryDto {
    private int rank;
    private String username;
    private int points;

    public RankingListEntryDto() {
    }

    public RankingListEntryDto(int rank, String username, int points) {
        this.rank = rank;
        this.username = username;
        this.points = points;
    }

    public int getRank() {
        return rank;
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }
}

