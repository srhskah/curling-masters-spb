package com.example.dto;

public class TournamentRankingItemDto {
    /**
     * 与办赛分步赋分一致的名次（1 最好）；0 表示尚未定名次（仍在争冠）；退赛行亦为 0，请结合 {@link #withdrawn}。
     */
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

