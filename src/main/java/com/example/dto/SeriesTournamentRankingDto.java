package com.example.dto;

import java.util.List;

public class SeriesTournamentRankingDto {
    private Long seriesId;
    private String seriesLabel;
    private List<TournamentRankingSectionDto> tournaments;

    public SeriesTournamentRankingDto() {
    }

    public SeriesTournamentRankingDto(Long seriesId, String seriesLabel, List<TournamentRankingSectionDto> tournaments) {
        this.seriesId = seriesId;
        this.seriesLabel = seriesLabel;
        this.tournaments = tournaments;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public String getSeriesLabel() {
        return seriesLabel;
    }

    public List<TournamentRankingSectionDto> getTournaments() {
        return tournaments;
    }
}

