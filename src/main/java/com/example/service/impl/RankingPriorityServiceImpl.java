package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.RankingPriorityEntry;
import com.example.entity.Series;
import com.example.entity.Tournament;
import com.example.entity.TournamentRankingPriorityEntry;
import com.example.mapper.RankingPriorityEntryMapper;
import com.example.mapper.SeriesMapper;
import com.example.mapper.TournamentMapper;
import com.example.mapper.TournamentRankingPriorityEntryMapper;
import com.example.ranking.RankingCriterion;
import com.example.service.RankingPriorityService;
import com.example.service.SystemConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RankingPriorityServiceImpl implements RankingPriorityService {

    private final RankingPriorityEntryMapper entryMapper;
    private final TournamentRankingPriorityEntryMapper tournamentEntryMapper;
    private final SeriesMapper seriesMapper;
    private final TournamentMapper tournamentMapper;
    private final SystemConfigService systemConfigService;

    public RankingPriorityServiceImpl(RankingPriorityEntryMapper entryMapper,
                                      TournamentRankingPriorityEntryMapper tournamentEntryMapper,
                                      SeriesMapper seriesMapper,
                                      TournamentMapper tournamentMapper,
                                      SystemConfigService systemConfigService) {
        this.entryMapper = entryMapper;
        this.tournamentEntryMapper = tournamentEntryMapper;
        this.seriesMapper = seriesMapper;
        this.tournamentMapper = tournamentMapper;
        this.systemConfigService = systemConfigService;
    }

    @Override
    public List<Map<String, Object>> listQueue() {
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (RankingCriterion c : queueForDisplay()) {
            Map<String, Object> row = RankingCriterion.toUiMap(c);
            row.put("priorityRank", rank++);
            out.add(row);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> listCandidates() {
        Set<RankingCriterion> inQueue = new HashSet<>(queueForDisplay());
        List<Map<String, Object>> out = new ArrayList<>();
        for (RankingCriterion c : RankingCriterion.values()) {
            if (!inQueue.contains(c)) {
                out.add(RankingCriterion.toUiMap(c));
            }
        }
        return out;
    }

    @Override
    @Transactional
    public void saveQueueAndSeriesFlags(List<String> orderedCriterionCodes, List<Long> enabledSeriesIds) {
        List<RankingCriterion> ordered = parseOrderedCriteria(orderedCriterionCodes);

        entryMapper.delete(Wrappers.<RankingPriorityEntry>lambdaQuery());
        LocalDateTime now = LocalDateTime.now();
        int order = 1;
        for (RankingCriterion c : ordered) {
            RankingPriorityEntry e = new RankingPriorityEntry();
            e.setCriterionCode(c.getCode());
            e.setSortOrder(order++);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            entryMapper.insert(e);
        }

        Set<Long> enable = enabledSeriesIds == null ? Set.of() : enabledSeriesIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        for (Series s : seriesMapper.selectList(null)) {
            boolean on = enable.contains(s.getId());
            Boolean cur = s.getRankingPriorityEnabled();
            if (cur == null || cur != on) {
                s.setRankingPriorityEnabled(on);
                seriesMapper.updateById(s);
            }
        }

        systemConfigService.setConfigValue(
                CONFIG_APPLY_TO_NEW_SERIES, "true", "保存排名参考优先级后，新建赛事系列默认启用");
    }

    @Override
    public boolean applyToNewSeries() {
        return "true".equalsIgnoreCase(
                systemConfigService.getConfigValue(CONFIG_APPLY_TO_NEW_SERIES, "false"));
    }

    @Override
    public List<RankingCriterion> criteriaOrderForTournament(Long tournamentId) {
        if (tournamentId == null) {
            return null;
        }
        Tournament t = tournamentMapper.selectById(tournamentId);
        if (t == null) {
            return null;
        }
        if (Boolean.TRUE.equals(t.getRankingPriorityOverride())) {
            return loadTournamentStoredOrder(tournamentId);
        }
        return criteriaOrderForSeries(t.getSeriesId());
    }

    @Override
    public boolean hasTournamentOverride(Long tournamentId) {
        if (tournamentId == null) {
            return false;
        }
        Tournament t = tournamentMapper.selectById(tournamentId);
        return t != null && Boolean.TRUE.equals(t.getRankingPriorityOverride());
    }

    @Override
    public Map<String, Object> editorStateForTournament(Long tournamentId) {
        Tournament t = tournamentId == null ? null : tournamentMapper.selectById(tournamentId);
        boolean override = t != null && Boolean.TRUE.equals(t.getRankingPriorityOverride());
        List<RankingCriterion> order;
        if (override) {
            order = loadTournamentStoredOrder(tournamentId);
        } else {
            List<RankingCriterion> seriesOrder = t == null ? null : criteriaOrderForSeries(t.getSeriesId());
            order = seriesOrder != null ? seriesOrder : new ArrayList<>(RankingCriterion.DEFAULT_ORDER);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("override", override);
        out.put("queue", toQueueMaps(order));
        out.put("candidates", toCandidateMaps(order));
        return out;
    }

    @Override
    @Transactional
    public void saveTournamentQueue(Long tournamentId, List<String> orderedCriterionCodes) {
        if (tournamentId == null) {
            throw new IllegalArgumentException("缺少赛事ID");
        }
        Tournament t = tournamentMapper.selectById(tournamentId);
        if (t == null) {
            throw new IllegalArgumentException("赛事不存在");
        }
        List<RankingCriterion> ordered = parseOrderedCriteria(orderedCriterionCodes);
        replaceTournamentEntries(tournamentId, ordered);
        if (!Boolean.TRUE.equals(t.getRankingPriorityOverride())) {
            t.setRankingPriorityOverride(true);
            tournamentMapper.updateById(t);
        }
    }

    @Override
    @Transactional
    public void clearTournamentOverride(Long tournamentId) {
        if (tournamentId == null) {
            throw new IllegalArgumentException("缺少赛事ID");
        }
        Tournament t = tournamentMapper.selectById(tournamentId);
        if (t == null) {
            throw new IllegalArgumentException("赛事不存在");
        }
        tournamentEntryMapper.delete(Wrappers.<TournamentRankingPriorityEntry>lambdaQuery()
                .eq(TournamentRankingPriorityEntry::getTournamentId, tournamentId));
        if (Boolean.TRUE.equals(t.getRankingPriorityOverride())) {
            t.setRankingPriorityOverride(false);
            tournamentMapper.updateById(t);
        }
    }

    @Override
    public List<RankingCriterion> criteriaOrderForSeries(Long seriesId) {
        if (seriesId == null) {
            return null;
        }
        Series series = seriesMapper.selectById(seriesId);
        if (series == null || !Boolean.TRUE.equals(series.getRankingPriorityEnabled())) {
            return null;
        }
        List<RankingCriterion> stored = loadStoredOrder();
        // 启用但尚未保存过队列：沿用代码默认
        return stored.isEmpty() ? null : stored;
    }

    @Override
    public Set<String> visibleColumnCodesForTournament(Long tournamentId) {
        List<RankingCriterion> order = criteriaOrderForTournament(tournamentId);
        if (order == null) {
            return null;
        }
        Set<String> cols = new HashSet<>();
        for (RankingCriterion c : order) {
            if (c.isHasDisplayColumn()) {
                cols.add(c.getCode());
            }
        }
        return cols;
    }

    private List<RankingCriterion> parseOrderedCriteria(List<String> orderedCriterionCodes) {
        List<RankingCriterion> ordered = new ArrayList<>();
        Set<RankingCriterion> seen = EnumSet.noneOf(RankingCriterion.class);
        if (orderedCriterionCodes != null) {
            for (String code : orderedCriterionCodes) {
                RankingCriterion c = RankingCriterion.fromCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("未知决胜项：" + code));
                if (seen.add(c)) {
                    ordered.add(c);
                }
            }
        }
        return ordered;
    }

    private void replaceTournamentEntries(Long tournamentId, List<RankingCriterion> ordered) {
        tournamentEntryMapper.delete(Wrappers.<TournamentRankingPriorityEntry>lambdaQuery()
                .eq(TournamentRankingPriorityEntry::getTournamentId, tournamentId));
        LocalDateTime now = LocalDateTime.now();
        int order = 1;
        for (RankingCriterion c : ordered) {
            TournamentRankingPriorityEntry e = new TournamentRankingPriorityEntry();
            e.setTournamentId(tournamentId);
            e.setCriterionCode(c.getCode());
            e.setSortOrder(order++);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            tournamentEntryMapper.insert(e);
        }
    }

    private List<RankingCriterion> loadTournamentStoredOrder(Long tournamentId) {
        List<TournamentRankingPriorityEntry> entries = tournamentEntryMapper.selectList(
                Wrappers.<TournamentRankingPriorityEntry>lambdaQuery()
                        .eq(TournamentRankingPriorityEntry::getTournamentId, tournamentId)
                        .orderByAsc(TournamentRankingPriorityEntry::getSortOrder)
                        .orderByAsc(TournamentRankingPriorityEntry::getId));
        List<RankingCriterion> out = new ArrayList<>();
        Set<RankingCriterion> seen = EnumSet.noneOf(RankingCriterion.class);
        for (TournamentRankingPriorityEntry e : entries) {
            RankingCriterion.fromCode(e.getCriterionCode()).ifPresent(c -> {
                if (seen.add(c)) {
                    out.add(c);
                }
            });
        }
        return out;
    }

    private List<Map<String, Object>> toQueueMaps(List<RankingCriterion> order) {
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (RankingCriterion c : order) {
            Map<String, Object> row = RankingCriterion.toUiMap(c);
            row.put("priorityRank", rank++);
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> toCandidateMaps(List<RankingCriterion> inQueue) {
        Set<RankingCriterion> queued = new HashSet<>(inQueue);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RankingCriterion c : RankingCriterion.values()) {
            if (!queued.contains(c)) {
                out.add(RankingCriterion.toUiMap(c));
            }
        }
        return out;
    }

    private List<RankingCriterion> loadStoredOrder() {
        List<RankingPriorityEntry> entries = entryMapper.selectList(
                Wrappers.<RankingPriorityEntry>lambdaQuery()
                        .orderByAsc(RankingPriorityEntry::getSortOrder)
                        .orderByAsc(RankingPriorityEntry::getId));
        List<RankingCriterion> out = new ArrayList<>();
        Set<RankingCriterion> seen = EnumSet.noneOf(RankingCriterion.class);
        for (RankingPriorityEntry e : entries) {
            RankingCriterion.fromCode(e.getCriterionCode()).ifPresent(c -> {
                if (seen.add(c)) {
                    out.add(c);
                }
            });
        }
        return out;
    }

    /** 控制台展示：库中无配置时，把当前代码默认顺序放进队列便于调整 */
    private List<RankingCriterion> queueForDisplay() {
        List<RankingCriterion> stored = loadStoredOrder();
        return stored.isEmpty() ? new ArrayList<>(RankingCriterion.DEFAULT_ORDER) : stored;
    }
}
