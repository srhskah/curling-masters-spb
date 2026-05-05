package com.example.dto;

public record UserMedalEventDto(
        long tournamentId,
        String tournamentName,
        String levelLabel,
        String seasonLabel,
        String seriesName,
        int medalPlace,
        String medalLabel
) {
    /** 与赛事详情页一致的「赛季 · 系列」一行展示 */
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
