package com.example.dto;

public class TournamentRankingItemDto {
    private int rank;
    private String username;
    private int points;
    private boolean withdrawn;
    /** 小组赛总排名（跨组）；无数据时为 null */
    private Integer groupOverallRank;

    public TournamentRankingItemDto() {
    }

    public TournamentRankingItemDto(int rank, String username, int points, boolean withdrawn) {
        this.rank = rank;
        this.username = username;
        this.points = points;
        this.withdrawn = withdrawn;
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

    public boolean isWithdrawn() {
        return withdrawn;
    }

    public Integer getGroupOverallRank() {
        return groupOverallRank;
    }

    public void setGroupOverallRank(Integer groupOverallRank) {
        this.groupOverallRank = groupOverallRank;
    }
}

