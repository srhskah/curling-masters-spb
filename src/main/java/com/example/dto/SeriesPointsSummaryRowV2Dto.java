package com.example.dto;

import java.util.Map;

public class SeriesPointsSummaryRowV2Dto {
    private String username;
    private boolean withdrawn;
    private Integer finalRank;
    private int finalPoints;
    private String bestKey;
    /**
     * 系列内任一赛事最好名次（1/2/3），用于金银铜高亮；若没有前三则为 null
     */
    private Integer bestFinish;
    /**
     * 每个赛事列对应的名次（1/2/3/...），用于在对应赛事的积分单元格做金银铜高亮
     */
    private Map<String, Integer> finishByKey;
    private Map<String, Integer> pointsByKey;

    public SeriesPointsSummaryRowV2Dto() {}

    public SeriesPointsSummaryRowV2Dto(
            String username,
            boolean withdrawn,
            Integer finalRank,
            int finalPoints,
            String bestKey,
            Integer bestFinish,
            Map<String, Integer> finishByKey,
            Map<String, Integer> pointsByKey
    ) {
        this.username = username;
        this.withdrawn = withdrawn;
        this.finalRank = finalRank;
        this.finalPoints = finalPoints;
        this.bestKey = bestKey;
        this.bestFinish = bestFinish;
        this.finishByKey = finishByKey;
        this.pointsByKey = pointsByKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isWithdrawn() {
        return withdrawn;
    }

    public void setWithdrawn(boolean withdrawn) {
        this.withdrawn = withdrawn;
    }

    public Integer getFinalRank() {
        return finalRank;
    }

    public void setFinalRank(Integer finalRank) {
        this.finalRank = finalRank;
    }

    public int getFinalPoints() {
        return finalPoints;
    }

    public void setFinalPoints(int finalPoints) {
        this.finalPoints = finalPoints;
    }

    public String getBestKey() {
        return bestKey;
    }

    public void setBestKey(String bestKey) {
        this.bestKey = bestKey;
    }

    public Integer getBestFinish() {
        return bestFinish;
    }

    public void setBestFinish(Integer bestFinish) {
        this.bestFinish = bestFinish;
    }

    public Map<String, Integer> getFinishByKey() {
        return finishByKey;
    }

    public void setFinishByKey(Map<String, Integer> finishByKey) {
        this.finishByKey = finishByKey;
    }

    public Map<String, Integer> getPointsByKey() {
        return pointsByKey;
    }

    public void setPointsByKey(Map<String, Integer> pointsByKey) {
        this.pointsByKey = pointsByKey;
    }
}

