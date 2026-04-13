package com.example.service.impl;

import com.example.entity.Tournament;
import com.example.entity.TournamentCompetitionConfig;
import com.example.entity.TournamentRegistrationSetting;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.mapper.TournamentRegistrationSettingMapper;
import com.example.service.TournamentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TournamentStatusScheduler {

    @Autowired
    private TournamentService tournamentService;
    
    @Autowired
    private TournamentRegistrationSettingMapper registrationSettingMapper;
    @Autowired
    private TournamentCompetitionConfigMapper competitionConfigMapper;
    @Autowired
    private TournamentCompetitionServiceImpl tournamentCompetitionService;

    /**
     * 每分钟检查一次报名截止的赛事
     * 注意：报名截止后不再自动更改赛事状态，需要管理员手动操作
     */
    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void autoUpdateTournamentStatus() {
        // 已禁用自动更改赛事状态功能
        // 报名截止后，管理员需要手动将赛事状态改为"进行中"
        
        // 可以在这里添加其他定时任务逻辑，例如：
        // - 发送报名截止通知
        // - 生成报名统计报告
        // - 等等
    }

    /**
     * 每分钟检查一次：小组赛截止后自动验收所有未验收小组赛。
     */
    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void autoAcceptOverdueGroupMatches() {
        LocalDateTime now = LocalDateTime.now();
        List<TournamentCompetitionConfig> overdue = competitionConfigMapper.selectList(
                Wrappers.<TournamentCompetitionConfig>lambdaQuery()
                        .isNotNull(TournamentCompetitionConfig::getGroupStageDeadline)
                        .le(TournamentCompetitionConfig::getGroupStageDeadline, now)
        );
        for (TournamentCompetitionConfig cfg : overdue) {
            if (cfg.getTournamentId() == null) continue;
            try {
                tournamentCompetitionService.autoAcceptOverdueGroupMatches(cfg.getTournamentId());
            } catch (Exception ignored) {
            }
        }
    }
}
