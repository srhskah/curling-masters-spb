package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.*;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.mapper.TournamentGroupMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.service.GroupRankingCalculator;
import com.example.service.IMatchService;
import com.example.service.ISetScoreService;
import com.example.service.TournamentService;
import com.example.service.UserService;
import com.example.service.knockout.KnockoutPairingUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnockoutBracketService {
    public static final String SOURCE_AUTO_FROM_GROUP = "AUTO_FROM_GROUP";
    public static final String SOURCE_MANUAL_KO_EDITOR = "MANUAL_KO_EDITOR";
    public static final String SOURCE_AUTO_BRACKET_ADVANCE = "AUTO_BRACKET_ADVANCE";

    @Autowired private TournamentGroupMapper groupMapper;
    @Autowired private TournamentGroupMemberMapper groupMemberMapper;
    @Autowired private IMatchService matchService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private TournamentCompetitionConfigMapper configMapper;
    @Autowired private GroupRankingCalculator groupRankingCalculator;
    @Autowired private UserService userService;
    @Autowired private TournamentService tournamentService;

    public static int playersInFirstKnockoutRound(Integer knockoutStartRound) {
        if (knockoutStartRound == null) {
            return 0;
        }
        if (Objects.equals(knockoutStartRound, 2)) {
            return 4;
        }
        return knockoutStartRound;
    }

    public static int nextKnockoutRoundField(int current) {
        if (current <= 1) {
            return 1;
        }
        if (current <= 4) {
            return 1;
        }
        return current / 2;
    }

    public boolean isGroupStageFullyLocked(Long tournamentId) {
        long total = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .count();
        if (total == 0) {
            return false;
        }
        long locked = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .eq(Match::getResultLocked, true)
                .count();
        return total == locked;
    }

    public boolean hasAnyKnockoutMatch(Long tournamentId) {
        return matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .count() > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAllKnockoutMatches(User operator, Long tournamentId) {
        if (!canManage(operator, tournamentId)) {
            throw new SecurityException("无权删除淘汰赛");
        }
        List<Match> ko = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .list();
        for (Match m : ko) {
            setScoreService.lambdaUpdate().eq(SetScore::getMatchId, m.getId()).remove();
            matchService.removeById(m.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int generateFirstKnockoutRound(User operator, Long tournamentId) {
        if (!canManage(operator, tournamentId)) {
            throw new SecurityException("无权生成淘汰赛");
        }
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        if (cfg == null || !Objects.equals(cfg.getMatchMode(), 3)) {
            throw new IllegalStateException("仅小组赛模式可自小组赛生成淘汰赛");
        }
        if (cfg.getKnockoutStartRound() == null) {
            throw new IllegalStateException("请先配置淘汰赛首轮");
        }
        if (cfg.getQualifierRound() != null) {
            throw new IllegalStateException("首轮挂载资格赛时请先完成资格赛（后续版本将自动衔接）");
        }
        if (!isGroupStageFullyLocked(tournamentId)) {
            throw new IllegalStateException("小组赛须全部验收后才可生成淘汰赛");
        }
        int bracketPlayers = playersInFirstKnockoutRound(cfg.getKnockoutStartRound());
        List<Long> ranked = loadOverallRankedUserIds(tournamentId, cfg);
        if (ranked.size() < bracketPlayers) {
            throw new IllegalStateException("人数不足首轮规模（需 " + bracketPlayers + " 人，当前 " + ranked.size() + "）");
        }
        List<Long> take = new ArrayList<>(ranked.subList(0, bracketPlayers));
        int mode = cfg.getKnockoutBracketMode() == null ? 0 : cfg.getKnockoutBracketMode();
        List<PlannedKo> planned = planFirstRound(cfg, take, tournamentId, mode);
        if (planned.isEmpty()) {
            throw new IllegalStateException("未能生成首轮对阵");
        }
        deleteAllKnockoutMatches(operator, tournamentId);
        int koSets = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : 8;
        int startRoundField = cfg.getKnockoutStartRound();
        for (int i = 0; i < planned.size(); i++) {
            PlannedKo p = planned.get(i);
            insertKoMatch(tournamentId, startRoundField, i, p.half, p.p1, p.p2, p.category, null, null, koSets, "MAIN",
                    operator == null ? null : operator.getId(), SOURCE_AUTO_FROM_GROUP);
        }
        return planned.size();
    }

    public List<Long> loadEligibleFirstRoundPlayers(User operator, Long tournamentId) {
        if (!canManage(operator, tournamentId)) {
            throw new SecurityException("无权查看手动排签候选");
        }
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        if (cfg == null || !Objects.equals(cfg.getMatchMode(), 3)) {
            throw new IllegalStateException("仅小组赛模式支持手动首轮排签");
        }
        if (cfg.getKnockoutStartRound() == null) {
            throw new IllegalStateException("请先配置淘汰赛首轮");
        }
        if (!isGroupStageFullyLocked(tournamentId)) {
            throw new IllegalStateException("小组赛须全部验收后才可排签淘汰赛");
        }
        int bracketPlayers = playersInFirstKnockoutRound(cfg.getKnockoutStartRound());
        List<Long> ranked = loadOverallRankedUserIds(tournamentId, cfg);
        if (ranked.size() < bracketPlayers) {
            throw new IllegalStateException("人数不足首轮规模（需 " + bracketPlayers + " 人，当前 " + ranked.size() + "）");
        }
        return new ArrayList<>(ranked.subList(0, bracketPlayers));
    }

    public List<ManualPairDraft> buildManualFirstRoundDraft(User operator, Long tournamentId) {
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        List<Long> eligible = loadEligibleFirstRoundPlayers(operator, tournamentId);
        List<int[]> pairs = KnockoutPairingUtil.classicOverallRankPairs(eligible.size());
        List<ManualPairDraft> out = new ArrayList<>();
        int startRoundField = cfg.getKnockoutStartRound();
        int idx = 0;
        for (int[] pr : pairs) {
            out.add(new ManualPairDraft(
                    idx,
                    eligible.get(pr[0] - 1),
                    eligible.get(pr[1] - 1),
                    koRoundLabel(startRoundField) + " 第" + (idx + 1) + "场"
            ));
            idx++;
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public int generateFirstKnockoutRoundManual(User operator, Long tournamentId, List<ManualPairInput> manualPairs) {
        if (!canManage(operator, tournamentId)) {
            throw new SecurityException("无权手动排签淘汰赛");
        }
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        if (cfg == null || !Objects.equals(cfg.getMatchMode(), 3)) {
            throw new IllegalStateException("仅小组赛模式支持手动首轮排签");
        }
        if (cfg.getKnockoutStartRound() == null) {
            throw new IllegalStateException("请先配置淘汰赛首轮");
        }
        if (!isGroupStageFullyLocked(tournamentId)) {
            throw new IllegalStateException("小组赛须全部验收后才可排签淘汰赛");
        }
        int bracketPlayers = playersInFirstKnockoutRound(cfg.getKnockoutStartRound());
        int expectedMatches = bracketPlayers / 2;
        if (manualPairs == null || manualPairs.size() != expectedMatches) {
            throw new IllegalStateException("手动排签场次数量不正确（应为 " + expectedMatches + " 场）");
        }
        Set<Long> eligible = new LinkedHashSet<>(loadEligibleFirstRoundPlayers(operator, tournamentId));
        Set<Long> used = new LinkedHashSet<>();
        for (int i = 0; i < manualPairs.size(); i++) {
            ManualPairInput p = manualPairs.get(i);
            if (p == null || p.player1Id == null || p.player2Id == null) {
                throw new IllegalStateException("第 " + (i + 1) + " 场仍有未选择选手");
            }
            if (Objects.equals(p.player1Id, p.player2Id)) {
                throw new IllegalStateException("第 " + (i + 1) + " 场选手重复");
            }
            if (!eligible.contains(p.player1Id) || !eligible.contains(p.player2Id)) {
                throw new IllegalStateException("第 " + (i + 1) + " 场包含非小组赛晋级选手");
            }
            used.add(p.player1Id);
            used.add(p.player2Id);
        }
        if (used.size() != bracketPlayers) {
            throw new IllegalStateException("手动排签存在重复选人或遗漏选人");
        }
        deleteAllKnockoutMatches(operator, tournamentId);
        int koSets = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : 8;
        int startRoundField = cfg.getKnockoutStartRound();
        for (int i = 0; i < manualPairs.size(); i++) {
            ManualPairInput p = manualPairs.get(i);
            String category = "[手动排签] " + koRoundLabel(startRoundField) + " 第" + (i + 1) + "场";
            insertKoMatch(tournamentId, startRoundField, i, null, p.player1Id, p.player2Id, category, null, null, koSets, "MAIN",
                    operator == null ? null : operator.getId(), SOURCE_MANUAL_KO_EDITOR);
        }
        return manualPairs.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public void tryAutoGenerateFromGroupStage(User operator, Long tournamentId) {
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        if (cfg == null || Boolean.FALSE.equals(cfg.getKnockoutAutoFromGroup())) {
            return;
        }
        if (!Objects.equals(cfg.getMatchMode(), 3) || cfg.getKnockoutStartRound() == null) {
            return;
        }
        if (!isGroupStageFullyLocked(tournamentId) || hasAnyKnockoutMatch(tournamentId)) {
            return;
        }
        try {
            generateFirstKnockoutRound(operator, tournamentId);
        } catch (RuntimeException ignored) {
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void onKnockoutMatchLocked(User operator, Match match) {
        if (match == null || match.getTournamentId() == null) {
            return;
        }
        String pc = match.getPhaseCode();
        if (pc == null || (!"MAIN".equalsIgnoreCase(pc) && !"FINAL".equalsIgnoreCase(pc))) {
            return;
        }
        if (!Boolean.TRUE.equals(match.getResultLocked())) {
            return;
        }
        tryAdvance(match);
    }

    private void tryAdvance(Match m) {
        TournamentCompetitionConfig cfg = configMapper.selectById(m.getTournamentId());
        if (cfg == null) {
            return;
        }
        int koSets = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : 8;
        int r = m.getRound() == null ? 0 : m.getRound();
        if (r <= 1) {
            return;
        }
        Integer slot = m.getKnockoutBracketSlot();
        if (slot == null) {
            return;
        }
        Integer half = m.getKnockoutHalf();
        if (half == null) {
            int partnerSlot = (slot % 2 == 0) ? slot + 1 : slot - 1;
            Match partner = findKoMatch(m.getTournamentId(), r, partnerSlot, null);
            if (partner == null || !Boolean.TRUE.equals(partner.getResultLocked())) {
                return;
            }
            if (nextKnockoutRoundField(r) == 1) {
                createGoldAndBronze(m, partner, cfg);
                return;
            }
            int nextR = nextKnockoutRoundField(r);
            int parentSlot = Math.min(slot, partnerSlot) / 2;
            if (existsKoSlot(m.getTournamentId(), nextR, parentSlot, null)) {
                return;
            }
            insertKoMatch(m.getTournamentId(), nextR, parentSlot, null, m.getWinnerId(), partner.getWinnerId(),
                    labelKo(r, nextR, parentSlot, null), m.getId(), partner.getId(), koSets, "MAIN",
                    null, SOURCE_AUTO_BRACKET_ADVANCE);
            return;
        }
        if (r > 4) {
            int partnerSlot = (slot % 2 == 0) ? slot + 1 : slot - 1;
            Match partner = findKoMatch(m.getTournamentId(), r, partnerSlot, half);
            if (partner == null || !Boolean.TRUE.equals(partner.getResultLocked())) {
                return;
            }
            int nextR = r / 2;
            int slotInHalf = Math.min(slot, partnerSlot) / 2;
            if (existsKoSlotHalf(m.getTournamentId(), nextR, half, slotInHalf)) {
                return;
            }
            insertKoMatch(m.getTournamentId(), nextR, slotInHalf, half, m.getWinnerId(), partner.getWinnerId(),
                    labelKo(r, nextR, slotInHalf, half), m.getId(), partner.getId(), koSets, "MAIN",
                    null, SOURCE_AUTO_BRACKET_ADVANCE);
            return;
        }
        if (r == 4) {
            Match other = findKoMatchOtherHalf(m.getTournamentId(), 4, half);
            if (other == null || !Boolean.TRUE.equals(other.getResultLocked())) {
                return;
            }
            if (existsKoSlot(m.getTournamentId(), 1, 0, null)) {
                return;
            }
            createGoldAndBronze(m, other, cfg);
        }
    }

    private void createGoldAndBronze(Match semiA, Match semiB, TournamentCompetitionConfig cfg) {
        Long tid = semiA.getTournamentId();
        if (existsKoSlot(tid, 1, 0, null)) {
            return;
        }
        int koSets = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : 8;
        int finalSets = (cfg.getFinalStageSets() != null && cfg.getFinalStageSets() > 0) ? cfg.getFinalStageSets() : koSets;
        insertKoMatch(semiA.getTournamentId(), 1, 0, null, semiA.getWinnerId(), semiB.getWinnerId(),
                "金牌赛", semiA.getId(), semiB.getId(), finalSets, "FINAL", null, SOURCE_AUTO_BRACKET_ADVANCE);
        insertKoMatch(semiA.getTournamentId(), 1, 1, null, loserOf(semiA), loserOf(semiB),
                "铜牌赛", semiA.getId(), semiB.getId(), finalSets, "MAIN", null, SOURCE_AUTO_BRACKET_ADVANCE);
    }

    private Long loserOf(Match m) {
        Long w = m.getWinnerId();
        if (w == null) {
            return null;
        }
        if (Objects.equals(w, m.getPlayer1Id())) {
            return m.getPlayer2Id();
        }
        return m.getPlayer1Id();
    }

    private boolean existsKoSlot(Long tid, int round, int slot, Integer half) {
        LambdaQueryWrapper<Match> q = Wrappers.lambdaQuery();
        q.eq(Match::getTournamentId, tid);
        q.eq(Match::getRound, round);
        q.eq(Match::getKnockoutBracketSlot, slot);
        q.in(Match::getPhaseCode, "MAIN", "FINAL");
        if (half == null) {
            q.isNull(Match::getKnockoutHalf);
        } else {
            q.eq(Match::getKnockoutHalf, half);
        }
        return matchService.count(q) > 0;
    }

    private boolean existsKoSlotHalf(Long tid, int round, int half, int slotInHalf) {
        return matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, round)
                .eq(Match::getKnockoutHalf, half)
                .eq(Match::getKnockoutBracketSlot, slotInHalf)
                .count() > 0;
    }

    private Match findKoMatch(Long tid, int round, int slot, Integer half) {
        LambdaQueryWrapper<Match> q = Wrappers.lambdaQuery();
        q.eq(Match::getTournamentId, tid);
        q.eq(Match::getRound, round);
        q.eq(Match::getKnockoutBracketSlot, slot);
        q.in(Match::getPhaseCode, "MAIN", "FINAL");
        if (half == null) {
            q.isNull(Match::getKnockoutHalf);
        } else {
            q.eq(Match::getKnockoutHalf, half);
        }
        q.last("LIMIT 1");
        return matchService.getOne(q);
    }

    private Match findKoMatchOtherHalf(Long tid, int round, int half) {
        int other = half == 0 ? 1 : 0;
        return matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, round)
                .eq(Match::getKnockoutHalf, other)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .last("LIMIT 1")
                .one();
    }

    private void insertKoMatch(Long tid, int roundField, int slot, Integer half,
                               Long p1, Long p2, String category, Long f1, Long f2, int sets, String phase,
                               Long createdByUserId, String createSource) {
        LocalDateTime now = LocalDateTime.now();
        Match m = new Match();
        m.setTournamentId(tid);
        m.setCategory(category);
        m.setPhaseCode(phase);
        m.setGroupId(null);
        m.setRound(roundField);
        m.setPlayer1Id(p1);
        m.setPlayer2Id(p2);
        m.setHomeUserId(p1);
        m.setAwayUserId(p2);
        m.setWinnerId(null);
        m.setStatus((byte) 0);
        m.setResultLocked(false);
        m.setKnockoutBracketSlot(slot);
        m.setKnockoutHalf(half);
        m.setFeederMatch1Id(f1);
        m.setFeederMatch2Id(f2);
        m.setCreatedByUserId(createdByUserId);
        m.setCreateSource(createSource);
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        matchService.save(m);
        for (int setNo = 1; setNo <= sets; setNo++) {
            SetScore ss = new SetScore();
            ss.setMatchId(m.getId());
            ss.setSetNumber(setNo);
            ss.setPlayer1Score(0);
            ss.setPlayer2Score(0);
            ss.setCreatedAt(now);
            setScoreService.save(ss);
        }
    }

    private String labelKo(int fromR, int toR, int slot, Integer half) {
        String h = half == null ? "" : (half == 0 ? "上半区 " : "下半区 ");
        return h + "淘汰赛(" + fromR + "→" + toR + ") 场" + (slot + 1);
    }

    private List<Long> loadOverallRankedUserIds(Long tournamentId, TournamentCompetitionConfig cfg) {
        List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Long>> memberIdsByGroup = new LinkedHashMap<>();
        for (TournamentGroup g : groups) {
            List<Long> uids = groupMemberMapper.selectList(Wrappers.<TournamentGroupMember>lambdaQuery()
                            .eq(TournamentGroupMember::getTournamentId, tournamentId)
                            .eq(TournamentGroupMember::getGroupId, g.getId())
                            .orderByAsc(TournamentGroupMember::getSeedNo)
                            .orderByAsc(TournamentGroupMember::getId))
                    .stream().map(TournamentGroupMember::getUserId).filter(Objects::nonNull).toList();
            memberIdsByGroup.put(g.getId(), uids);
        }
        Map<Long, String> uname = userService.list().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        List<Match> matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .list();
        List<Long> mids = matches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        Map<Long, List<SetScore>> scoreByMatch = mids.isEmpty() ? Map.of() : setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, mids)
                .list()
                .stream()
                .collect(Collectors.groupingBy(SetScore::getMatchId));
        Map<Long, List<Map<String, Object>>> byG = groupRankingCalculator.buildGroupRankingsByMemberIds(
                groups, memberIdsByGroup, uname, matches, scoreByMatch, cfg);
        List<Map<String, Object>> overall = groupRankingCalculator.buildOverallRanking(byG);
        List<Long> out = new ArrayList<>();
        for (Map<String, Object> row : overall) {
            Long uid = (Long) row.get("userId");
            if (uid != null) {
                out.add(uid);
            }
        }
        return out;
    }

    private List<PlannedKo> planFirstRound(TournamentCompetitionConfig cfg, List<Long> rankedPlayers,
                                            Long tournamentId, int mode) {
        int n = rankedPlayers.size();
        int startField = cfg.getKnockoutStartRound();
        List<PlannedKo> list = new ArrayList<>();
        // 模式 0：只取小组赛总排名列表 rankedPlayers，与小组个数、奇偶无关
        if (mode == 0) {
            List<int[]> pairs = KnockoutPairingUtil.classicOverallRankPairs(n);
            int idx = 1;
            for (int[] pr : pairs) {
                Long a = rankedPlayers.get(pr[0] - 1);
                Long b = rankedPlayers.get(pr[1] - 1);
                list.add(new PlannedKo(a, b, koRoundLabel(startField) + " 第" + idx + "场", null));
                idx++;
            }
            return list;
        }
        List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Map<String, Object>>> gr = groupRankRows(tournamentId, cfg, groups);
        if (mode == 1) {
            if (groups.size() < 2 || groups.size() % 2 != 0) {
                throw new IllegalStateException("上下半区组间交叉须至少 2 组且为偶数个小组（两半区各含若干组）");
            }
            int halfCount = groups.size() / 2;
            int gsz = cfg.getGroupSize() != null ? cfg.getGroupSize() : (n / groups.size());
            List<int[]> hx = KnockoutPairingUtil.halfCrossPairsBetweenTwoGroups(gsz);
            int matchNo = 1;
            for (int h = 0; h < 2; h++) {
                int lo = h == 0 ? 0 : halfCount;
                TournamentGroup ga = groups.get(lo);
                TournamentGroup gb = groups.get(lo + 1);
                for (int[] pr : hx) {
                    Long a = userAtGroupRank(gr, ga.getId(), pr[0]);
                    Long b = userAtGroupRank(gr, gb.getId(), pr[1]);
                    list.add(new PlannedKo(a, b, koRoundLabel(startField) + " 第" + matchNo + "场", h));
                    matchNo++;
                }
            }
            return list;
        }
        if (mode == 2) {
            int gCount = groups.size();
            if (gCount < 2 || gCount % 2 != 0) {
                throw new IllegalStateException("世界杯式交错对阵须至少 2 个小组且为偶数个小组");
            }
            int pairCount = gCount / 2;
            int q = cfg.getGroupSize() != null ? cfg.getGroupSize() : (n / gCount);
            if (cfg.getGroupSize() == null && n % gCount != 0) {
                throw new IllegalStateException("未配置每组人数时，世界杯式首轮人数须为小组数的整数倍");
            }
            if (n != q * gCount) {
                throw new IllegalStateException("世界杯式首轮人数须等于 小组数×每组晋级人数（当前 " + n + "，期望 " + (q * gCount) + "）");
            }
            int matchNo = 1;
            for (int half = 0; half < 2; half++) {
                for (int t = 0; t < q; t++) {
                    int[] pr = KnockoutPairingUtil.worldCupCrossRankPair(t, q);
                    if (half == 1) {
                        pr = new int[]{pr[1], pr[0]};
                    }
                    for (int p = 0; p < pairCount; p++) {
                        TournamentGroup g0 = groups.get(2 * p);
                        TournamentGroup g1 = groups.get(2 * p + 1);
                        list.add(new PlannedKo(
                                userAtGroupRank(gr, g0.getId(), pr[0]),
                                userAtGroupRank(gr, g1.getId(), pr[1]),
                                koRoundLabel(startField) + " 第" + matchNo + "场", half));
                        matchNo++;
                    }
                }
            }
            return list;
        }
        throw new IllegalArgumentException("未知淘汰赛模式: " + mode);
    }

    private Map<Long, List<Map<String, Object>>> groupRankRows(Long tournamentId, TournamentCompetitionConfig cfg,
                                                               List<TournamentGroup> groups) {
        Map<Long, List<Long>> memberIdsByGroup = new LinkedHashMap<>();
        for (TournamentGroup g : groups) {
            List<Long> uids = groupMemberMapper.selectList(Wrappers.<TournamentGroupMember>lambdaQuery()
                            .eq(TournamentGroupMember::getTournamentId, tournamentId)
                            .eq(TournamentGroupMember::getGroupId, g.getId()))
                    .stream().map(TournamentGroupMember::getUserId).filter(Objects::nonNull).toList();
            memberIdsByGroup.put(g.getId(), uids);
        }
        Map<Long, String> uname = userService.list().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        List<Match> matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .list();
        List<Long> mids = matches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        Map<Long, List<SetScore>> scoreByMatch = mids.isEmpty() ? Map.of() : setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, mids)
                .list()
                .stream()
                .collect(Collectors.groupingBy(SetScore::getMatchId));
        return groupRankingCalculator.buildGroupRankingsByMemberIds(groups, memberIdsByGroup, uname, matches, scoreByMatch, cfg);
    }

    private static Long userAtGroupRank(Map<Long, List<Map<String, Object>>> gr, long groupId, int groupRank1Based) {
        List<Map<String, Object>> rows = gr.get(groupId);
        if (rows == null || groupRank1Based < 1 || groupRank1Based > rows.size()) {
            return null;
        }
        return (Long) rows.get(groupRank1Based - 1).get("userId");
    }

    private static String koRoundLabel(int v) {
        if (v == 16) {
            return "1/16决赛";
        }
        if (v == 8) {
            return "1/8决赛";
        }
        if (v == 4) {
            return "1/4决赛";
        }
        if (v == 2) {
            return "半决赛";
        }
        return "淘汰赛";
    }

    private boolean canManage(User u, Long tournamentId) {
        if (u == null) {
            return false;
        }
        if (u.getRole() != null && u.getRole() <= 1) {
            return true;
        }
        Tournament t = tournamentService.getById(tournamentId);
        return t != null && Objects.equals(t.getHostUserId(), u.getId());
    }

    private static final class PlannedKo {
        final Long p1;
        final Long p2;
        final String category;
        final Integer half;

        PlannedKo(Long p1, Long p2, String category, Integer half) {
            this.p1 = p1;
            this.p2 = p2;
            this.category = category;
            this.half = half;
        }
    }

    public static final class ManualPairInput {
        public final Long player1Id;
        public final Long player2Id;

        public ManualPairInput(Long player1Id, Long player2Id) {
            this.player1Id = player1Id;
            this.player2Id = player2Id;
        }
    }

    public static final class ManualPairDraft {
        public final int slot;
        public final Long defaultPlayer1Id;
        public final Long defaultPlayer2Id;
        public final String label;

        public ManualPairDraft(int slot, Long defaultPlayer1Id, Long defaultPlayer2Id, String label) {
            this.slot = slot;
            this.defaultPlayer1Id = defaultPlayer1Id;
            this.defaultPlayer2Id = defaultPlayer2Id;
            this.label = label;
        }
    }
}
