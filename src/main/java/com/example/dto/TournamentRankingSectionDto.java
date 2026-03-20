package com.example.dto;

import java.util.List;

public class TournamentRankingSectionDto {
    private Long tournamentId;
    private String tournamentLabel;
    private Integer edition;
    private Integer status;
    private String statusLabel;
    private List<TournamentRankingItemDto> rankings;

    public TournamentRankingSectionDto() {
    }

    public TournamentRankingSectionDto(
            Long tournamentId,
            String tournamentLabel,
            Integer edition,
            Integer status,
            String statusLabel,
            List<TournamentRankingItemDto> rankings
    ) {
        this.tournamentId = tournamentId;
        this.tournamentLabel = tournamentLabel;
        this.edition = edition;
        this.status = status;
        this.statusLabel = statusLabel;
        this.rankings = rankings;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public String getTournamentLabel() {
        return tournamentLabel;
    }

    public Integer getEdition() {
        return edition;
    }

    public Integer getStatus() {
        return status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public List<TournamentRankingItemDto> getRankings() {
        return rankings;
    }
}

