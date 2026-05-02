package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.TournamentCompetitionConfig;
import com.example.entity.TournamentEntry;
import com.example.entity.TournamentGroupMember;
import com.example.entity.UserTournamentPoints;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.service.TournamentEntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 正赛+资格赛（entryMode=1）下：仅「直通车 / 资格赛晋级 / 替补」写入名单或已入小组的选手参与赛事最终排名展示与录入；
 * 纯资格赛报名侧、未入选名单者不计入该赛事排名（避免误录入）。
 */
@Service
public class TournamentRankingRosterService {

    @Autowired private TournamentCompetitionConfigMapper competitionConfigMapper;
    @Autowired private TournamentEntryService tournamentEntryService;
    @Autowired private TournamentGroupMemberMapper tournamentGroupMemberMapper;

    public boolean usesMainPlusQualifierMode(Long tournamentId) {
        if (tournamentId == null) {
            return false;
        }
        TournamentCompetitionConfig c = competitionConfigMapper.selectById(tournamentId);
        return c != null && c.getEntryMode() != null && c.getEntryMode() == 1;
    }

    public Set<Long> rosterUserIdsForEventRanking(Long tournamentId) {
        Set<Long> out = new HashSet<>();
        tournamentEntryService.lambdaQuery()
                .eq(TournamentEntry::getTournamentId, tournamentId)
                .list()
                .forEach(e -> {
                    if (e.getUserId() != null) {
                        out.add(e.getUserId());
                    }
                });
        tournamentGroupMemberMapper.selectList(Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tournamentId))
                .forEach(m -> {
                    if (m.getUserId() != null) {
                        out.add(m.getUserId());
                    }
                });
        return out;
    }

    public List<UserTournamentPoints> filterUtpsForDisplay(Long tournamentId, List<UserTournamentPoints> utps) {
        if (utps == null || utps.isEmpty()) {
            return utps == null ? List.of() : utps;
        }
        if (!usesMainPlusQualifierMode(tournamentId)) {
            return utps;
        }
        Set<Long> roster = rosterUserIdsForEventRanking(tournamentId);
        // 尚无正赛入选/小组名单数据时，不按名单误伤全体（否则展示与积分均为空）
        if (roster.isEmpty()) {
            return utps;
        }
        List<UserTournamentPoints> filtered = new ArrayList<>(utps.size());
        for (UserTournamentPoints u : utps) {
            if (u.getUserId() == null || roster.contains(u.getUserId())) {
                filtered.add(u);
            }
        }
        return filtered;
    }

    /** 手动录入排名时跳过：资格赛侧未进名单者 */
    public boolean shouldOmitUserFromManualRankingSave(Long tournamentId, Long userId) {
        if (userId == null) {
            return false;
        }
        if (!usesMainPlusQualifierMode(tournamentId)) {
            return false;
        }
        Set<Long> roster = rosterUserIdsForEventRanking(tournamentId);
        if (roster.isEmpty()) {
            return false;
        }
        return !roster.contains(userId);
    }
}
