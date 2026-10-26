package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.*;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.mapper.TournamentDisqualificationMapper;
import com.example.mapper.TournamentGroupMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.service.GroupRankingCalculator;
import com.example.service.IMatchService;
import com.example.service.ISetScoreService;
import com.example.service.RankingPriorityService;
import com.example.service.TournamentService;
import com.example.service.UserService;
import com.example.service.knockout.KnockoutPairingUtil;
import com.example.util.MatchPhaseClassifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnockoutBracketService {
    public static final String SOURCE_AUTO_FROM_GROUP = "AUTO_FROM_GROUP";
    public static final String SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER = "AUTO_FROM_GROUP_KO_QUALIFIER";
    public static final String SOURCE_MANUAL_KO_EDITOR = "MANUAL_KO_EDITOR";
    public static final String SOURCE_AUTO_BRACKET_ADVANCE = "AUTO_BRACKET_ADVANCE";
    private static final Pattern KO_QUALIFIER_SLOT_PATTERN = Pattern.compile("关联第(\\d+)场");

    @Autowired private TournamentGroupMapper groupMapper;
    @Autowired private TournamentGroupMemberMapper groupMemberMapper;
    @Autowired private IMatchService matchService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private TournamentCompetitionConfigMapper configMapper;
    @Autowired private GroupRankingCalculator groupRankingCalculator;
    @Autowired private RankingPriorityService rankingPriorityService;
    @Autowired private UserService userService;
    @Autowired private TournamentService tournamentService;
    @Autowired private TournamentDisqualificationMapper tournamentDisqualificationMapper;

    public static int playersInFirstKnockoutRound(Integer knockoutStartRound) {
        if (knockoutStartRound == null) {
            return 0;
        }
        // 语义：knockoutStartRound=16/8/4/2 分别表示 1/16、1/8、1/4、半决赛
        // 对应首轮参赛人数应为 32/16/8/4
        if (Objects.equals(knockoutStartRound, 2)) {
            return 4;
        }
        return knockoutStartRound * 2;
    }

    public static int nextKnockoutRoundField(int current) {
        if (current <= 1) {
            return 1;
        }
        // 语义：16 -> 8 -> 4 -> 2 -> 1
        // 仅当当前轮为半决赛（2）时，下一轮才是金/铜牌赛（1）
        if (current == 2) {
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
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        List<Match> ko = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .list();
        Set<Long> qualifierIdsToDelete = new LinkedHashSet<>();
        for (Match q : findMountedKoQualifierMatches(tournamentId, cfg)) {
            if (q.getId() != null) {
                qualifierIdsToDelete.add(q.getId());
            }
        }
        // 首轮 MAIN 通过 feeder 挂上的资格赛：即使 createSource/文案不规范也一并清掉
        for (Match m : ko) {
            collectQualifierFeeder(m.getFeederMatch1Id(), qualifierIdsToDelete);
            collectQualifierFeeder(m.getFeederMatch2Id(), qualifierIdsToDelete);
        }
        for (Match m : ko) {
            setScoreService.lambdaUpdate().eq(SetScore::getMatchId, m.getId()).remove();
            matchService.removeById(m.getId());
        }
        for (Long qid : qualifierIdsToDelete) {
            setScoreService.lambdaUpdate().eq(SetScore::getMatchId, qid).remove();
            matchService.removeById(qid);
        }
    }

    private void collectQualifierFeeder(Long feederMatchId, Set<Long> out) {
        if (feederMatchId == null) {
            return;
        }
        Match feeder = matchService.getById(feederMatchId);
        if (feeder != null && "QUALIFIER".equalsIgnoreCase(feeder.getPhaseCode())) {
            out.add(feederMatchId);
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
        if (!isGroupStageFullyLocked(tournamentId)) {
            throw new IllegalStateException("小组赛须全部验收后才可生成淘汰赛");
        }
        int bracketPlayers = playersInFirstKnockoutRound(cfg.getKnockoutStartRound());
        List<Long> ranked = loadOverallRankedUserIds(tournamentId, cfg);
        if (ranked.size() < bracketPlayers) {
            throw new IllegalStateException("人数不足首轮规模（需 " + bracketPlayers + " 人，当前 " + ranked.size() + "）");
        }
        int mode = cfg.getKnockoutBracketMode() == null ? 0 : cfg.getKnockoutBracketMode();
        List<Long> take = firstRoundBracketPlayerIds(tournamentId, cfg, bracketPlayers, mode, ranked);
        List<PlannedKo> planned = planFirstRound(cfg, take, tournamentId, mode);
        if (planned.isEmpty()) {
            throw new IllegalStateException("未能生成首轮对阵");
        }
        deleteAllKnockoutMatches(operator, tournamentId);
        // 重新生成首轮时，仅清空“首轮淘汰赛附加资格赛”（KO 挂载资格赛），避免误删“正赛资格赛”。
        deleteAutoGeneratedKoQualifierMatches(tournamentId);
        int koSets = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : 8;
        int startRoundField = cfg.getKnockoutStartRound();
        if (cfg.getQualifierRound() != null && Objects.equals(cfg.getQualifierRound(), cfg.getKnockoutStartRound())) {
            return generateFirstRoundWithMountedQualifiers(operator, tournamentId, cfg, ranked, planned, koSets, startRoundField, bracketPlayers);
        }
        for (int i = 0; i < planned.size(); i++) {
            PlannedKo p = planned.get(i);
            insertKoMatch(tournamentId, startRoundField, i, p.half, p.p1, p.p2, p.category, null, null, koSets, "MAIN",
                    operator == null ? null : operator.getId(), SOURCE_AUTO_FROM_GROUP);
        }
        return planned.size();
    }

    public List<Long> loadOverallRankedUserIdsForKnockout(Long tournamentId) {
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        if (cfg == null) {
            return List.of();
        }
        return loadOverallRankedUserIds(tournamentId, cfg);
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
        int mode = cfg.getKnockoutBracketMode() == null ? 0 : cfg.getKnockoutBracketMode();
        if (isFirstRoundMountedQualifier(cfg)) {
            List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery()
                    .eq(TournamentGroup::getTournamentId, tournamentId)
                    .orderByAsc(TournamentGroup::getGroupOrder));
            MountedQualifierRule rule = resolveMountedQualifierRule(cfg.getKnockoutStartRound(), groups.size(), mode);
            int requiredRanked = bracketPlayers + rule.qualifyCount;
            if (ranked.size() < requiredRanked) {
                throw new IllegalStateException("人数不足挂载资格赛（需至少 " + requiredRanked + " 人，当前 " + ranked.size() + "）");
            }
        }
        List<Long> base = firstRoundBracketPlayerIds(tournamentId, cfg, bracketPlayers, mode, ranked);
        return sortPlayerIdsByOverallRank(augmentFirstRoundManualEligiblePool(tournamentId, cfg, base), ranked);
    }

    private List<Long> sortPlayerIdsByOverallRank(Collection<Long> ids, List<Long> overallRanked) {
        Map<Long, Integer> overallOrder = new HashMap<>();
        for (int i = 0; i < overallRanked.size(); i++) {
            overallOrder.putIfAbsent(overallRanked.get(i), i);
        }
        return ids.stream()
                .sorted(Comparator.comparingInt(uid -> overallOrder.getOrDefault(uid, Integer.MAX_VALUE)))
                .toList();
    }

    /**
     * 在种子池基础上纳入：已落位的首轮 MAIN 选手、挂载资格赛参赛者与胜者（含从附加赛晋级者）。
     */
    private LinkedHashSet<Long> augmentFirstRoundManualEligiblePool(Long tournamentId,
                                                                    TournamentCompetitionConfig cfg,
                                                                    Collection<Long> basePool) {
        LinkedHashSet<Long> pool = new LinkedHashSet<>(basePool);
        if (cfg == null || cfg.getKnockoutStartRound() == null) {
            return pool;
        }
        int startRound = cfg.getKnockoutStartRound();
        List<Match> mains = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, startRound)
                .list();
        for (Match m : mains) {
            addResolvedFirstRoundSidePlayer(pool, m, true);
            addResolvedFirstRoundSidePlayer(pool, m, false);
        }
        if (isFirstRoundMountedQualifier(cfg)) {
            for (Match q : findMountedKoQualifierMatches(tournamentId, cfg)) {
                if (q.getPlayer1Id() != null) {
                    pool.add(q.getPlayer1Id());
                }
                if (q.getPlayer2Id() != null) {
                    pool.add(q.getPlayer2Id());
                }
                if (q.getWinnerId() != null) {
                    pool.add(q.getWinnerId());
                }
            }
        }
        return pool;
    }

    /** MAIN 已登记选手；若该侧仍为空但已挂资格赛 feeder，则纳入资格赛胜者（已晋级 1/8 等）。 */
    private void addResolvedFirstRoundSidePlayer(Set<Long> pool, Match main, boolean side1) {
        Long id = resolveFirstRoundSidePlayerId(main, side1);
        if (id != null) {
            pool.add(id);
        }
    }

    private Long resolveFirstRoundSidePlayerId(Match main, boolean side1) {
        if (main == null) {
            return null;
        }
        Long id = side1 ? main.getPlayer1Id() : main.getPlayer2Id();
        if (id != null) {
            return id;
        }
        Long feederId = side1 ? main.getFeederMatch1Id() : main.getFeederMatch2Id();
        if (feederId == null) {
            return null;
        }
        Match feeder = matchService.getById(feederId);
        if (feeder == null) {
            return null;
        }
        if (feeder.getWinnerId() != null) {
            return feeder.getWinnerId();
        }
        return null;
    }

    private Map<Integer, Match> loadFirstRoundMainMatchesBySlot(Long tournamentId, int startRoundField) {
        List<Match> existingFirstRound = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, startRoundField)
                .list();
        return existingFirstRound.stream()
                .filter(m -> m.getKnockoutBracketSlot() != null)
                .collect(Collectors.toMap(Match::getKnockoutBracketSlot, m -> m, (a, b) -> a));
    }

    /**
     * 首轮淘汰赛落位选手：模式0=总排名前 N；模式1/2=各组前 q 名（q=N/组数），与 planFirstRound 一致。
     */
    private List<Long> firstRoundBracketPlayerIds(Long tournamentId,
                                                  TournamentCompetitionConfig cfg,
                                                  int bracketPlayers,
                                                  int mode,
                                                  List<Long> overallRanked) {
        if (mode == 0) {
            return new ArrayList<>(overallRanked.subList(0, bracketPlayers));
        }
        List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder));
        if (groups.isEmpty()) {
            throw new IllegalStateException("未找到小组，无法确定首轮晋级名单");
        }
        if (bracketPlayers % groups.size() != 0) {
            throw new IllegalStateException("首轮人数须为小组数的整数倍（当前 " + bracketPlayers + " 人，" + groups.size() + " 组）");
        }
        int ranksPerGroup = bracketPlayers / groups.size();
        Map<Long, List<Map<String, Object>>> gr = groupRankRows(tournamentId, cfg, groups);
        LinkedHashSet<Long> pool = new LinkedHashSet<>();
        for (TournamentGroup g : groups) {
            for (int r = 1; r <= ranksPerGroup; r++) {
                Long uid = userAtGroupRank(gr, g.getId(), r);
                if (uid == null) {
                    throw new IllegalStateException("小组名次不完整，无法确定首轮晋级名单（组序 " + g.getGroupOrder() + " 缺第 " + r + " 名）");
                }
                pool.add(uid);
            }
        }
        if (pool.size() < bracketPlayers) {
            throw new IllegalStateException("首轮晋级名单不足 " + bracketPlayers + " 人");
        }
        return sortPlayerIdsByOverallRank(pool, overallRanked);
    }

    /** 首轮淘汰赛是否挂载附加资格赛（与自动生成首轮条件一致）。 */
    public boolean isFirstRoundMountedQualifier(TournamentCompetitionConfig cfg) {
        return cfg != null
                && cfg.getKnockoutStartRound() != null
                && cfg.getQualifierRound() != null
                && Objects.equals(cfg.getQualifierRound(), cfg.getKnockoutStartRound());
    }

    public ManualFirstRoundPageData buildManualFirstRoundPageData(User operator, Long tournamentId) {
        List<ManualPairDraft> main = buildManualFirstRoundDraftInternal(operator, tournamentId);
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        List<ManualPairDraft> qualifiers = isFirstRoundMountedQualifier(cfg)
                ? buildManualMountedQualifierDraft(operator, tournamentId, cfg, main)
                : List.of();
        return new ManualFirstRoundPageData(main, qualifiers);
    }

    public List<ManualPairDraft> buildManualFirstRoundDraft(User operator, Long tournamentId) {
        return buildManualFirstRoundDraftInternal(operator, tournamentId);
    }

    private List<ManualPairDraft> buildManualMainRoundDraft(TournamentCompetitionConfig cfg,
                                                            Long tournamentId,
                                                            int expectedMatches,
                                                            List<PlannedKo> planned) {
        int startRound = cfg.getKnockoutStartRound();
        Map<Integer, Match> existingBySlot = loadFirstRoundMainMatchesBySlot(tournamentId, startRound);
        Set<Long> scoredIds = loadMatchIdsLockedForManualEdit(existingBySlot.values());
        List<ManualPairDraft> out = new ArrayList<>();
        for (int idx = 0; idx < expectedMatches; idx++) {
            PlannedKo p = planned.get(idx);
            Match existing = existingBySlot.get(idx);
            Long p1 = existing != null ? resolveFirstRoundSidePlayerId(existing, true) : null;
            Long p2 = existing != null ? resolveFirstRoundSidePlayerId(existing, false) : null;
            if (p1 == null) {
                p1 = p.p1;
            }
            if (p2 == null) {
                p2 = p.p2;
            }
            boolean locked = isMatchLockedForManualEdit(existing, scoredIds);
            String label = p.category == null || p.category.isBlank() ? ("第" + (idx + 1) + "场") : p.category;
            out.add(new ManualPairDraft(idx, p1, p2, label, locked, existing == null ? null : existing.getId()));
        }
        return out;
    }

    private List<ManualPairDraft> buildManualMountedQualifierDraft(User operator,
                                                                   Long tournamentId,
                                                                   TournamentCompetitionConfig cfg,
                                                                   List<ManualPairDraft> mainDraft) {
        int startRound = cfg.getKnockoutStartRound();
        List<Match> existing = listMountedKoQualifiersSorted(tournamentId, cfg);
        Set<Long> scoredIds = loadMatchIdsLockedForManualEdit(existing);
        if (!existing.isEmpty()) {
            List<ManualPairDraft> out = new ArrayList<>();
            for (int i = 0; i < existing.size(); i++) {
                Match q = existing.get(i);
                boolean locked = isMatchLockedForManualEdit(q, scoredIds);
                int koSlot = mountedQualifierLinkedKoSlot(q);
                String label = q.getCategory() != null && !q.getCategory().isBlank()
                        ? q.getCategory()
                        : ("挂载资格赛 第" + (i + 1) + "场");
                out.add(new ManualPairDraft(i, q.getPlayer1Id(), q.getPlayer2Id(), label, locked, q.getId(), koSlot));
            }
            return out;
        }
        List<ManualPairInput> mainInputs = mainDraft.stream()
                .map(d -> new ManualPairInput(d.defaultPlayer1Id, d.defaultPlayer2Id))
                .toList();
        List<PlannedQualifierPair> planned = planMountedQualifierPairs(operator, tournamentId, cfg, mainInputs);
        List<ManualPairDraft> out = new ArrayList<>();
        for (int i = 0; i < planned.size(); i++) {
            PlannedQualifierPair pq = planned.get(i);
            out.add(new ManualPairDraft(i, pq.player1Id, pq.player2Id, pq.label, false, null, pq.mainKoSlot));
        }
        return out;
    }

    /**
     * 手动排签锁定判定用：生成淘汰赛时会预插全 0 局分占位，不能仅凭存在 set_score 行即视为已开打。
     * 与赛事详情 buildMatchCardForDetail 的 completedNonZero 一致，并包含验收/胜者。
     */
    private Set<Long> loadMatchIdsLockedForManualEdit(Collection<Match> matches) {
        if (matches == null || matches.isEmpty()) {
            return Set.of();
        }
        List<Long> matchIds = matches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        if (matchIds.isEmpty()) {
            return Set.of();
        }
        Map<Long, List<SetScore>> scoresByMatch = setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, matchIds)
                .list()
                .stream()
                .collect(Collectors.groupingBy(SetScore::getMatchId));
        Set<Long> locked = new HashSet<>();
        for (Match m : matches) {
            if (m == null || m.getId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(m.getResultLocked()) || m.getWinnerId() != null) {
                locked.add(m.getId());
                continue;
            }
            if (hasRecordedScoreBeyondPlaceholder(scoresByMatch.getOrDefault(m.getId(), List.of()))) {
                locked.add(m.getId());
            }
        }
        return locked;
    }

    private static boolean hasRecordedScoreBeyondPlaceholder(List<SetScore> setScores) {
        if (setScores == null || setScores.isEmpty()) {
            return false;
        }
        int p1 = setScores.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
        int p2 = setScores.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
        if (p1 != 0 || p2 != 0) {
            return true;
        }
        for (SetScore s : setScores) {
            if (Boolean.TRUE.equals(s.getPlayer1IsX()) || Boolean.TRUE.equals(s.getPlayer2IsX())) {
                return true;
            }
            if (s.getIsBlankEnd() != null && s.getIsBlankEnd() != 0) {
                return true;
            }
            if (s.getHammerPlayerId() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean isMatchLockedForManualEdit(Match match, Set<Long> lockedMatchIds) {
        if (match == null || match.getId() == null) {
            return false;
        }
        return lockedMatchIds.contains(match.getId());
    }

    private void assertManualPairUnchangedIfLocked(Match existing,
                                                   ManualPairInput submitted,
                                                   Set<Long> scoredIds,
                                                   String label) {
        if (!isMatchLockedForManualEdit(existing, scoredIds)) {
            return;
        }
        Long e1 = resolveFirstRoundSidePlayerId(existing, true);
        Long e2 = resolveFirstRoundSidePlayerId(existing, false);
        if (!Objects.equals(e1, submitted.player1Id) || !Objects.equals(e2, submitted.player2Id)) {
            throw new IllegalStateException(label + " 已有比分或已验收，不可修改选手");
        }
    }

    private List<Match> listMountedKoQualifiersSorted(Long tournamentId, TournamentCompetitionConfig cfg) {
        List<Match> qs = findMountedKoQualifierMatches(tournamentId, cfg);
        if (qs.isEmpty()) {
            return List.of();
        }
        List<Match> sorted = new ArrayList<>(qs);
        sorted.sort(Comparator
                .comparingInt(this::mountedQualifierLinkedKoSlot)
                .thenComparing(m -> m.getId() == null ? 0L : m.getId()));
        return sorted;
    }

    private int mountedQualifierLinkedKoSlot(Match qualifierMatch) {
        if (qualifierMatch == null || qualifierMatch.getCategory() == null) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = KO_QUALIFIER_SLOT_PATTERN.matcher(qualifierMatch.getCategory());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1)) - 1;
            } catch (NumberFormatException ignored) {
                return Integer.MAX_VALUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    private List<ManualPairDraft> buildManualFirstRoundDraftInternal(User operator, Long tournamentId) {
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        if (cfg == null || cfg.getKnockoutStartRound() == null) {
            throw new IllegalStateException("请先配置淘汰赛首轮");
        }
        int bracketPlayers = playersInFirstKnockoutRound(cfg.getKnockoutStartRound());
        int expectedMatches = bracketPlayers / 2;
        List<Long> ranked = loadOverallRankedUserIds(tournamentId, cfg);
        int mode = cfg.getKnockoutBracketMode() == null ? 0 : cfg.getKnockoutBracketMode();
        List<Long> seedPool = firstRoundBracketPlayerIds(tournamentId, cfg, bracketPlayers, mode, ranked);
        List<PlannedKo> planned = planFirstRound(cfg, seedPool, tournamentId, mode);
        if (planned.size() != expectedMatches) {
            throw new IllegalStateException("首轮对阵计划场次数异常（期望 " + expectedMatches + " 场，实际 " + planned.size() + " 场）");
        }
        return buildManualMainRoundDraft(cfg, tournamentId, expectedMatches, planned);
    }

    @Transactional(rollbackFor = Exception.class)
    public int generateFirstKnockoutRoundManual(User operator, Long tournamentId, List<ManualPairInput> manualPairs) {
        return generateFirstKnockoutRoundManual(operator, tournamentId, manualPairs, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public int generateFirstKnockoutRoundManual(User operator,
                                                Long tournamentId,
                                                List<ManualPairInput> manualPairs,
                                                List<ManualPairInput> qualifierPairs) {
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
        int startRoundField = cfg.getKnockoutStartRound();
        Map<Integer, Match> existingBySlot = loadFirstRoundMainMatchesBySlot(tournamentId, startRoundField);
        Set<Long> mainScoredIds = loadMatchIdsLockedForManualEdit(existingBySlot.values());

        Set<Long> eligible = new LinkedHashSet<>(loadEligibleFirstRoundPlayers(operator, tournamentId));
        Set<Long> used = new LinkedHashSet<>();
        for (int i = 0; i < manualPairs.size(); i++) {
            ManualPairInput p = manualPairs.get(i);
            Match existing = existingBySlot.get(i);
            assertManualPairUnchangedIfLocked(existing, p, mainScoredIds, "1/8 等首轮第 " + (i + 1) + " 场");
            if (p == null || p.player1Id == null || p.player2Id == null) {
                throw new IllegalStateException("第 " + (i + 1) + " 场仍有未选择选手");
            }
            if (Objects.equals(p.player1Id, p.player2Id)) {
                throw new IllegalStateException("第 " + (i + 1) + " 场选手重复");
            }
            if (!eligible.contains(p.player1Id) || !eligible.contains(p.player2Id)) {
                throw new IllegalStateException("第 " + (i + 1) + " 场选手不在当前淘汰赛首轮候选名单内（含各组种子席与已从挂载资格赛晋级落位的选手）");
            }
            if (isMatchLockedForManualEdit(existing, mainScoredIds)) {
                Long e1 = resolveFirstRoundSidePlayerId(existing, true);
                Long e2 = resolveFirstRoundSidePlayerId(existing, false);
                if (e1 != null) {
                    used.add(e1);
                }
                if (e2 != null) {
                    used.add(e2);
                }
                continue;
            }
            used.add(p.player1Id);
            used.add(p.player2Id);
        }
        if (used.size() != bracketPlayers) {
            throw new IllegalStateException("手动排签存在重复选人或遗漏选人");
        }
        if (isFirstRoundMountedQualifier(cfg)) {
            validateManualQualifierPairs(operator, tournamentId, cfg, manualPairs, qualifierPairs);
            return generateFirstKnockoutRoundManualWithMountedQualifiers(operator, tournamentId, cfg, manualPairs,
                    qualifierPairs);
        }

        int koSets = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : 8;
        
        int updatedCount = 0;
        for (int i = 0; i < manualPairs.size(); i++) {
            ManualPairInput p = manualPairs.get(i);
            Match existing = existingBySlot.get(i);
            if (isMatchLockedForManualEdit(existing, mainScoredIds)) {
                continue;
            }
            
            String category = "[手动排签] " + koRoundLabel(startRoundField) + " 第" + (i + 1) + "场";
            
            if (existing != null) {
                // 更新现有比赛（删除比分后更新选手）
                setScoreService.lambdaUpdate().eq(SetScore::getMatchId, existing.getId()).remove();
                existing.setPlayer1Id(p.player1Id);
                existing.setPlayer2Id(p.player2Id);
                existing.setCategory(category);
                existing.setWinnerId(null);
                existing.setResultLocked(false);
                existing.setUpdatedAt(LocalDateTime.now());
                matchService.updateById(existing);
            } else {
                // 插入新比赛
                insertKoMatch(tournamentId, startRoundField, i, null, p.player1Id, p.player2Id, category, null, null, koSets, "MAIN",
                        operator == null ? null : operator.getId(), SOURCE_MANUAL_KO_EDITOR);
            }
            updatedCount++;
        }
        
        return updatedCount;
    }

    /**
     * 手动排签首轮且挂载附加资格赛：按手动对阵落位 MAIN，并同步生成/重建挂载资格赛场次（对手按规则自动配对）。
     */
    private void validateManualQualifierPairs(User operator,
                                              Long tournamentId,
                                              TournamentCompetitionConfig cfg,
                                              List<ManualPairInput> mainPairs,
                                              List<ManualPairInput> qualifierPairs) {
        List<Match> existing = listMountedKoQualifiersSorted(tournamentId, cfg);
        Set<Long> scoredIds = loadMatchIdsLockedForManualEdit(existing);
        int expected = planMountedQualifierPairs(operator, tournamentId, cfg, mainPairs).size();
        if (expected == 0) {
            return;
        }
        if (qualifierPairs == null || qualifierPairs.size() != expected) {
            throw new IllegalStateException("挂载资格赛场次数量不正确（应为 " + expected + " 场）");
        }
        Set<Long> eligible = new LinkedHashSet<>(loadEligibleFirstRoundPlayers(operator, tournamentId));
        for (int i = 0; i < qualifierPairs.size(); i++) {
            ManualPairInput p = qualifierPairs.get(i);
            Match ex = i < existing.size() ? existing.get(i) : null;
            String label = "挂载资格赛第 " + (i + 1) + " 场";
            assertManualPairUnchangedIfLocked(ex, p, scoredIds, label);
            if (isMatchLockedForManualEdit(ex, scoredIds)) {
                continue;
            }
            if (p == null || p.player1Id == null || p.player2Id == null) {
                throw new IllegalStateException(label + " 仍有未选择选手");
            }
            if (Objects.equals(p.player1Id, p.player2Id)) {
                throw new IllegalStateException(label + " 选手重复");
            }
            if (!eligible.contains(p.player1Id) || !eligible.contains(p.player2Id)) {
                throw new IllegalStateException(label + " 选手不在候选名单内");
            }
        }
    }

    private List<ManualPairInput> manualPairsFromDraft(List<ManualPairDraft> draft) {
        return draft.stream().map(d -> new ManualPairInput(d.defaultPlayer1Id, d.defaultPlayer2Id)).toList();
    }

    private int generateFirstKnockoutRoundManualWithMountedQualifiers(User operator,
                                                                      Long tournamentId,
                                                                      TournamentCompetitionConfig cfg,
                                                                      List<ManualPairInput> manualPairs,
                                                                      List<ManualPairInput> qualifierPairs) {
        int koSets = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : 8;
        int startRoundField = cfg.getKnockoutStartRound();
        int bracketPlayers = playersInFirstKnockoutRound(startRoundField);
        int mode = cfg.getKnockoutBracketMode() == null ? 0 : cfg.getKnockoutBracketMode();
        List<Long> ranked = loadOverallRankedUserIds(tournamentId, cfg);
        List<Long> seedPool = firstRoundBracketPlayerIds(tournamentId, cfg, bracketPlayers, mode, ranked);
        List<PlannedKo> template = planFirstRound(cfg, seedPool, tournamentId, mode);
        List<PlannedKo> planned = new ArrayList<>();
        for (int i = 0; i < manualPairs.size(); i++) {
            ManualPairInput p = manualPairs.get(i);
            Integer half = i < template.size() ? template.get(i).half : null;
            String category = "[手动排签] " + koRoundLabel(startRoundField) + " 第" + (i + 1) + "场";
            planned.add(new PlannedKo(p.player1Id, p.player2Id, category, half));
        }

        Map<Integer, Match> existingBySlot = loadFirstRoundMainMatchesBySlot(tournamentId, startRoundField);
        Set<Long> mainScoredIds = loadMatchIdsLockedForManualEdit(existingBySlot.values());

        deleteUnlockedMountedKoQualifierMatches(tournamentId, cfg);
        int touched = applyMountedQualifiersToPlannedFirstRound(
                operator, tournamentId, cfg, ranked, planned, koSets, startRoundField, bracketPlayers,
                existingBySlot, mainScoredIds, SOURCE_MANUAL_KO_EDITOR, qualifierPairs);
        return touched;
    }

    private void deleteUnlockedMountedKoQualifierMatches(Long tournamentId, TournamentCompetitionConfig cfg) {
        List<Match> qs = findMountedKoQualifierMatches(tournamentId, cfg);
        Set<Long> scoredIds = loadMatchIdsLockedForManualEdit(qs);
        for (Match q : qs) {
            if (isMatchLockedForManualEdit(q, scoredIds)) {
                continue;
            }
            setScoreService.lambdaUpdate().eq(SetScore::getMatchId, q.getId()).remove();
            matchService.removeById(q.getId());
        }
        int startRound = cfg.getKnockoutStartRound();
        List<Match> mains = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, startRound)
                .list();
        Set<Long> lockedQualIds = qs.stream()
                .filter(q -> isMatchLockedForManualEdit(q, scoredIds))
                .map(Match::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Match main : mains) {
            if (main.getFeederMatch1Id() != null && !lockedQualIds.contains(main.getFeederMatch1Id())) {
                main.setFeederMatch1Id(null);
            }
            if (main.getFeederMatch2Id() != null && !lockedQualIds.contains(main.getFeederMatch2Id())) {
                main.setFeederMatch2Id(null);
            }
            main.setUpdatedAt(LocalDateTime.now());
            matchService.updateById(main);
        }
    }

    private int applyMountedQualifiersToPlannedFirstRound(User operator,
                                                          Long tournamentId,
                                                          TournamentCompetitionConfig cfg,
                                                          List<Long> ranked,
                                                          List<PlannedKo> planned,
                                                          int koSets,
                                                          int startRoundField,
                                                          int bracketPlayers,
                                                          Map<Integer, Match> existingBySlot,
                                                          Set<Long> matchIdsWithScores,
                                                          String mainCreateSource,
                                                          List<ManualPairInput> manualQualifierPairs) {
        int mode = cfg.getKnockoutBracketMode() == null ? 0 : cfg.getKnockoutBracketMode();
        List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Map<String, Object>>> gr = groupRankRows(tournamentId, cfg, groups);
        MountedQualifierRule rule = resolveMountedQualifierRule(startRoundField, groups.size(), mode);
        int qualifyCount = rule.qualifyCount;
        int requiredRanked = bracketPlayers + qualifyCount;
        if (ranked.size() < requiredRanked) {
            throw new IllegalStateException("人数不足挂载资格赛（需至少 " + requiredRanked + " 人，当前 " + ranked.size() + "）");
        }
        Integer configuredQualifyCount = cfg.getKnockoutQualifyCount();
        if (configuredQualifyCount == null || configuredQualifyCount <= 0 || !Objects.equals(configuredQualifyCount, qualifyCount)) {
            cfg.setKnockoutQualifyCount(qualifyCount);
            configMapper.updateById(cfg);
        }
        Map<Long, Long> userGroupId = new HashMap<>();
        Map<Long, Integer> userGroupRank = new HashMap<>();
        for (TournamentGroup g : groups) {
            List<Map<String, Object>> rows = gr.getOrDefault(g.getId(), List.of());
            for (Map<String, Object> row : rows) {
                Long uid = (Long) row.get("userId");
                if (uid != null) {
                    userGroupId.put(uid, g.getId());
                    userGroupRank.put(uid, (Integer) row.get("groupRank"));
                }
            }
        }
        Set<Long> directSet = new LinkedHashSet<>();
        if (rule.directTopRankPerGroup() > 0) {
            for (TournamentGroup g : groups) {
                for (int r = 1; r <= rule.directTopRankPerGroup(); r++) {
                    Long uid = userAtGroupRank(gr, g.getId(), r);
                    if (uid != null) {
                        directSet.add(uid);
                    }
                }
            }
        }
        if (rule.startRoundField == 2 && groups.size() == 1) {
            directSet.clear();
            for (int r = 1; r <= 2; r++) {
                Long uid = userAtGroupRank(gr, groups.get(0).getId(), r);
                if (uid != null) {
                    directSet.add(uid);
                }
            }
        }
        List<Long> qualifierOpponents = ranked.subList(bracketPlayers, bracketPlayers + qualifyCount);
        List<Match> existingQualifiers = listMountedKoQualifiersSorted(tournamentId, cfg);
        Set<Long> qualScoredIds = loadMatchIdsLockedForManualEdit(existingQualifiers);
        int qSets = koSets;
        int qIdx = 0;
        Set<Long> usedQualifierOpponents = new LinkedHashSet<>();
        Long operatorId = operator == null ? null : operator.getId();
        int touched = 0;
        for (int i = 0; i < planned.size(); i++) {
            PlannedKo p = planned.get(i);
            Long mP1 = p.p1;
            Long mP2 = p.p2;
            Long originalP1 = p.p1;
            Long originalP2 = p.p2;
            Long feeder1 = null;
            Long feeder2 = null;
            Match existingMain = existingBySlot.get(i);
            if (mP1 != null && !directSet.contains(mP1)) {
                int mountedRank = userGroupRank.getOrDefault(mP1, rule.mountedRankPerGroup);
                int preferredRank = preferredQualifierOpponentRank(rule, mountedRank);
                Long challenger = pickMountedQualifierOpponent(cfg, qualifierOpponents, qIdx, usedQualifierOpponents,
                        gr, userGroupId.get(originalP2), preferredRank);
                if (challenger == null) {
                    throw new IllegalStateException("资格赛占位数量与名额配置不一致（p1）");
                }
                ManualPairInput manualQ = manualQualifierPairs != null && qIdx < manualQualifierPairs.size()
                        ? manualQualifierPairs.get(qIdx) : null;
                Long qualP1 = manualQ != null ? manualQ.player1Id : mP1;
                Long qualP2 = manualQ != null ? manualQ.player2Id : challenger;
                Match existingQ = existingMain != null && existingMain.getFeederMatch1Id() != null
                        ? matchService.getById(existingMain.getFeederMatch1Id()) : null;
                Long qid = resolveMountedQualifierMatchId(tournamentId, i, startRoundField, qualP1, qualP2, qSets,
                        operatorId, existingQ, qualScoredIds);
                qIdx++;
                feeder1 = qid;
                mP1 = null;
                touched++;
            }
            if (mP2 != null && !directSet.contains(mP2)) {
                int mountedRank = userGroupRank.getOrDefault(mP2, rule.mountedRankPerGroup);
                int preferredRank = preferredQualifierOpponentRank(rule, mountedRank);
                Long challenger = pickMountedQualifierOpponent(cfg, qualifierOpponents, qIdx, usedQualifierOpponents,
                        gr, userGroupId.get(originalP1), preferredRank);
                if (challenger == null) {
                    throw new IllegalStateException("资格赛占位数量与名额配置不一致（p2）");
                }
                ManualPairInput manualQ = manualQualifierPairs != null && qIdx < manualQualifierPairs.size()
                        ? manualQualifierPairs.get(qIdx) : null;
                Long qualP1 = manualQ != null ? manualQ.player1Id : mP2;
                Long qualP2 = manualQ != null ? manualQ.player2Id : challenger;
                Match existingQ = existingMain != null && existingMain.getFeederMatch2Id() != null
                        ? matchService.getById(existingMain.getFeederMatch2Id()) : null;
                Long qid = resolveMountedQualifierMatchId(tournamentId, i, startRoundField, qualP1, qualP2, qSets,
                        operatorId, existingQ, qualScoredIds);
                qIdx++;
                feeder2 = qid;
                mP2 = null;
                touched++;
            }
            touched += upsertFirstRoundMainMatch(tournamentId, startRoundField, i, p.half, mP1, mP2, feeder1, feeder2,
                    p.category, koSets, operatorId, mainCreateSource, existingBySlot, matchIdsWithScores);
        }
        return touched;
    }

    private Long resolveMountedQualifierMatchId(Long tournamentId,
                                                int koSlot,
                                                int startRoundField,
                                                Long p1,
                                                Long p2,
                                                int qSets,
                                                Long operatorId,
                                                Match existingQ,
                                                Set<Long> qualScoredIds) {
        if (isMatchLockedForManualEdit(existingQ, qualScoredIds)) {
            return existingQ.getId();
        }
        if (existingQ != null) {
            setScoreService.lambdaUpdate().eq(SetScore::getMatchId, existingQ.getId()).remove();
            existingQ.setPlayer1Id(p1);
            existingQ.setPlayer2Id(p2);
            existingQ.setHomeUserId(p1);
            existingQ.setAwayUserId(p2);
            existingQ.setWinnerId(null);
            existingQ.setResultLocked(false);
            existingQ.setUpdatedAt(LocalDateTime.now());
            matchService.updateById(existingQ);
            return existingQ.getId();
        }
        return insertQualifierMatch(tournamentId, koSlot, startRoundField, p1, p2, qSets, operatorId);
    }

    private List<PlannedQualifierPair> planMountedQualifierPairs(User operator,
                                                                 Long tournamentId,
                                                                 TournamentCompetitionConfig cfg,
                                                                 List<ManualPairInput> mainPairs) {
        if (!isFirstRoundMountedQualifier(cfg) || mainPairs == null || mainPairs.isEmpty()) {
            return List.of();
        }
        int startRoundField = cfg.getKnockoutStartRound();
        int bracketPlayers = playersInFirstKnockoutRound(startRoundField);
        int mode = cfg.getKnockoutBracketMode() == null ? 0 : cfg.getKnockoutBracketMode();
        List<Long> ranked = loadOverallRankedUserIds(tournamentId, cfg);
        List<Long> seedPool = firstRoundBracketPlayerIds(tournamentId, cfg, bracketPlayers, mode, ranked);
        List<PlannedKo> template = planFirstRound(cfg, seedPool, tournamentId, mode);
        List<PlannedKo> planned = new ArrayList<>();
        for (int i = 0; i < mainPairs.size(); i++) {
            ManualPairInput mp = mainPairs.get(i);
            Integer half = i < template.size() ? template.get(i).half : null;
            planned.add(new PlannedKo(mp.player1Id, mp.player2Id, "", half));
        }
        List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Map<String, Object>>> gr = groupRankRows(tournamentId, cfg, groups);
        MountedQualifierRule rule = resolveMountedQualifierRule(startRoundField, groups.size(), mode);
        Map<Long, Long> userGroupId = new HashMap<>();
        Map<Long, Integer> userGroupRank = new HashMap<>();
        for (TournamentGroup g : groups) {
            for (Map<String, Object> row : gr.getOrDefault(g.getId(), List.of())) {
                Long uid = (Long) row.get("userId");
                if (uid != null) {
                    userGroupId.put(uid, g.getId());
                    userGroupRank.put(uid, (Integer) row.get("groupRank"));
                }
            }
        }
        Set<Long> directSet = new LinkedHashSet<>();
        if (rule.directTopRankPerGroup() > 0) {
            for (TournamentGroup g : groups) {
                for (int r = 1; r <= rule.directTopRankPerGroup(); r++) {
                    Long uid = userAtGroupRank(gr, g.getId(), r);
                    if (uid != null) {
                        directSet.add(uid);
                    }
                }
            }
        }
        List<Long> qualifierOpponents = ranked.subList(bracketPlayers, bracketPlayers + rule.qualifyCount);
        int qIdx = 0;
        Set<Long> usedQualifierOpponents = new LinkedHashSet<>();
        List<PlannedQualifierPair> out = new ArrayList<>();
        for (int i = 0; i < planned.size(); i++) {
            PlannedKo p = planned.get(i);
            if (p.p1 != null && !directSet.contains(p.p1)) {
                int mountedRank = userGroupRank.getOrDefault(p.p1, rule.mountedRankPerGroup);
                Long challenger = pickMountedQualifierOpponent(cfg, qualifierOpponents, qIdx, usedQualifierOpponents,
                        gr, userGroupId.get(p.p2), preferredQualifierOpponentRank(rule, mountedRank));
                if (challenger != null) {
                    out.add(new PlannedQualifierPair(i, p.p1, challenger,
                            "淘汰赛首轮资格赛（关联第" + (i + 1) + "场）"));
                    qIdx++;
                }
            }
            if (p.p2 != null && !directSet.contains(p.p2)) {
                int mountedRank = userGroupRank.getOrDefault(p.p2, rule.mountedRankPerGroup);
                Long challenger = pickMountedQualifierOpponent(cfg, qualifierOpponents, qIdx, usedQualifierOpponents,
                        gr, userGroupId.get(p.p1), preferredQualifierOpponentRank(rule, mountedRank));
                if (challenger != null) {
                    out.add(new PlannedQualifierPair(i, p.p2, challenger,
                            "淘汰赛首轮资格赛（关联第" + (i + 1) + "场）"));
                    qIdx++;
                }
            }
        }
        return out;
    }

    private record PlannedQualifierPair(int mainKoSlot, Long player1Id, Long player2Id, String label) {}

    private int upsertFirstRoundMainMatch(Long tournamentId,
                                          int startRoundField,
                                          int slot,
                                          Integer half,
                                          Long p1,
                                          Long p2,
                                          Long feeder1,
                                          Long feeder2,
                                          String category,
                                          int koSets,
                                          Long operatorId,
                                          String createSource,
                                          Map<Integer, Match> existingBySlot,
                                          Set<Long> matchIdsWithScores) {
        Match existing = existingBySlot.get(slot);
        if (isMatchLockedForManualEdit(existing, matchIdsWithScores)) {
            if (existing != null
                    && Objects.equals(existing.getPlayer1Id(), p1)
                    && Objects.equals(existing.getPlayer2Id(), p2)
                    && Objects.equals(existing.getFeederMatch1Id(), feeder1)
                    && Objects.equals(existing.getFeederMatch2Id(), feeder2)) {
                return 0;
            }
            throw new IllegalStateException("首轮第 " + (slot + 1) + " 场已有比分或已验收，不可修改");
        }
        if (existing != null) {
            setScoreService.lambdaUpdate().eq(SetScore::getMatchId, existing.getId()).remove();
            existing.setPlayer1Id(p1);
            existing.setPlayer2Id(p2);
            existing.setHomeUserId(p1);
            existing.setAwayUserId(p2);
            existing.setFeederMatch1Id(feeder1);
            existing.setFeederMatch2Id(feeder2);
            existing.setKnockoutHalf(half);
            existing.setCategory(category);
            existing.setWinnerId(null);
            existing.setResultLocked(false);
            existing.setUpdatedAt(LocalDateTime.now());
            if (existing.getCreateSource() == null || existing.getCreateSource().isBlank()) {
                existing.setCreateSource(createSource);
            }
            if (existing.getCreatedByUserId() == null) {
                existing.setCreatedByUserId(operatorId);
            }
            matchService.updateById(existing);
            return 1;
        }
        insertKoMatch(tournamentId, startRoundField, slot, half, p1, p2, category, feeder1, feeder2, koSets, "MAIN",
                operatorId, createSource);
        return 1;
    }

    /**
     * 按当前淘汰赛赛况手动补生成下一轮：
     * 仅当某一轮（MAIN/FINAL）全部验收且下一轮尚未落地时，触发晋级链补生成。
     */
    @Transactional(rollbackFor = Exception.class)
    public int generateNextKnockoutRound(User operator, Long tournamentId) {
        if (!canManage(operator, tournamentId)) {
            throw new SecurityException("无权生成下一轮淘汰赛");
        }
        List<Match> ko = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .isNotNull(Match::getRound)
                .orderByDesc(Match::getRound)
                .orderByAsc(Match::getKnockoutHalf)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        if (ko.isEmpty()) {
            throw new IllegalStateException("当前无淘汰赛场次，请先生成首轮");
        }
        Map<Integer, List<Match>> byRound = ko.stream()
                .filter(m -> m.getRound() != null && m.getRound() > 1)
                .collect(Collectors.groupingBy(Match::getRound));
        if (byRound.isEmpty()) {
            throw new IllegalStateException("当前不存在可手动生成的下一轮（需某一轮全部验收且下一轮尚未生成）");
        }
        Integer currentRound = resolveSettledRoundForGenerateNext(byRound);
        if (currentRound == null) {
            throw new IllegalStateException("当前轮次尚未全部验收，无法手动生成下一轮");
        }
        List<Match> oneRound = byRound.getOrDefault(currentRound, List.of());
        if (oneRound.isEmpty()) {
            throw new IllegalStateException("当前不存在可手动生成的下一轮（需某一轮全部验收且下一轮尚未生成）");
        }
        clearKnockoutDownstreamRounds(tournamentId, currentRound);
        long before = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .count();
        for (Match m : oneRound) {
            tryAdvance(m);
        }
        long after = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .count();
        if (after > before) {
            return (int) (after - before);
        }
        throw new IllegalStateException("当前不存在可手动生成的下一轮（需某一轮全部验收且下一轮尚未生成）");
    }

    private static boolean allKnockoutMatchesLocked(List<Match> ms) {
        return ms != null && !ms.isEmpty() && ms.stream().allMatch(m -> Boolean.TRUE.equals(m.getResultLocked()));
    }

    /**
     * 选取应对哪一 MAIN/FINAL 轮（round 数字较大=更靠外、人数更多）执行 clearDownstream + tryAdvance。
     * <p>
     * 若简单取 {@code min(round)}，会在「外层已全部验收、内层已生成或已录分」时仍选中外层，
     * {@link #clearKnockoutDownstreamRounds} 会删除所有 round 更小的 MAIN/FINAL 场次及局分，
     * 从而误清空已存在的 1/4 等内层比赛（含从附加资格赛晋级的对阵）。
     * <p>
     * 规则：自外向内扫描；仅当某外层轮全员验收，且其向内一层要么尚不存在、要么也已全员验收时，
     * 才返回该外层作为晋级源；若内层已存在但未全员验收则报错，避免误删。
     */
    private Integer resolveSettledRoundForGenerateNext(Map<Integer, List<Match>> byRound) {
        List<Integer> desc = new ArrayList<>(byRound.keySet());
        desc.sort(Comparator.reverseOrder());
        for (int outer : desc) {
            List<Match> outerMs = byRound.get(outer);
            if (!allKnockoutMatchesLocked(outerMs)) {
                continue;
            }
            int inner = nextKnockoutRoundField(outer);
            if (inner >= outer) {
                return outer;
            }
            List<Match> innerMs = byRound.getOrDefault(inner, List.of());
            if (!innerMs.isEmpty() && !allKnockoutMatchesLocked(innerMs)) {
                throw new IllegalStateException(
                        "外层淘汰赛已全部验收，但内层仍有未验收场次；请先完成内层轮次再生成下一轮，避免误清空已录场次。");
            }
            if (!innerMs.isEmpty()) {
                continue;
            }
            return outer;
        }
        return null;
    }

    /**
     * 清除指定轮次之后（更靠后轮次，round 值更小）的 MAIN/FINAL 场次及其局分，
     * 便于按当前轮次赛果重新生成下一轮对阵。
     */
    private void clearKnockoutDownstreamRounds(Long tournamentId, int settledRound) {
        List<Match> downstream = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .lt(Match::getRound, settledRound)
                .list();
        for (Match m : downstream) {
            setScoreService.lambdaUpdate().eq(SetScore::getMatchId, m.getId()).remove();
            matchService.removeById(m.getId());
        }
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
        if (pc == null) {
            return;
        }
        if (MatchPhaseClassifier.isQualifier(match)) {
            if (!MatchPhaseClassifier.isKnockoutQualifier(match)) {
                return;
            }
            if (Boolean.TRUE.equals(match.getResultLocked())) {
                tryFillMountedQualifierWinner(match);
            }
            return;
        }
        if (!"MAIN".equalsIgnoreCase(pc) && !"FINAL".equalsIgnoreCase(pc)) {
            return;
        }
        if (!Boolean.TRUE.equals(match.getResultLocked())) {
            return;
        }
        tryAdvance(match);
    }

    private int generateFirstRoundWithMountedQualifiers(User operator,
                                                         Long tournamentId,
                                                         TournamentCompetitionConfig cfg,
                                                         List<Long> ranked,
                                                         List<PlannedKo> planned,
                                                         int koSets,
                                                         int startRoundField,
                                                         int bracketPlayers) {
        applyMountedQualifiersToPlannedFirstRound(
                operator, tournamentId, cfg, ranked, planned, koSets, startRoundField, bracketPlayers,
                Map.of(), Set.of(), SOURCE_AUTO_FROM_GROUP, null);
        return planned.size();
    }

    private int preferredQualifierOpponentRank(MountedQualifierRule rule, int mountedRank) {
        if (rule.startRoundField == 2 && rule.groupCount == 1) {
            // 半决赛 1 组：3vs6、4vs5
            if (mountedRank == 3) return 6;
            if (mountedRank == 4) return 5;
            return rule.challengerRankPerGroup;
        }
        return rule.challengerRankPerGroup;
    }

    private MountedQualifierRule resolveMountedQualifierRule(int startRoundField, int groupCount, int mode) {
        if (startRoundField == 2) {
            if (groupCount == 1) {
                return new MountedQualifierRule(startRoundField, groupCount, 2, 3, 6);
            }
            if (groupCount == 2) {
                return new MountedQualifierRule(startRoundField, groupCount, 2, 2, 3);
            }
            throw new IllegalStateException("半决赛挂载资格赛仅支持 1 组或 2 组");
        }
        if (startRoundField <= 2) {
            throw new IllegalStateException("当前淘汰赛轮次不支持挂载资格赛");
        }
        // 首轮为 1/n 决赛：
        // - 小组数 = n/2：各组 4/5 交叉打资格赛（前 3 直通）
        // - 小组数 = n  ：各组 2/3 交叉打资格赛（前 1 直通）
        if (groupCount * 2 == startRoundField) {
            return new MountedQualifierRule(startRoundField, groupCount, groupCount, 4, 5);
        }
        if (groupCount == startRoundField) {
            return new MountedQualifierRule(startRoundField, groupCount, groupCount, 2, 3);
        }
        throw new IllegalStateException("当前小组数与首轮淘汰赛轮次不匹配，无法按规则挂载资格赛");
    }

    private record MountedQualifierRule(int startRoundField,
                                        int groupCount,
                                        int qualifyCount,
                                        int mountedRankPerGroup,
                                        int challengerRankPerGroup) {
        int directTopRankPerGroup() {
            return Math.max(0, mountedRankPerGroup - 1);
        }
    }

    private Long pickMountedQualifierOpponent(TournamentCompetitionConfig cfg,
                                              List<Long> fallbackOpponents,
                                              int fallbackIndex,
                                              Set<Long> used,
                                              Map<Long, List<Map<String, Object>>> groupRanks,
                                              Long preferredGroupId,
                                              int preferredGroupRank) {
        // 模式1/2 优先按对局选手所在小组的对侧名次线取人（如 A4–B5 中的 B5）
        int bracketMode = cfg == null || cfg.getKnockoutBracketMode() == null ? 0 : cfg.getKnockoutBracketMode();
        if ((bracketMode == 1 || bracketMode == 2) && preferredGroupId != null && preferredGroupRank > 0) {
            Long candidate = userAtGroupRank(groupRanks, preferredGroupId, preferredGroupRank);
            if (candidate != null && !used.contains(candidate)) {
                used.add(candidate);
                return candidate;
            }
            if (bracketMode == 1) {
                // 模式1下若无法命中对侧名次线，直接报错，避免串组取人导致错误对阵
                return null;
            }
        }
        for (int i = Math.max(0, fallbackIndex); i < fallbackOpponents.size(); i++) {
            Long candidate = fallbackOpponents.get(i);
            if (candidate != null && !used.contains(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
        return null;
    }

    private Long insertQualifierMatch(Long tid, int koSlot, int mountedKoRound, Long p1, Long p2, int sets, Long createdByUserId) {
        LocalDateTime now = LocalDateTime.now();
        Match m = new Match();
        m.setTournamentId(tid);
        m.setCategory("淘汰赛首轮资格赛（关联第" + (koSlot + 1) + "场）");
        m.setPhaseCode("QUALIFIER");
        // 与普通资格赛区分：记录其挂载的淘汰赛轮次（16/8/4/2）
        m.setQualifierRound(mountedKoRound);
        m.setRound(1);
        m.setPlayer1Id(p1);
        m.setPlayer2Id(p2);
        m.setHomeUserId(p1);
        m.setAwayUserId(p2);
        m.setWinnerId(null);
        m.setStatus((byte) 0);
        m.setResultLocked(false);
        m.setCreatedByUserId(createdByUserId);
        m.setCreateSource(SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER);
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
        return m.getId();
    }

    private void tryFillMountedQualifierWinner(Match qualifierMatch) {
        if (qualifierMatch.getId() == null || qualifierMatch.getWinnerId() == null) {
            return;
        }
        TournamentCompetitionConfig cfg = configMapper.selectById(qualifierMatch.getTournamentId());
        if (cfg == null || cfg.getKnockoutStartRound() == null) {
            return;
        }
        List<Match> targets = matchService.lambdaQuery()
                .eq(Match::getTournamentId, qualifierMatch.getTournamentId())
                // 仅回填首轮淘汰赛卡位，不影响后续自动晋级轮次
                .eq(Match::getRound, cfg.getKnockoutStartRound())
                .eq(Match::getPhaseCode, "MAIN")
                .and(q -> q.eq(Match::getFeederMatch1Id, qualifierMatch.getId())
                        .or()
                        .eq(Match::getFeederMatch2Id, qualifierMatch.getId()))
                .list();
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (Match ko : targets) {
            boolean changed = false;
            if (Objects.equals(ko.getFeederMatch1Id(), qualifierMatch.getId())
                    && !Objects.equals(ko.getPlayer1Id(), qualifierMatch.getWinnerId())) {
                ko.setPlayer1Id(qualifierMatch.getWinnerId());
                ko.setHomeUserId(qualifierMatch.getWinnerId());
                changed = true;
            }
            if (Objects.equals(ko.getFeederMatch2Id(), qualifierMatch.getId())
                    && !Objects.equals(ko.getPlayer2Id(), qualifierMatch.getWinnerId())) {
                ko.setPlayer2Id(qualifierMatch.getWinnerId());
                ko.setAwayUserId(qualifierMatch.getWinnerId());
                changed = true;
            }
            if (changed) {
                ko.setUpdatedAt(LocalDateTime.now());
                matchService.updateById(ko);
            }
        }
    }

    private void deleteAutoGeneratedKoQualifierMatches(Long tournamentId) {
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        List<Match> qs = findMountedKoQualifierMatches(tournamentId, cfg);
        for (Match q : qs) {
            setScoreService.lambdaUpdate().eq(SetScore::getMatchId, q.getId()).remove();
            matchService.removeById(q.getId());
        }
    }

    /**
     * 识别“首轮淘汰赛挂载资格赛”：
     * 1) 新数据：createSource=AUTO_FROM_GROUP_KO_QUALIFIER
     * 2) 兼容旧数据：phase=QUALIFIER 且 qualifierRound=knockoutStartRound，且 category 为挂载资格赛文案
     */
    private List<Match> findMountedKoQualifierMatches(Long tournamentId, TournamentCompetitionConfig cfg) {
        List<Match> qualifiers = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "QUALIFIER")
                .list();
        if (qualifiers == null || qualifiers.isEmpty()) {
            return List.of();
        }
        Integer startRoundField = cfg == null ? null : cfg.getKnockoutStartRound();
        List<Match> mounted = new ArrayList<>();
        for (Match q : qualifiers) {
            boolean isBySource = MatchPhaseClassifier.isKnockoutQualifier(q)
                    || SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER.equalsIgnoreCase(
                    q.getCreateSource() == null ? "" : q.getCreateSource());
            boolean isLegacyMounted = startRoundField != null
                    && Objects.equals(q.getQualifierRound(), startRoundField)
                    && q.getCategory() != null
                    && (q.getCategory().contains("淘汰赛首轮资格赛") || q.getCategory().contains("关联第"));
            if (isBySource || isLegacyMounted) {
                mounted.add(q);
            }
        }
        return mounted;
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
        if (r > 2) {
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
        if (r == 2) {
            if (existsKoSlot(m.getTournamentId(), 1, 0, null)) {
                return;
            }
            // 同半区内相邻卡位（如 0–1）两场半决赛 → 金/铜牌赛（世界杯式等会把两场半决赛都落在同一 half）
            int partnerSlot = (slot % 2 == 0) ? slot + 1 : slot - 1;
            Match partner = findKoMatch(m.getTournamentId(), r, partnerSlot, half);
            if (partner != null && Boolean.TRUE.equals(partner.getResultLocked())) {
                createGoldAndBronze(m, partner, cfg);
                return;
            }
            // 上下半区各一场半决赛（各 half 的 slot 0）
            Match other = findKoMatchOtherHalf(m.getTournamentId(), 2, half);
            if (other == null || !Boolean.TRUE.equals(other.getResultLocked())) {
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
        var rankingPriority = rankingPriorityService.criteriaOrderForTournament(tournamentId);
        Map<Long, List<Map<String, Object>>> byG = groupRankingCalculator.buildGroupRankingsByMemberIds(
                groups, memberIdsByGroup, uname, matches, scoreByMatch, cfg, rankingPriority);
        boolean allowDrawKo = cfg == null || !Boolean.FALSE.equals(cfg.getGroupAllowDraw());
        int regularSetsKo = (cfg != null && cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;
        groupRankingCalculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                groups, byG, matches, scoreByMatch, Map.of(), uname, allowDrawKo, regularSetsKo, rankingPriority);
        List<Map<String, Object>> overall = groupRankingCalculator.buildOverallRanking(byG, rankingPriority);
        List<Long> out = new ArrayList<>();
        for (Map<String, Object> row : overall) {
            Long uid = (Long) row.get("userId");
            if (uid != null) {
                out.add(uid);
            }
        }
        Set<Long> dqUserIds = tournamentDisqualificationMapper.selectList(Wrappers.<TournamentDisqualification>lambdaQuery()
                        .eq(TournamentDisqualification::getTournamentId, tournamentId)
                        .eq(TournamentDisqualification::getEffective, true))
                .stream()
                .map(TournamentDisqualification::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (dqUserIds.isEmpty()) {
            return out;
        }
        // 取消资格选手不可晋级淘汰赛等后续比赛：直接从候选池移除，晋级名额顺延。
        return out.stream().filter(uid -> !dqUserIds.contains(uid)).toList();
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
            if (n % groups.size() != 0) {
                throw new IllegalStateException("上下半区组间交叉：首轮参赛人数须为小组数的整数倍");
            }
            // 这里应使用“实际晋级人数/每组”而非 groupSize（每组总人数）
            int gsz = n / groups.size();
            List<int[]> hx = KnockoutPairingUtil.halfCrossPairsBetweenTwoGroups(gsz);
            int matchNo = 1;
            for (int h = 0; h < 2; h++) {
                int lo = h == 0 ? 0 : halfCount;
                int hi = h == 0 ? halfCount : groups.size();
                // 每个半区按“相邻两组”成对交叉（例如上半区 A-B、C-D；下半区 E-F、G-H）
                for (int g = lo; g + 1 < hi; g += 2) {
                    TournamentGroup ga = groups.get(g);
                    TournamentGroup gb = groups.get(g + 1);
                    for (int[] pr : hx) {
                        Long a = userAtGroupRank(gr, ga.getId(), pr[0]);
                        Long b = userAtGroupRank(gr, gb.getId(), pr[1]);
                        list.add(new PlannedKo(a, b, koRoundLabel(startField) + " 第" + matchNo + "场", h));
                        matchNo++;
                    }
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
            if (n % gCount != 0) {
                throw new IllegalStateException("未配置每组人数时，世界杯式首轮人数须为小组数的整数倍");
            }
            // 同上：使用实际晋级人数/每组，避免误读为每组总人数
            int q = n / gCount;
            if (n != q * gCount) {
                throw new IllegalStateException("世界杯式首轮人数须等于 小组数×每组晋级人数（当前 " + n + "，期望 " + (q * gCount) + "）");
            }
            // 与模式1一致：每个名次线 t 在相邻组对上各产 1 场，共 q×(组对数) 场（例：4 组 16 人 → 4×2=8 场）。
            // 勿按 half 再复制一轮，否则会重复 16 场并耗尽挂载资格赛名额。
            int matchNo = 1;
            for (int t = 0; t < q; t++) {
                int[] pr = KnockoutPairingUtil.worldCupCrossRankPair(t, q);
                for (int p = 0; p < pairCount; p++) {
                    TournamentGroup g0 = groups.get(2 * p);
                    TournamentGroup g1 = groups.get(2 * p + 1);
                    int half = p;
                    list.add(new PlannedKo(
                            userAtGroupRank(gr, g0.getId(), pr[0]),
                            userAtGroupRank(gr, g1.getId(), pr[1]),
                            koRoundLabel(startField) + " 第" + matchNo + "场", half));
                    matchNo++;
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
        var rankingPriority = rankingPriorityService.criteriaOrderForTournament(tournamentId);
        Map<Long, List<Map<String, Object>>> gr = groupRankingCalculator.buildGroupRankingsByMemberIds(
                groups, memberIdsByGroup, uname, matches, scoreByMatch, cfg, rankingPriority);
        boolean allowDrawGr = cfg == null || !Boolean.FALSE.equals(cfg.getGroupAllowDraw());
        int regularSetsGr = (cfg != null && cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;
        groupRankingCalculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                groups, gr, matches, scoreByMatch, Map.of(), uname, allowDrawGr, regularSetsGr, rankingPriority);
        return gr;
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

    public static final class ManualFirstRoundPageData {
        public final List<ManualPairDraft> mainPairs;
        public final List<ManualPairDraft> qualifierPairs;

        public ManualFirstRoundPageData(List<ManualPairDraft> mainPairs, List<ManualPairDraft> qualifierPairs) {
            this.mainPairs = mainPairs == null ? List.of() : mainPairs;
            this.qualifierPairs = qualifierPairs == null ? List.of() : qualifierPairs;
        }
    }

    public static final class ManualPairDraft {
        public final int slot;
        public final Long defaultPlayer1Id;
        public final Long defaultPlayer2Id;
        public final String label;
        public final boolean locked;
        public final Long matchId;
        /** 挂载资格赛关联的首轮 MAIN 卡位（0 起），仅资格赛行有意义 */
        public final Integer linkedMainKoSlot;

        public ManualPairDraft(int slot, Long defaultPlayer1Id, Long defaultPlayer2Id, String label) {
            this(slot, defaultPlayer1Id, defaultPlayer2Id, label, false, null, null);
        }

        public ManualPairDraft(int slot,
                               Long defaultPlayer1Id,
                               Long defaultPlayer2Id,
                               String label,
                               boolean locked,
                               Long matchId) {
            this(slot, defaultPlayer1Id, defaultPlayer2Id, label, locked, matchId, null);
        }

        public ManualPairDraft(int slot,
                               Long defaultPlayer1Id,
                               Long defaultPlayer2Id,
                               String label,
                               boolean locked,
                               Long matchId,
                               Integer linkedMainKoSlot) {
            this.slot = slot;
            this.defaultPlayer1Id = defaultPlayer1Id;
            this.defaultPlayer2Id = defaultPlayer2Id;
            this.label = label;
            this.locked = locked;
            this.matchId = matchId;
            this.linkedMainKoSlot = linkedMainKoSlot;
        }
    }
}
