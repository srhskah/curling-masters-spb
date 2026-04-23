package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.*;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.service.*;
import static com.example.service.impl.KnockoutBracketService.SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 资格赛赛程：按报名接龙最终顺序生成首轮单败对阵（phase=QUALIFIER）。<br>
 * 完整多轮与轮空规则见业务说明，此处实现「人数 &gt; 名额」时的首轮配对骨架，后续轮次可在管理端补录或迭代扩展。
 */
@Service
public class QualifierScheduleService {

    @Autowired private TournamentService tournamentService;
    @Autowired private IMatchService matchService;
    @Autowired private ITournamentRegistrationService registrationService;
    @Autowired private TournamentCompetitionConfigMapper competitionConfigMapper;

    @Transactional(rollbackFor = Exception.class)
    public int generateQualifierFirstRound(User operator, Long tournamentId) {
        if (operator == null || tournamentId == null) {
            throw new IllegalArgumentException("参数无效");
        }
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            throw new IllegalStateException("赛事不存在");
        }
        if (t.getHostUserId() == null || !t.getHostUserId().equals(operator.getId())) {
            if (operator.getRole() == null || operator.getRole() > 1) {
                throw new SecurityException("仅主办或管理员可生成资格赛赛程");
            }
        }
        TournamentCompetitionConfig cfg = competitionConfigMapper.selectById(tournamentId);
        if (cfg == null || cfg.getEntryMode() == null || cfg.getEntryMode() != 1) {
            throw new IllegalStateException("仅正赛+资格赛模式可生成资格赛");
        }
        Integer k = cfg.getKnockoutQualifyCount();
        if (k == null || k < 1) {
            throw new IllegalStateException("请先配置资格赛名额数");
        }
        TournamentRegistrationSetting reg = registrationService.getSetting(tournamentId);
        if (reg == null) {
            throw new IllegalStateException("报名未配置");
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> qualifierUids = new ArrayList<>();
        int m = reg.getMainDirectM() != null ? reg.getMainDirectM() : 0;
        List<com.example.dto.TournamentRegistrationRowDto> rows = registrationService.listRows(tournamentId, now);
        List<Long> eligibleOrder = rows.stream()
                .filter(com.example.dto.TournamentRegistrationRowDto::isEffectiveApproved)
                .map(com.example.dto.TournamentRegistrationRowDto::getUserId)
                .filter(Objects::nonNull)
                .toList();
        for (int i = m; i < eligibleOrder.size(); i++) {
            qualifierUids.add(eligibleOrder.get(i));
        }
        int q = qualifierUids.size();
        if (q <= k) {
            return 0;
        }
        // 仅清理“正赛资格赛”赛程，绝不影响“首轮淘汰赛附加资格赛”
        matchService.remove(Wrappers.<Match>lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "QUALIFIER")
                .and(qw -> qw.isNull(Match::getCreateSource)
                        .or()
                        .ne(Match::getCreateSource, SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER)));

        int roundLabel = 1;
        int matchIdx = 1;
        for (int i = 0; i + 1 < qualifierUids.size(); i += 2) {
            Match mch = new Match();
            mch.setTournamentId(tournamentId);
            mch.setCategory("资格赛 第" + roundLabel + "场 场次" + matchIdx);
            mch.setPhaseCode("QUALIFIER");
            mch.setQualifierRound(roundLabel);
            mch.setRound(roundLabel);
            // 与「首轮淘汰赛附加资格赛」区分：正赛资格赛不打 createSource
            mch.setCreateSource(null);
            mch.setPlayer1Id(qualifierUids.get(i));
            mch.setPlayer2Id(qualifierUids.get(i + 1));
            mch.setStatus((byte) 0);
            mch.setCreatedAt(now);
            mch.setUpdatedAt(now);
            matchService.save(mch);
            matchIdx++;
        }
        if (qualifierUids.size() % 2 == 1) {
            Long bye = qualifierUids.get(qualifierUids.size() - 1);
            Match mch = new Match();
            mch.setTournamentId(tournamentId);
            mch.setCategory("资格赛 第" + roundLabel + "场 轮空晋级");
            mch.setPhaseCode("QUALIFIER");
            mch.setQualifierRound(roundLabel);
            mch.setRound(roundLabel);
            mch.setCreateSource(null);
            mch.setPlayer1Id(bye);
            mch.setPlayer2Id(bye);
            mch.setWinnerId(bye);
            mch.setStatus((byte) 2);
            mch.setCreatedAt(now);
            mch.setUpdatedAt(now);
            matchService.save(mch);
        }
        return matchIdx - 1 + (qualifierUids.size() % 2);
    }
}
