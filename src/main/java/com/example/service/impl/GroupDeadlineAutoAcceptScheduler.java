package com.example.service.impl;

import com.example.entity.TournamentCompetitionConfig;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.service.ITournamentCompetitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GroupDeadlineAutoAcceptScheduler {

    @Autowired
    private TournamentCompetitionConfigMapper configMapper;

    @Autowired
    private ITournamentCompetitionService competitionService;

    /** 每分钟扫描一次：小组赛截止后自动验收全部未锁定小组赛。 */
    @Scheduled(cron = "0 * * * * ?")
    public void autoAcceptOverdueGroupMatches() {
        LocalDateTime now = LocalDateTime.now();
        List<TournamentCompetitionConfig> configs = configMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentCompetitionConfig>lambdaQuery()
                        .isNotNull(TournamentCompetitionConfig::getGroupStageDeadline)
                        .le(TournamentCompetitionConfig::getGroupStageDeadline, now)
        );
        for (TournamentCompetitionConfig c : configs) {
            if (c.getTournamentId() != null) {
                competitionService.autoAcceptOverdueGroupMatches(c.getTournamentId());
            }
        }
    }

    /** 每分钟扫描一次：各阶段淘汰赛截止后自动验收该轮未锁定场次。 */
    @Scheduled(cron = "0 * * * * ?")
    public void autoAcceptOverdueKnockoutMatches() {
        LocalDateTime now = LocalDateTime.now();
        List<TournamentCompetitionConfig> configs = configMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentCompetitionConfig>lambdaQuery()
                        .and(w -> w
                                .isNotNull(TournamentCompetitionConfig::getKnockoutRound16Deadline)
                                .or().isNotNull(TournamentCompetitionConfig::getKnockoutRound8Deadline)
                                .or().isNotNull(TournamentCompetitionConfig::getKnockoutRound4Deadline)
                                .or().isNotNull(TournamentCompetitionConfig::getKnockoutRound2Deadline)
                                .or().isNotNull(TournamentCompetitionConfig::getKnockoutRound1Deadline)
                                .or().isNotNull(TournamentCompetitionConfig::getKnockoutQualifierDeadline))
        );
        for (TournamentCompetitionConfig c : configs) {
            if (c.getTournamentId() == null) {
                continue;
            }
            boolean due = false;
            if (c.getKnockoutQualifierDeadline() != null && !now.isBefore(c.getKnockoutQualifierDeadline())) {
                due = true;
            }
            if (!due) {
                java.util.List<Integer> rounds = TournamentCompetitionConfig.knockoutRoundsFromStart(c.getKnockoutStartRound());
                if (rounds.isEmpty()) {
                    rounds = java.util.List.of(16, 8, 4, 2, 1);
                }
                for (int round : rounds) {
                    LocalDateTime d = c.getKnockoutDeadlineForRound(round);
                    if (d != null && !now.isBefore(d)) {
                        due = true;
                        break;
                    }
                }
            }
            if (due) {
                try {
                    competitionService.autoAcceptOverdueKnockoutMatches(c.getTournamentId());
                } catch (Exception ignored) {
                }
            }
        }
    }
}
