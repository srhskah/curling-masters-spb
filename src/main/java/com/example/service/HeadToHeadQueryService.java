package com.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.dto.h2h.*;
import com.example.entity.*;
import com.example.mapper.MatchAcceptanceMapper;
import com.example.util.SeriesDisplayNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HeadToHeadQueryService {

    private static final long MISSING_SEASON_ID = -1L;
    private static final long MISSING_SERIES_ID = -1L;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired private UserService userService;
    @Autowired private IMatchService matchService;
    @Autowired private TournamentService tournamentService;
    @Autowired private SeriesService seriesService;
    @Autowired private SeasonService seasonService;
    @Autowired private ITournamentLevelService tournamentLevelService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private MatchAcceptanceMapper matchAcceptanceMapper;

    public List<H2hUserOption> listAllPlayersForPicker() {
        return userService.lambdaQuery()
                .orderByAsc(User::getUsername)
                .list()
                .stream()
                .filter(u -> u.getId() != null && u.getUsername() != null && !u.getUsername().isBlank())
                .map(u -> new H2hUserOption(u.getId(), u.getUsername()))
                .toList();
    }

    public List<H2hUserOption> listOpponentsOf(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Set<Long> opp = new LinkedHashSet<>();
        List<Match> ms = listMatchesInvolvingUser(userId);
        for (Match m : ms) {
            Long other = opponentUserId(m, userId);
            if (other != null && !Objects.equals(other, userId) && other > 0) {
                opp.add(other);
            }
        }
        if (opp.isEmpty()) {
            return List.of();
        }
        List<User> users = userService.listByIds(opp);
        users.sort(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER));
        return users.stream()
                .map(u -> new H2hUserOption(u.getId(), u.getUsername()))
                .toList();
    }

    /**
     * 从一场比赛中解析「另一名选手」：综合 player1/2 与主客四槽位中已出现的不同 userId
     * （避免仅一侧为 null 时无法识别对手；轮空/同 id 等仍可能无对手）。
     */
    private static Long opponentUserId(Match m, long userId) {
        Set<Long> involved = new LinkedHashSet<>();
        addUserRef(involved, m.getPlayer1Id());
        addUserRef(involved, m.getPlayer2Id());
        addUserRef(involved, m.getHomeUserId());
        addUserRef(involved, m.getAwayUserId());
        if (!involved.contains(userId)) {
            return null;
        }
        for (Long u : involved) {
            if (u != null && u > 0 && !Objects.equals(u, userId)) {
                return u;
            }
        }
        return null;
    }

    public H2hPayload buildPayload(Long userId1, Long userId2) {
        if (userId1 == null) {
            return new H2hPayload(List.of());
        }
        if (userId2 != null && Objects.equals(userId1, userId2)) {
            return new H2hPayload(List.of());
        }
        List<Match> matches;
        if (userId2 == null) {
            matches = listMatchesInvolvingUser(userId1);
        } else {
            matches = listMatchesHeadToHead(userId1, userId2);
        }
        if (matches.isEmpty()) {
            return new H2hPayload(List.of());
        }
        return buildPayloadFromMatches(matches);
    }

    /**
     * 供超级管理员调试：对比「槽位查询」与「验收表仅作参考计数」、以及前端分组后的场次数量（正式列表不含验收兜底场次）。
     */
    public H2hDebugStats debugMatchStats(Long userId1, Long userId2) {
        if (userId1 == null) {
            return new H2hDebugStats(null, userId2, "MISSING_USER1",
                    0, 0, 0, 0, 0, 0);
        }
        if (userId2 != null && Objects.equals(userId1, userId2)) {
            return new H2hDebugStats(userId1, userId2, "INVALID_SAME_USER",
                    0, 0, 0, 0, 0, 0);
        }
        if (userId2 == null) {
            return debugMatchStatsSingle(userId1);
        }
        return debugMatchStatsHeadToHead(userId1, userId2);
    }

    private H2hDebugStats debugMatchStatsSingle(long userId1) {
        LambdaQueryWrapper<Match> directWrapper = Wrappers.<Match>lambdaQuery()
                .and(q -> q.eq(Match::getPlayer1Id, userId1)
                        .or().eq(Match::getPlayer2Id, userId1)
                        .or().eq(Match::getHomeUserId, userId1)
                        .or().eq(Match::getAwayUserId, userId1));
        Set<Long> directIds = matchService.list(directWrapper).stream()
                .map(Match::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int acceptanceDistinctIds = (int) matchAcceptanceMapper.selectList(
                        Wrappers.<MatchAcceptance>lambdaQuery()
                                .eq(MatchAcceptance::getUserId, userId1)
                                .isNotNull(MatchAcceptance::getMatchId))
                .stream()
                .map(MatchAcceptance::getMatchId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        List<Match> mergedList = listMatchesInvolvingUser(userId1);
        int acceptanceOnly = (int) mergedList.stream()
                .map(Match::getId)
                .filter(Objects::nonNull)
                .filter(id -> !directIds.contains(id))
                .count();

        H2hPayload payload = buildPayload(userId1, null);
        int payloadSeasonCount = payload.seasons().size();
        int payloadTotal = countPayloadMatches(payload);

        return new H2hDebugStats(userId1, null, "SINGLE_USER",
                directIds.size(),
                acceptanceDistinctIds,
                acceptanceOnly,
                mergedList.size(),
                payloadSeasonCount,
                payloadTotal);
    }

    private H2hDebugStats debugMatchStatsHeadToHead(long u1, long u2) {
        LambdaQueryWrapper<Match> directWrapper = Wrappers.<Match>lambdaQuery()
                .and(q -> q
                        .and(x -> x.eq(Match::getPlayer1Id, u1).eq(Match::getPlayer2Id, u2))
                        .or(x -> x.eq(Match::getPlayer1Id, u2).eq(Match::getPlayer2Id, u1))
                        .or(x -> x.eq(Match::getHomeUserId, u1).eq(Match::getAwayUserId, u2))
                        .or(x -> x.eq(Match::getHomeUserId, u2).eq(Match::getAwayUserId, u1)));
        Set<Long> directIds = matchService.list(directWrapper).stream()
                .map(Match::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> acc1 = matchAcceptanceMapper.selectList(
                        Wrappers.<MatchAcceptance>lambdaQuery()
                                .eq(MatchAcceptance::getUserId, u1)
                                .isNotNull(MatchAcceptance::getMatchId))
                .stream()
                .map(MatchAcceptance::getMatchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> acc2 = matchAcceptanceMapper.selectList(
                        Wrappers.<MatchAcceptance>lambdaQuery()
                                .eq(MatchAcceptance::getUserId, u2)
                                .isNotNull(MatchAcceptance::getMatchId))
                .stream()
                .map(MatchAcceptance::getMatchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> intersection = new HashSet<>(acc1);
        intersection.retainAll(acc2);
        int acceptanceIntersection = intersection.size();

        List<Match> mergedList = listMatchesHeadToHead(u1, u2);
        int acceptanceOnly = (int) mergedList.stream()
                .map(Match::getId)
                .filter(Objects::nonNull)
                .filter(id -> !directIds.contains(id))
                .count();

        H2hPayload payload = buildPayload(u1, u2);
        int payloadSeasonCount = payload.seasons().size();
        int payloadTotal = countPayloadMatches(payload);

        return new H2hDebugStats(u1, u2, "HEAD_TO_HEAD",
                directIds.size(),
                acceptanceIntersection,
                acceptanceOnly,
                mergedList.size(),
                payloadSeasonCount,
                payloadTotal);
    }

    private static int countPayloadMatches(H2hPayload payload) {
        if (payload == null || payload.seasons() == null) {
            return 0;
        }
        int n = 0;
        for (H2hSeasonNode season : payload.seasons()) {
            if (season == null || season.series() == null) {
                continue;
            }
            for (H2hSeriesNode series : season.series()) {
                if (series == null || series.levels() == null) {
                    continue;
                }
                for (H2hLevelNode level : series.levels()) {
                    if (level != null && level.matches() != null) {
                        n += level.matches().size();
                    }
                }
            }
        }
        return n;
    }

    private H2hPayload buildPayloadFromMatches(List<Match> matches) {
        Set<Long> tournamentIds = matches.stream().map(Match::getTournamentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Tournament> tournaments = tournamentIds.isEmpty()
                ? Map.of()
                : tournamentService.listByIds(tournamentIds).stream().collect(Collectors.toMap(Tournament::getId, t -> t));

        Set<Long> seriesIds = tournaments.values().stream().map(Tournament::getSeriesId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Series> seriesById = new HashMap<>();
        for (Long sid : seriesIds) {
            Series s = seriesService.getById(sid);
            if (s != null) {
                seriesById.put(sid, s);
            }
        }

        Set<Long> seasonIds = seriesById.values().stream().map(Series::getSeasonId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Season> seasonById = new HashMap<>();
        for (Long seId : seasonIds) {
            Season se = seasonService.getById(seId);
            if (se != null) {
                seasonById.put(seId, se);
            }
        }

        Set<String> levelCodes = tournaments.values().stream().map(Tournament::getLevelCode).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> levelNameByCode = new HashMap<>();
        for (String code : levelCodes) {
            TournamentLevel tl = tournamentLevelService.lambdaQuery().eq(TournamentLevel::getCode, code).one();
            levelNameByCode.put(code, tl != null && tl.getName() != null ? tl.getName() : code);
        }

        Set<Long> userIds = new HashSet<>();
        for (Match m : matches) {
            addUserRef(userIds, m.getPlayer1Id());
            addUserRef(userIds, m.getPlayer2Id());
            addUserRef(userIds, m.getHomeUserId());
            addUserRef(userIds, m.getAwayUserId());
            addUserRef(userIds, m.getWinnerId());
        }
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, int[]> totalsByMatch = computeTotals(matches.stream().map(Match::getId).filter(Objects::nonNull).toList());

        record BucketKey(long seasonId, long seriesId, String levelCode) {
        }

        Map<BucketKey, List<H2hMatchRow>> bucket = new LinkedHashMap<>();

        for (Match m : matches) {
            Tournament t = tournaments.get(m.getTournamentId());
            long seasonId = MISSING_SEASON_ID;
            long seriesId = MISSING_SERIES_ID;
            String seasonLabel = "（未知赛季）";
            String seriesLabel = "（未知系列）";
            String levelCode = "?";
            String levelName = "?";
            String tournamentTitle = "";

            if (t != null) {
                levelCode = t.getLevelCode() != null ? t.getLevelCode() : "?";
                levelName = levelNameByCode.getOrDefault(levelCode, levelCode);
                tournamentTitle = seasonSeriesTournamentTitle(t, seriesById, seasonById);
                if (t.getSeriesId() != null) {
                    Series ser = seriesById.get(t.getSeriesId());
                    if (ser != null) {
                        seriesId = ser.getId();
                        seriesLabel = SeriesDisplayNames.seriesDisplayName(seriesService, ser);
                        if (ser.getSeasonId() != null) {
                            Season se = seasonById.get(ser.getSeasonId());
                            if (se != null) {
                                seasonId = se.getId();
                                seasonLabel = SeriesDisplayNames.formatSeasonShort(se);
                            }
                        }
                    }
                }
            }

            Long pid1 = firstNonNull(m.getPlayer1Id(), m.getHomeUserId());
            Long pid2 = firstNonNull(m.getPlayer2Id(), m.getAwayUserId());
            User u1 = pid1 != null ? usersById.get(pid1) : null;
            User u2 = pid2 != null ? usersById.get(pid2) : null;
            User uw = m.getWinnerId() != null ? usersById.get(m.getWinnerId()) : null;
            int[] tot = totalsByMatch.getOrDefault(m.getId(), new int[]{0, 0});

            String scheduled = m.getScheduledTime() != null ? DT_FMT.format(m.getScheduledTime()) : "";

            H2hMatchRow row = new H2hMatchRow(
                    m.getId(),
                    m.getTournamentId() != null ? m.getTournamentId() : 0L,
                    tournamentTitle,
                    m.getCategory() != null ? m.getCategory() : "",
                    m.getRound(),
                    m.getPhaseCode() != null ? m.getPhaseCode() : "",
                    scheduled,
                    pid1 != null ? pid1 : 0L,
                    pid2 != null ? pid2 : 0L,
                    u1 != null ? u1.getUsername() : "?",
                    u2 != null ? u2.getUsername() : "?",
                    m.getWinnerId(),
                    uw != null ? uw.getUsername() : null,
                    m.getStatus() != null ? m.getStatus() : 0,
                    tot[0],
                    tot[1],
                    Boolean.TRUE.equals(m.getResultLocked())
            );

            BucketKey key = new BucketKey(seasonId, seriesId, levelCode);
            bucket.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        Comparator<Season> seasonSort = Comparator
                .comparing(Season::getYear, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Season::getHalf, Comparator.nullsLast(Comparator.reverseOrder()));

        Comparator<H2hMatchRow> matchSort = Comparator
                .comparing(H2hMatchRow::scheduledTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(H2hMatchRow::matchId, Comparator.reverseOrder());

        for (List<H2hMatchRow> list : bucket.values()) {
            list.sort(matchSort);
        }

        List<Long> seasonOrder = bucket.keySet().stream()
                .map(BucketKey::seasonId)
                .distinct()
                .sorted((a, b) -> {
                    if (a.equals(b)) {
                        return 0;
                    }
                    Season sa = a.equals(MISSING_SEASON_ID) ? null : seasonById.get(a);
                    Season sb = b.equals(MISSING_SEASON_ID) ? null : seasonById.get(b);
                    if (sa == null && sb == null) {
                        return 0;
                    }
                    if (sa == null) {
                        return 1;
                    }
                    if (sb == null) {
                        return -1;
                    }
                    return seasonSort.compare(sa, sb);
                })
                .toList();

        List<H2hSeasonNode> outSeasons = new ArrayList<>();
        for (Long sid : seasonOrder) {
            String seasonLabel = sid.equals(MISSING_SEASON_ID)
                    ? "（未知赛季）"
                    : SeriesDisplayNames.formatSeasonShort(seasonById.get(sid));

            List<BucketKey> keysThisSeason = bucket.keySet().stream()
                    .filter(k -> Objects.equals(k.seasonId(), sid))
                    .sorted(Comparator
                            .comparing(BucketKey::seriesId)
                            .thenComparing(BucketKey::levelCode, Comparator.nullsLast(String::compareTo)))
                    .toList();

            Map<Long, List<BucketKey>> bySeries = new LinkedHashMap<>();
            for (BucketKey k : keysThisSeason) {
                bySeries.computeIfAbsent(k.seriesId(), x -> new ArrayList<>()).add(k);
            }

            List<Long> seriesIdsOrdered = bySeries.keySet().stream()
                    .sorted((a, b) -> compareSeriesOrder(a, b, seriesById))
                    .toList();

            List<H2hSeriesNode> seriesNodes = new ArrayList<>();
            for (Long serId : seriesIdsOrdered) {
                String serLabel = serId.equals(MISSING_SERIES_ID)
                        ? "（未知系列）"
                        : SeriesDisplayNames.seriesDisplayName(seriesService, seriesById.get(serId));
                List<BucketKey> keys = bySeries.get(serId);
                keys.sort(Comparator.comparing(BucketKey::levelCode, Comparator.nullsLast(String::compareTo)));

                List<H2hLevelNode> levels = new ArrayList<>();
                for (BucketKey lk : keys) {
                    List<H2hMatchRow> rows = bucket.get(lk);
                    String lc = lk.levelCode();
                    String ln = levelNameByCode.getOrDefault(lc, lc);
                    levels.add(new H2hLevelNode(lc, ln, rows));
                }
                seriesNodes.add(new H2hSeriesNode(serId.equals(MISSING_SERIES_ID) ? -1L : serId, serLabel, levels));
            }
            outSeasons.add(new H2hSeasonNode(sid.equals(MISSING_SEASON_ID) ? -1L : sid, seasonLabel, seriesNodes));
        }

        return new H2hPayload(outSeasons);
    }

    private int compareSeriesOrder(Long a, Long b, Map<Long, Series> seriesById) {
        if (Objects.equals(a, b)) {
            return 0;
        }
        if (a.equals(MISSING_SERIES_ID)) {
            return 1;
        }
        if (b.equals(MISSING_SERIES_ID)) {
            return -1;
        }
        Series sa = seriesById.get(a);
        Series sb = seriesById.get(b);
        if (sa == null && sb == null) {
            return Long.compare(a, b);
        }
        if (sa == null) {
            return 1;
        }
        if (sb == null) {
            return -1;
        }
        /* sequence 越大表示该赛季内系列越新（与首页 Series 列表 orderByDesc 一致），H2H 展示从新到旧 */
        int cmp = Integer.compare(
                sa.getSequence() != null ? sa.getSequence() : 0,
                sb.getSequence() != null ? sb.getSequence() : 0);
        if (cmp != 0) {
            return -cmp;
        }
        return Long.compare(a, b);
    }

    private String seasonSeriesTournamentTitle(Tournament t, Map<Long, Series> seriesById, Map<Long, Season> seasonById) {
        if (t == null) {
            return "";
        }
        Series ser = t.getSeriesId() != null ? seriesById.get(t.getSeriesId()) : null;
        Season se = ser != null && ser.getSeasonId() != null ? seasonById.get(ser.getSeasonId()) : null;
        String seasonText = se != null ? SeriesDisplayNames.formatSeasonShort(se) : "";
        String seriesText = ser != null ? SeriesDisplayNames.seriesDisplayName(seriesService, ser) : "";
        String level = t.getLevelCode() != null ? t.getLevelCode() : "";
        StringBuilder sb = new StringBuilder();
        if (!seasonText.isEmpty()) {
            sb.append(seasonText);
        }
        if (!seriesText.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(seriesText);
        }
        if (!level.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(level);
        }
        return sb.toString();
    }

    private Map<Long, int[]> computeTotals(List<Long> matchIds) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<SetScore> w = new LambdaQueryWrapper<>();
        w.in(SetScore::getMatchId, matchIds);
        List<SetScore> all = setScoreService.list(w);
        Map<Long, int[]> out = new HashMap<>();
        for (SetScore ss : all) {
            int[] arr = out.computeIfAbsent(ss.getMatchId(), k -> new int[]{0, 0});
            arr[0] += ss.getPlayer1Score() != null ? ss.getPlayer1Score() : 0;
            arr[1] += ss.getPlayer2Score() != null ? ss.getPlayer2Score() : 0;
        }
        return out;
    }

    private static void addUserRef(Set<Long> ids, Long uid) {
        if (uid != null && uid > 0) {
            ids.add(uid);
        }
    }

    private static Long firstNonNull(Long a, Long b) {
        return a != null ? a : b;
    }

    /**
     * 该选手作为出场方出现在 player1/player2 或主/客队槽位中的全部场次（不含仅验收、未出现在上述槽位的比赛）。
     */
    private List<Match> listMatchesInvolvingUser(long userId) {
        LambdaQueryWrapper<Match> directWrapper = Wrappers.<Match>lambdaQuery()
                .and(q -> q.eq(Match::getPlayer1Id, userId)
                        .or().eq(Match::getPlayer2Id, userId)
                        .or().eq(Match::getHomeUserId, userId)
                        .or().eq(Match::getAwayUserId, userId));
        return matchService.list(directWrapper);
    }

    /**
     * 双方在 player 槽位或主客槽位上互为对阵的场次（不含仅靠 match_acceptance 关联的同场记录）。
     */
    private List<Match> listMatchesHeadToHead(long u1, long u2) {
        LambdaQueryWrapper<Match> directWrapper = Wrappers.<Match>lambdaQuery()
                .and(q -> q
                        .and(x -> x.eq(Match::getPlayer1Id, u1).eq(Match::getPlayer2Id, u2))
                        .or(x -> x.eq(Match::getPlayer1Id, u2).eq(Match::getPlayer2Id, u1))
                        .or(x -> x.eq(Match::getHomeUserId, u1).eq(Match::getAwayUserId, u2))
                        .or(x -> x.eq(Match::getHomeUserId, u2).eq(Match::getAwayUserId, u1)));
        return matchService.list(directWrapper);
    }
}
