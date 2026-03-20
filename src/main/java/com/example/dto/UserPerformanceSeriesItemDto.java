package com.example.dto;

public class UserPerformanceSeriesItemDto {
    private String seasonLabel;
    private String seriesName;
    private Integer seriesSequence;
    private Integer points;
    private boolean countedInTop10;

    public UserPerformanceSeriesItemDto() {}

    public UserPerformanceSeriesItemDto(String seasonLabel, String seriesName, Integer seriesSequence, Integer points, boolean countedInTop10) {
        this.seasonLabel = seasonLabel;
        this.seriesName = seriesName;
        this.seriesSequence = seriesSequence;
        this.points = points;
        this.countedInTop10 = countedInTop10;
    }

    public String getSeasonLabel() {
        return seasonLabel;
    }

    public void setSeasonLabel(String seasonLabel) {
        this.seasonLabel = seasonLabel;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }

    public Integer getSeriesSequence() {
        return seriesSequence;
    }

    public void setSeriesSequence(Integer seriesSequence) {
        this.seriesSequence = seriesSequence;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public boolean isCountedInTop10() {
        return countedInTop10;
    }

    public void setCountedInTop10(boolean countedInTop10) {
        this.countedInTop10 = countedInTop10;
    }
}

