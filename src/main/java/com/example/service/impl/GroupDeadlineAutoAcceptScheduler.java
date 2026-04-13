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
}
