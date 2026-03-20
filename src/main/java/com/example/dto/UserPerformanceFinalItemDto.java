package com.example.dto;

public class UserPerformanceFinalItemDto {
    private String seasonLabel;
    private String seriesName;
    private String levelName;
    private Integer points;

    public UserPerformanceFinalItemDto() {}

    public UserPerformanceFinalItemDto(String seasonLabel, String seriesName, String levelName, Integer points) {
        this.seasonLabel = seasonLabel;
        this.seriesName = seriesName;
        this.levelName = levelName;
        this.points = points;
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

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }
}

