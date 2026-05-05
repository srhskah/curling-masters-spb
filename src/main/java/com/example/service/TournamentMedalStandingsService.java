package com.example.service;

import com.example.dto.MedalTop3Resolution;
import com.example.dto.UserMedalByLevelDto;
import com.example.dto.UserMedalEventDto;
import com.example.dto.UserTournamentPlacementHistoryDto;
import com.example.entity.Tournament;

import java.util.List;

/**
 * 奖牌榜与单用户奖牌/赛事名次汇总（与 {@link com.example.controller.HomeController} 奖牌榜逻辑一致）。
 */
public interface TournamentMedalStandingsService {

    MedalTop3Resolution resolveTournamentTop3ByFinalRanking(Tournament tournament);

    List<UserMedalByLevelDto> summarizeUserMedalsByLevel(long userId);

    List<UserMedalEventDto> listUserMedalEvents(long userId);

    List<UserTournamentPlacementHistoryDto> buildUserTournamentPlacementHistory(long userId);
}
