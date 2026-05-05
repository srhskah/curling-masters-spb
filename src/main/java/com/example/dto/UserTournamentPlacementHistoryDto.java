package com.example.dto;

public record UserTournamentPlacementHistoryDto(
        long tournamentId,
        String tournamentName,
        String levelLabel,
        String seasonLabel,
        String seriesName,
        Integer finalRank,
        Integer points,
        String medalLabel
) {
    public String seasonSeriesLine() {
        String s = seasonLabel == null ? "" : seasonLabel.trim();
        String n = seriesName == null ? "" : seriesName.trim();
        if (s.isEmpty()) {
            return n;
        }
        if (n.isEmpty()) {
            return s;
        }
        return s + " · " + n;
    }
}
