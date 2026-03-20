package com.example.service;

import com.example.dto.RankingEntry;

import java.util.List;

public interface RankingService {
    List<RankingEntry> getTotalRanking(Integer limit);
    List<RankingEntry> getSeasonRanking(Long seasonId, Integer limit);
    List<RankingEntry> getSeasonRankingAllSeasonsMerged(Integer limit);
}

