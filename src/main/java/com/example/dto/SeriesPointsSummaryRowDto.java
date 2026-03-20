package com.example.dto;

public class SeriesPointsSummaryRowDto {
    private String username;
    private int finalPoints;
    private Integer cm1000Points;
    private Integer cm500Points;
    private Integer cm250Points;
    private boolean withdrawn;

    public SeriesPointsSummaryRowDto() {
    }

    public SeriesPointsSummaryRowDto(
            String username,
            int finalPoints,
            Integer cm1000Points,
            Integer cm500Points,
            Integer cm250Points,
            boolean withdrawn
    ) {
        this.username = username;
        this.finalPoints = finalPoints;
        this.cm1000Points = cm1000Points;
        this.cm500Points = cm500Points;
        this.cm250Points = cm250Points;
        this.withdrawn = withdrawn;
    }

    public String getUsername() {
        return username;
    }

    public int getFinalPoints() {
        return finalPoints;
    }

    public Integer getCm1000Points() {
        return cm1000Points;
    }

    public Integer getCm500Points() {
        return cm500Points;
    }

    public Integer getCm250Points() {
        return cm250Points;
    }

    public boolean isWithdrawn() {
        return withdrawn;
    }
}

