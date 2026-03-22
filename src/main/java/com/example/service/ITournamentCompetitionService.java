package com.example.service;

import com.example.entity.*;

import java.util.List;
import java.util.Map;

public interface ITournamentCompetitionService {
    TournamentCompetitionConfig getConfig(Long tournamentId);
    TournamentCompetitionConfig saveConfig(User operator, TournamentCompetitionConfig form);
    List<Integer> calcGroupSizeOptions(Integer participantCount);
    void autoFillGroupMembersFromRegistration(User operator, Long tournamentId);
    void saveGroupMembers(User operator, Long tournamentId, Map<Long, List<Long>> groupUserMap);
    void generateGroupMatches(User operator, Long tournamentId);
    boolean canEditMatchScore(User operator, Match match);
    void saveMatchScore(User operator, Long matchId, Integer firstEndHammer, List<String> player1Scores, List<String> player2Scores,
                        Boolean autoAccept, String signature);
    void acceptMatchScore(User operator, Long matchId, String signature);
}
