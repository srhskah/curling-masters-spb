package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.*;
import com.example.mapper.*;
import com.example.service.*;
import com.example.dto.DrawPool;
import com.example.dto.TournamentRegistrationRowDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class DrawManagementService {

    private static final String SRC_DRAW_OPEN = "DRAW_OPEN";
    private static final String SRC_DRAW_COMPLETE = "DRAW_COMPLETE";
    @Autowired private TournamentDrawMapper drawMapper;
    @Autowired private TournamentDrawResultMapper drawResultMapper;
    @Autowired private TournamentRegistrationMapper registrationMapper;
    @Autowired private TournamentGroupMapper groupMapper;
    @Autowired private TournamentGroupMemberMapper groupMemberMapper;
    @Autowired private UserService userService;
    @Autowired private TournamentService tournamentService;
    @Autowired private ITournamentRegistrationService registrationService;
    @Autowired private TournamentCompetitionConfigMapper competitionConfigMapper;
    @Autowired private INotificationService notificationService;
    @Autowired private TournamentEntryService tournamentEntryService;

    private TournamentDraw loadDraw(Long tournamentId, DrawPool pool) {
        return drawMapper.selectOne(Wrappers.<TournamentDraw>lambdaQuery()
                .eq(TournamentDraw::getTournamentId, tournamentId)
                .eq(TournamentDraw::getDrawPool, pool.name()));
    }

    /** 正赛+资格赛：每组前若干位为资格赛席，其余为直通席 */
    public int qualifierSlotsPerGroup(Long tournamentId) {
        TournamentCompetitionConfig cfg = competitionConfigMapper.selectById(tournamentId);
        if (cfg == null || cfg.getEntryMode() == null || cfg.getEntryMode() != 1) {
            return 0;
        }
        Integer k = cfg.getKnockoutQualifyCount();
        int gs = cfg.getGroupSize() != null ? cfg.getGroupSize() : 0;
        int pc = cfg.getParticipantCount() != null ? cfg.getParticipantCount() : 0;
        if (k == null || k < 1 || gs < 1 || pc < gs || pc % gs != 0) {
            return 0;
        }
        int gc = pc / gs;
        return (k + gc - 1) / gc;
    }

    /**
     * 获取小组数的有效因数（排除1和本身）
     */
    public List<Integer> getValidGroupCounts(int totalPlayers) {
        List<Integer> factors = new ArrayList<>();
        for (int i = 2; i < totalPlayers; i++) {
            if (totalPlayers % i == 0) {
                factors.add(i);
            }
        }
        return factors;
    }

    /**
     * 获取档数的有效因数（不包括1，包括本身）
     */
    public List<Integer> getValidTierCounts(int playersPerGroup) {
        List<Integer> factors = new ArrayList<>();
        for (int i = 2; i <= playersPerGroup; i++) {
            if (playersPerGroup % i == 0) {
                factors.add(i);
            }
        }
        return factors;
    }

    public long countGroupMembers(Long tournamentId) {
        return groupMemberMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tournamentId));
    }

    /**
     * 直通车抽签：按小组数重建空 {@code tournament_group} 骨架。
     * 必须先确认 {@code tournament} 在库中存在，否则插入会触发外键失败。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncMainDrawGroupSkeleton(Long tournamentId, int groupCount) {
        if (tournamentId == null || tournamentId <= 0) {
            throw new IllegalArgumentException("赛事编号无效");
        }
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null || t.getId() == null) {
            throw new IllegalStateException("赛事不存在，无法创建小组骨架（请从赛事详情重新进入抽签页）");
        }
        Long tid = t.getId();
        List<TournamentGroup> existing = groupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        if (existing.size() == groupCount) {
            return;
        }
        groupMapper.delete(Wrappers.<TournamentGroup>lambdaQuery().eq(TournamentGroup::getTournamentId, tid));
        String[] groupNames = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < groupCount; i++) {
            TournamentGroup group = new TournamentGroup();
            group.setTournamentId(tid);
            group.setGroupName(i < groupNames.length ? groupNames[i] + "组" : ("第" + (i + 1) + "组"));
            group.setGroupOrder(i + 1);
            group.setCreatedAt(now);
            groupMapper.insert(group);
        }
    }

    /** 抽签是否已开放（报名截止后写入 draw_opened_at，或截止前手动保存则截止时自动写入） */
    public boolean isDrawWindowOpen(TournamentDraw draw, LocalDateTime now) {
        if (draw == null || draw.getDrawOpenedAt() == null) {
            return false;
        }
        // 容忍 JDBC/MySQL 对 LocalDateTime 的微秒舍入，避免「开放瞬间」略晚于紧接着的 now() 被误判为未开放
        LocalDateTime opened = draw.getDrawOpenedAt().minus(1, ChronoUnit.MILLIS);
        return !now.isBefore(opened);
    }
    
    /**
     * 获取抽签配置信息（包括报名名单和赛事配置）
     */
    public Map<String, Object> getDrawConfig(Long tournamentId) {
        return getDrawConfig(tournamentId, DrawPool.MAIN);
    }

    /**
     * @param pool MAIN=报名顺序前 m 人直通车；QUALIFIER=已写入「资格赛晋级」名单（entry_type=2）的选手
     */
    public Map<String, Object> getDrawConfig(Long tournamentId, DrawPool pool) {
        Map<String, Object> config = new HashMap<>();

        Tournament tournament = tournamentService.getById(tournamentId);
        config.put("tournament", tournament);

        TournamentCompetitionConfig competitionConfig = competitionConfigMapper.selectById(tournamentId);
        config.put("competitionConfig", competitionConfig);

        TournamentRegistrationSetting regSetting = registrationService.getSetting(tournamentId);
        config.put("registrationSetting", regSetting);

        List<TournamentRegistrationRowDto> rows = registrationService.listRows(tournamentId, LocalDateTime.now());
        List<TournamentRegistrationRowDto> eligible = rows.stream()
                .filter(TournamentRegistrationRowDto::isEffectiveApproved)
                .collect(Collectors.toList());

        int mode = regSetting != null && regSetting.getMode() != null ? regSetting.getMode() : 0;
        List<Long> drawParticipants = new ArrayList<>();

        if (pool == DrawPool.QUALIFIER) {
            if (mode != 1) {
                config.put("eligiblePlayers", eligible);
                config.put("drawParticipants", List.of());
                config.put("totalDrawPlayers", 0);
                config.put("drawPool", pool.name());
                putRecommendedCounts(config, competitionConfig);
                return config;
            }
            Map<Long, Integer> regOrder = new HashMap<>();
            int ord = 0;
            for (TournamentRegistrationRowDto r : eligible) {
                if (r.getUserId() != null && !regOrder.containsKey(r.getUserId())) {
                    regOrder.put(r.getUserId(), ord++);
                }
            }
            List<TournamentEntry> qEntries = tournamentEntryService.lambdaQuery()
                    .eq(TournamentEntry::getTournamentId, tournamentId)
                    .eq(TournamentEntry::getEntryType, 2)
                    .list();
            drawParticipants = qEntries.stream()
                    .map(TournamentEntry::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparingInt(u -> regOrder.getOrDefault(u, Integer.MAX_VALUE)))
                    .collect(Collectors.toList());
        } else {
            if (mode == 0) {
                int n = regSetting != null && regSetting.getQuotaN() != null ? regSetting.getQuotaN() : 32;
                drawParticipants = eligible.stream()
                        .limit(n)
                        .map(TournamentRegistrationRowDto::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            } else {
                int m = regSetting != null && regSetting.getMainDirectM() != null ? regSetting.getMainDirectM() : 0;
                drawParticipants = eligible.stream()
                        .limit(m)
                        .map(TournamentRegistrationRowDto::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        }

        config.put("eligiblePlayers", eligible);
        config.put("drawParticipants", drawParticipants);
        config.put("totalDrawPlayers", drawParticipants.size());
        config.put("drawPool", pool.name());

        int qp = qualifierSlotsPerGroup(tournamentId);
        int gs = competitionConfig != null && competitionConfig.getGroupSize() != null ? competitionConfig.getGroupSize() : 0;
        config.put("qualifierSlotsPerGroup", qp);
        config.put("mainSlotsPerGroup", gs > 0 ? Math.max(0, gs - qp) : 0);

        putRecommendedCounts(config, competitionConfig);
        return config;
    }

    private static void putRecommendedCounts(Map<String, Object> config, TournamentCompetitionConfig competitionConfig) {
        if (competitionConfig != null && competitionConfig.getParticipantCount() != null) {
            config.put("recommendedTotalPlayers", competitionConfig.getParticipantCount());
            if (competitionConfig.getGroupSize() != null) {
                int groupCount = competitionConfig.getParticipantCount() / competitionConfig.getGroupSize();
                config.put("recommendedGroupCount", groupCount);
                config.put("recommendedGroupSize", competitionConfig.getGroupSize());
            }
        }
    }

    /**
     * 兼容旧调用：种子数由类型决定（RANDOM/TIERED 传 null）。
     */
    @Transactional
    public TournamentDraw initializeDraw(Long tournamentId, String drawType, Integer groupCount, Integer tierCount) {
        return initializeDraw(tournamentId, drawType, groupCount, tierCount, null, DrawPool.MAIN);
    }

    /**
     * 分档抽签可选档数：须整除每组人数（≥2，且 ≤ 每组人数）。与小组数无关。
     *
     * @param groupCount      当前小组数（仅用于与调用方上下文一致；选项计算不依赖此值）
     * @param playersPerGroup 每组人数
     */
    public List<Integer> getValidTieredDrawTierCounts(int groupCount, int playersPerGroup) {
        if (groupCount < 1 || playersPerGroup <= 1) {
            return List.of();
        }
        return getValidTierCounts(playersPerGroup);
    }

    /**
     * 种子抽签：种子人数须为小组数的正整数倍、小于参赛人数，且每组种子位数 &lt; 每组人数。
     */
    public List<Integer> getValidSeedCountOptions(int groupCount, int playersPerGroup, int totalPlayers) {
        List<Integer> out = new ArrayList<>();
        if (groupCount < 1 || playersPerGroup <= 1 || totalPlayers <= 1) {
            return out;
        }
        int maxSeeds = Math.min(totalPlayers - 1, (playersPerGroup - 1) * groupCount);
        for (int k = groupCount; k <= maxSeeds; k += groupCount) {
            if (k % groupCount != 0) {
                continue;
            }
            int seedsPerGroup = k / groupCount;
            if (seedsPerGroup >= playersPerGroup) {
                continue;
            }
            out.add(k);
        }
        return out;
    }

    /**
     * 当前用户尚可抽签时，可选的小组 ID（未满且符合分档/种子规则）。
     */
    public List<Long> getEligibleGroupIds(Long tournamentId, Long userId) {
        return getEligibleGroupIds(tournamentId, userId, DrawPool.MAIN);
    }

    public List<Long> getEligibleGroupIds(Long tournamentId, Long userId, DrawPool pool) {
        if (userId == null) {
            return List.of();
        }
        TournamentDraw draw = loadDraw(tournamentId, pool);
        if (draw == null) {
            return List.of();
        }
        Map<String, Object> config = getDrawConfig(tournamentId, pool);
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) config.get("drawParticipants");
        if (drawParticipants == null || !drawParticipants.contains(userId)) {
            return List.of();
        }
        int totalPlayers = drawParticipants.size();
        if (draw.getGroupCount() == null || draw.getGroupCount() < 1 || totalPlayers % draw.getGroupCount() != 0) {
            return List.of();
        }
        int playersPerGroup = totalPlayers / draw.getGroupCount();

        List<TournamentGroup> groups = groupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tournamentId)
                        .orderByAsc(TournamentGroup::getGroupOrder));

        List<Long> eligible = new ArrayList<>();
        for (TournamentGroup g : groups) {
            long currentCount = drawResultMapper.selectCount(
                    Wrappers.<TournamentDrawResult>lambdaQuery()
                            .eq(TournamentDrawResult::getTournamentId, tournamentId)
                            .eq(TournamentDrawResult::getDrawPool, pool.name())
                            .eq(TournamentDrawResult::getGroupId, g.getId()));
            if (currentCount >= playersPerGroup) {
                continue;
            }

            String dt = draw.getDrawType();
            if ("RANDOM".equals(dt)) {
                eligible.add(g.getId());
            } else if ("TIERED".equals(dt)) {
                Integer tierNumber = calculateTierNumber(userId, tournamentId, draw, pool);
                int tc = draw.getTierCount() != null ? draw.getTierCount() : 1;
                long tierInGroup = drawResultMapper.selectCount(
                        Wrappers.<TournamentDrawResult>lambdaQuery()
                                .eq(TournamentDrawResult::getTournamentId, tournamentId)
                                .eq(TournamentDrawResult::getDrawPool, pool.name())
                                .eq(TournamentDrawResult::getGroupId, g.getId())
                                .eq(TournamentDrawResult::getTierNumber, tierNumber));
                int playersPerTier = tc > 0 ? playersPerGroup / tc : playersPerGroup;
                if (tierInGroup < playersPerTier) {
                    eligible.add(g.getId());
                }
            } else if ("SEED".equals(dt)) {
                int sc = draw.getSeedCount() != null ? draw.getSeedCount() : 0;
                int gc = draw.getGroupCount() != null ? draw.getGroupCount() : 1;
                int seedsPerGroup = sc / gc;
                int nonSeedCap = playersPerGroup - seedsPerGroup;
                int pos = drawParticipants.indexOf(userId);
                boolean userIsSeed = pos >= 0 && pos < sc;
                long seedsInGroup = drawResultMapper.selectCount(
                        Wrappers.<TournamentDrawResult>lambdaQuery()
                                .eq(TournamentDrawResult::getTournamentId, tournamentId)
                                .eq(TournamentDrawResult::getDrawPool, pool.name())
                                .eq(TournamentDrawResult::getGroupId, g.getId())
                                .eq(TournamentDrawResult::getIsSeed, true));
                long nonSeedsInGroup = drawResultMapper.selectCount(
                        Wrappers.<TournamentDrawResult>lambdaQuery()
                                .eq(TournamentDrawResult::getTournamentId, tournamentId)
                                .eq(TournamentDrawResult::getDrawPool, pool.name())
                                .eq(TournamentDrawResult::getGroupId, g.getId())
                                .eq(TournamentDrawResult::getIsSeed, false));
                if (userIsSeed) {
                    if (seedsInGroup < seedsPerGroup) {
                        eligible.add(g.getId());
                    }
                } else {
                    if (nonSeedsInGroup < nonSeedCap) {
                        eligible.add(g.getId());
                    }
                }
            }
        }
        return eligible;
    }

    /**
     * 初始化抽签配置（自动从报名名单和赛事配置导入）。
     * 允许赛事状态为筹备中或进行中，且尚未保存小组名单（无 tournament_group_member）。
     * seedCount 仅当 drawType=SEED 时必填，且须为小组数的正整数倍并小于参赛人数。
     */
    @Transactional
    public TournamentDraw initializeDraw(Long tournamentId, String drawType, Integer groupCount, Integer tierCount, Integer seedCount) {
        return initializeDraw(tournamentId, drawType, groupCount, tierCount, seedCount, DrawPool.MAIN);
    }

    @Transactional
    public TournamentDraw initializeDraw(Long tournamentId, String drawType, Integer groupCount, Integer tierCount, Integer seedCount, DrawPool pool) {
        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            throw new IllegalStateException("赛事不存在");
        }
        Integer st = tournament.getStatus();
        if (st == null || (st != 0 && st != 1)) {
            throw new IllegalStateException("仅在筹备中或进行中的赛事可配置抽签");
        }
        if (pool == DrawPool.MAIN) {
            if (countGroupMembers(tournamentId) > 0) {
                throw new IllegalStateException("已保存小组名单后不可再初始化直通车抽签，请先清空小组成员");
            }
        } else {
            if (countGroupMembers(tournamentId) <= 0) {
                throw new IllegalStateException("请先导入直通车小组名单后再初始化资格赛抽签");
            }
            if (!"RANDOM".equals(drawType)) {
                throw new IllegalStateException("资格赛抽签暂仅支持默认随机抽签");
            }
        }

        Map<String, Object> cfg = getDrawConfig(tournamentId, pool);
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) cfg.get("drawParticipants");
        int totalPlayers = drawParticipants != null ? drawParticipants.size() : 0;
        if (totalPlayers <= 0) {
            throw new IllegalStateException("暂无符合规则的名单，无法初始化抽签");
        }
        if (groupCount == null || groupCount < 1 || totalPlayers % groupCount != 0) {
            throw new IllegalStateException("小组数无效或不能整除参赛人数");
        }
        int playersPerGroup = totalPlayers / groupCount;

        if ("TIERED".equals(drawType)) {
            if (tierCount == null || tierCount < 2 || playersPerGroup % tierCount != 0) {
                throw new IllegalStateException("档数须至少为 2 且须整除每组人数（当前每组 " + playersPerGroup + " 人）");
            }
        } else if ("SEED".equals(drawType)) {
            if (seedCount == null || seedCount <= 0 || seedCount >= totalPlayers) {
                throw new IllegalStateException("种子数须大于 0 且小于参赛人数");
            }
            if (seedCount % groupCount != 0) {
                throw new IllegalStateException("种子数须为小组数的整数倍");
            }
            int spg = seedCount / groupCount;
            if (spg >= playersPerGroup) {
                throw new IllegalStateException("每组种子位数须小于每组人数");
            }
        } else if (!"RANDOM".equals(drawType)) {
            throw new IllegalStateException("不支持的抽签类型");
        }

        drawResultMapper.delete(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name()));

        TournamentRegistrationSetting reg = registrationService.getSetting(tournamentId);
        LocalDateTime deadline = reg != null ? reg.getDeadline() : null;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime openedAt;
        if (deadline != null && now.isBefore(deadline)) {
            openedAt = null;
        } else if (deadline != null) {
            openedAt = deadline;
        } else {
            openedAt = now;
        }

        TournamentDraw existing = loadDraw(tournamentId, pool);

        if (existing != null) {
            existing.setDrawType(drawType);
            existing.setGroupCount(groupCount);
            existing.setTierCount("TIERED".equals(drawType) ? tierCount : null);
            existing.setSeedCount("SEED".equals(drawType) ? seedCount : null);
            existing.setStatus("PENDING");
            existing.setDrawOpenedAt(openedAt);
            existing.setUpdatedAt(now);
            drawMapper.updateById(existing);
            if (openedAt != null) {
                notifyDrawOpened(tournamentId, pool);
            }
            return existing;
        }

        TournamentDraw draw = new TournamentDraw();
        draw.setTournamentId(tournamentId);
        draw.setDrawPool(pool.name());
        draw.setDrawType(drawType);
        draw.setGroupCount(groupCount);
        draw.setTierCount("TIERED".equals(drawType) ? tierCount : null);
        draw.setSeedCount("SEED".equals(drawType) ? seedCount : null);
        draw.setStatus("PENDING");
        draw.setDrawOpenedAt(openedAt);
        draw.setCreatedAt(now);
        draw.setUpdatedAt(now);
        drawMapper.insert(draw);
        if (openedAt != null) {
            notifyDrawOpened(tournamentId, pool);
        }
        return draw;
    }

    /**
     * 执行用户抽签
     */
    @Transactional
    public TournamentDrawResult performDraw(Long tournamentId, Long userId, Long groupId) {
        return performDraw(tournamentId, userId, groupId, DrawPool.MAIN);
    }

    @Transactional
    public TournamentDrawResult performDraw(Long tournamentId, Long userId, Long groupId, DrawPool pool) {
        LocalDateTime now = LocalDateTime.now();
        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null || tournament.getStatus() == null || (tournament.getStatus() != 0 && tournament.getStatus() != 1)) {
            throw new RuntimeException("仅在筹备中或进行中的赛事可抽签");
        }

        TournamentDraw draw = loadDraw(tournamentId, pool);
        if (draw == null) {
            throw new RuntimeException("抽签配置不存在");
        }
        if (!isDrawWindowOpen(draw, now)) {
            throw new RuntimeException("抽签尚未开放（报名截止后自动开放，请稍候）");
        }

        TournamentDrawResult existing = drawResultMapper.selectOne(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name())
                        .eq(TournamentDrawResult::getUserId, userId));

        if (existing != null) {
            throw new RuntimeException("该选手已完成抽签");
        }

        Map<String, Object> config = getDrawConfig(tournamentId, pool);
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) config.get("drawParticipants");

        if (drawParticipants == null || !drawParticipants.contains(userId)) {
            throw new RuntimeException("该选手不在抽签名单中");
        }

        int totalPlayers = drawParticipants.size();
        int playersPerGroup = totalPlayers / draw.getGroupCount();

        if ("RANDOM".equals(draw.getDrawType())) {
            List<TournamentGroup> randomGroups = groupMapper.selectList(
                    Wrappers.<TournamentGroup>lambdaQuery()
                            .eq(TournamentGroup::getTournamentId, tournamentId)
                            .orderByAsc(TournamentGroup::getGroupOrder));
            groupId = pickRandomOpenGroupId(tournamentId, pool, playersPerGroup, randomGroups);
        } else {
            if (groupId == null) {
                throw new RuntimeException("请选择小组");
            }
        }

        return insertDrawResultRow(tournamentId, pool, userId, groupId, now, false);
    }

    /**
     * 写入一条抽签结果（选手点签与「导入前仅一组未满」自动补录共用）。
     */
    private TournamentDrawResult insertDrawResultRow(Long tournamentId, DrawPool pool, Long userId, Long groupId,
                                                     LocalDateTime now, boolean autoAssigned) {
        TournamentDraw draw = loadDraw(tournamentId, pool);
        if (draw == null) {
            throw new RuntimeException("抽签配置不存在");
        }
        Map<String, Object> config = getDrawConfig(tournamentId, pool);
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) config.get("drawParticipants");
        if (drawParticipants == null || !drawParticipants.contains(userId)) {
            throw new RuntimeException("该选手不在抽签名单中");
        }
        TournamentDrawResult existing = drawResultMapper.selectOne(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name())
                        .eq(TournamentDrawResult::getUserId, userId));
        if (existing != null) {
            throw new RuntimeException("该选手已完成抽签");
        }
        int totalPlayers = drawParticipants.size();
        Integer gcObj = draw.getGroupCount();
        if (gcObj == null || gcObj < 1 || totalPlayers % gcObj != 0) {
            throw new RuntimeException("抽签人数与小组数不匹配，无法落位");
        }
        int playersPerGroup = totalPlayers / gcObj;

        long currentCount = drawResultMapper.selectCount(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name())
                        .eq(TournamentDrawResult::getGroupId, groupId));

        if (currentCount >= playersPerGroup) {
            throw new RuntimeException("该小组已满");
        }

        int mainSlotOffset = mainSlotOffsetForDraw(tournamentId, pool);

        Integer tierNumber = null;
        Boolean isSeed = null;
        Integer groupSlotIndex = null;

        if ("TIERED".equals(draw.getDrawType())) {
            tierNumber = calculateTierNumber(userId, tournamentId, draw, pool);
            long tierCount = drawResultMapper.selectCount(
                    Wrappers.<TournamentDrawResult>lambdaQuery()
                            .eq(TournamentDrawResult::getTournamentId, tournamentId)
                            .eq(TournamentDrawResult::getDrawPool, pool.name())
                            .eq(TournamentDrawResult::getGroupId, groupId)
                            .eq(TournamentDrawResult::getTierNumber, tierNumber));
            int playersPerTier = playersPerGroup / draw.getTierCount();
            if (tierCount >= playersPerTier) {
                throw new RuntimeException("该小组的第" + tierNumber + "档位已满");
            }
        } else if ("SEED".equals(draw.getDrawType())) {
            int sc = draw.getSeedCount() != null ? draw.getSeedCount() : 0;
            int gc = draw.getGroupCount() != null ? draw.getGroupCount() : 1;
            int seedsPerGroup = sc / gc;
            int pos = drawParticipants.indexOf(userId);
            boolean userIsSeed = pos >= 0 && pos < sc;
            isSeed = userIsSeed;
            long seedsInGroup = drawResultMapper.selectCount(
                    Wrappers.<TournamentDrawResult>lambdaQuery()
                            .eq(TournamentDrawResult::getTournamentId, tournamentId)
                            .eq(TournamentDrawResult::getDrawPool, pool.name())
                            .eq(TournamentDrawResult::getGroupId, groupId)
                            .eq(TournamentDrawResult::getIsSeed, true));
            long nonSeedsInGroup = drawResultMapper.selectCount(
                    Wrappers.<TournamentDrawResult>lambdaQuery()
                            .eq(TournamentDrawResult::getTournamentId, tournamentId)
                            .eq(TournamentDrawResult::getDrawPool, pool.name())
                            .eq(TournamentDrawResult::getGroupId, groupId)
                            .eq(TournamentDrawResult::getIsSeed, false));
            if (userIsSeed) {
                if (seedsInGroup >= seedsPerGroup) {
                    throw new RuntimeException("该小组种子位已满");
                }
                groupSlotIndex = (int) seedsInGroup + 1;
            } else {
                int nonSeedCap = playersPerGroup - seedsPerGroup;
                if (nonSeedsInGroup >= nonSeedCap) {
                    throw new RuntimeException("该小组非种子位已满");
                }
                groupSlotIndex = seedsPerGroup + (int) nonSeedsInGroup + 1;
            }
            if (mainSlotOffset > 0 && groupSlotIndex != null) {
                groupSlotIndex = mainSlotOffset + groupSlotIndex;
            }
        } else {
            long inG = drawResultMapper.selectCount(
                    Wrappers.<TournamentDrawResult>lambdaQuery()
                            .eq(TournamentDrawResult::getTournamentId, tournamentId)
                            .eq(TournamentDrawResult::getDrawPool, pool.name())
                            .eq(TournamentDrawResult::getGroupId, groupId));
            groupSlotIndex = pool == DrawPool.QUALIFIER
                    ? (int) inG + 1
                    : mainSlotOffset + (int) inG + 1;
        }

        Integer maxOrder = drawResultMapper.selectList(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name())
        ).stream().map(TournamentDrawResult::getDrawOrder).filter(Objects::nonNull).max(Integer::compareTo).orElse(0);

        TournamentDrawResult result = new TournamentDrawResult();
        result.setTournamentId(tournamentId);
        result.setDrawPool(pool.name());
        result.setUserId(userId);
        result.setGroupId(groupId);
        result.setTierNumber(tierNumber);
        result.setIsSeed(isSeed);
        result.setGroupSlotIndex(groupSlotIndex);
        result.setDrawOrder(maxOrder + 1);
        result.setIsAutoAssigned(autoAssigned);
        result.setCreatedAt(now);
        drawResultMapper.insert(result);

        if ("PENDING".equals(draw.getStatus())) {
            draw.setStatus("IN_PROGRESS");
            draw.setUpdatedAt(now);
            drawMapper.updateById(draw);
        }

        TournamentDraw fresh = loadDraw(tournamentId, pool);
        if (fresh != null) {
            checkAndCompleteDraw(tournamentId, fresh, totalPlayers);
        }

        return result;
    }

    /**
     * 导入小组前：若仅有 1 个组未满，且未抽签者均在抽签名单中、人数恰好等于该组空位数，则自动写入抽签结果（标记为自动分配）。
     */
    private void autoFillUndrawnIntoSingleOpenGroupIfApplicable(Long tournamentId, DrawPool pool, TournamentDraw draw,
                                                              List<Long> drawParticipants, int expected,
                                                              List<TournamentDrawResult> drawResults) {
        if (draw == null || drawParticipants == null || drawParticipants.isEmpty() || expected <= 0) {
            return;
        }
        if (drawResults.size() >= expected) {
            return;
        }
        Integer gc = draw.getGroupCount();
        if (gc == null || gc < 1 || expected % gc != 0) {
            return;
        }
        if (drawParticipants.size() != expected) {
            return;
        }
        int playersPerGroup = expected / gc;
        List<TournamentGroup> groups = groupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tournamentId)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, Long> countByGroup = drawResults.stream()
                .collect(Collectors.groupingBy(TournamentDrawResult::getGroupId, Collectors.counting()));
        List<Long> underfilled = new ArrayList<>();
        for (TournamentGroup g : groups) {
            long c = countByGroup.getOrDefault(g.getId(), 0L);
            if (c < playersPerGroup) {
                underfilled.add(g.getId());
            }
        }
        if (underfilled.size() != 1) {
            return;
        }
        Long targetGroupId = underfilled.get(0);
        int slots = playersPerGroup - countByGroup.getOrDefault(targetGroupId, 0L).intValue();
        if (slots <= 0) {
            return;
        }
        Set<Long> drawn = drawResults.stream().map(TournamentDrawResult::getUserId).collect(Collectors.toSet());
        List<Long> undrawnOrdered = new ArrayList<>();
        for (Long uid : drawParticipants) {
            if (!drawn.contains(uid)) {
                undrawnOrdered.add(uid);
            }
        }
        if (undrawnOrdered.size() != slots) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long uid : undrawnOrdered) {
            insertDrawResultRow(tournamentId, pool, uid, targetGroupId, now, true);
        }
    }

    /** 默认随机抽签：在未满的小组中随机选一个组（选手端不选具体小组）。 */
    private Long pickRandomOpenGroupId(Long tournamentId, DrawPool pool, int playersPerGroup,
                                       List<TournamentGroup> groups) {
        List<Long> open = new ArrayList<>();
        for (TournamentGroup g : groups) {
            long currentCount = drawResultMapper.selectCount(
                    Wrappers.<TournamentDrawResult>lambdaQuery()
                            .eq(TournamentDrawResult::getTournamentId, tournamentId)
                            .eq(TournamentDrawResult::getDrawPool, pool.name())
                            .eq(TournamentDrawResult::getGroupId, g.getId()));
            if (currentCount < playersPerGroup) {
                open.add(g.getId());
            }
        }
        if (open.isEmpty()) {
            throw new RuntimeException("没有仍有空位的小组");
        }
        Collections.shuffle(open, ThreadLocalRandom.current());
        return open.get(0);
    }

    /** 直通车抽签在正赛+资格赛布局下，组内位次从「资格赛席」之后开始编号 */
    private int mainSlotOffsetForDraw(Long tournamentId, DrawPool pool) {
        if (pool != DrawPool.MAIN) {
            return 0;
        }
        return qualifierSlotsPerGroup(tournamentId);
    }

    /**
     * 计算选手档位：按报名接龙最终顺序将全体选手均分为 tierCount 档（第 1 档最先抽签）。
     */
    /** 分档抽签：当前用户在第几档（仅展示用） */
    public Integer getParticipantTierNumber(Long tournamentId, Long userId) {
        TournamentDraw draw = loadDraw(tournamentId, DrawPool.MAIN);
        if (draw == null || !"TIERED".equals(draw.getDrawType())) {
            return null;
        }
        return calculateTierNumber(userId, tournamentId, draw, DrawPool.MAIN);
    }

    /** 种子抽签：当前用户是否为种子（按报名顺序前 seedCount 人） */
    public boolean isUserSeedParticipant(Long tournamentId, Long userId) {
        TournamentDraw draw = loadDraw(tournamentId, DrawPool.MAIN);
        if (draw == null || !"SEED".equals(draw.getDrawType()) || userId == null) {
            return false;
        }
        int sc = draw.getSeedCount() != null ? draw.getSeedCount() : 0;
        Map<String, Object> config = getDrawConfig(tournamentId, DrawPool.MAIN);
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) config.get("drawParticipants");
        if (drawParticipants == null) {
            return false;
        }
        int pos = drawParticipants.indexOf(userId);
        return pos >= 0 && pos < sc;
    }

    private Integer calculateTierNumber(Long userId, Long tournamentId, TournamentDraw draw, DrawPool pool) {
        Map<String, Object> config = getDrawConfig(tournamentId, pool);
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) config.get("drawParticipants");

        if (drawParticipants == null || drawParticipants.isEmpty()) {
            return 1;
        }
        int position = drawParticipants.indexOf(userId);
        if (position < 0) {
            return 1;
        }
        int tierCount = draw.getTierCount() != null ? draw.getTierCount() : 1;
        int totalPlayers = drawParticipants.size();
        if (tierCount <= 1 || totalPlayers <= 0) {
            return 1;
        }
        int bucketSize = totalPlayers / tierCount;
        if (bucketSize <= 0) {
            return 1;
        }
        int tier = position / bucketSize + 1;
        return Math.min(tierCount, tier);
    }

    /**
     * 获取抽签状态
     */
    public Map<String, Object> getDrawStatus(Long tournamentId) {
        return getDrawStatus(tournamentId, DrawPool.MAIN);
    }

    public Map<String, Object> getDrawStatus(Long tournamentId, DrawPool pool) {
        TournamentDraw draw = loadDraw(tournamentId, pool);

        Map<String, Object> config = getDrawConfig(tournamentId, pool);
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) config.get("drawParticipants");
        int totalPlayers = drawParticipants != null ? drawParticipants.size() : 0;

        List<TournamentDrawResult> results = drawResultMapper.selectList(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                .eq(TournamentDrawResult::getTournamentId, tournamentId)
                .eq(TournamentDrawResult::getDrawPool, pool.name())
        );

        List<TournamentGroup> nameGroups = groupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tournamentId));
        Map<Long, String> groupIdToName = nameGroups.stream()
                .collect(Collectors.toMap(
                        TournamentGroup::getId,
                        g -> g.getGroupName() != null && !g.getGroupName().isBlank()
                                ? g.getGroupName()
                                : ("组#" + g.getId()),
                        (a, b) -> a));

        Map<String, Object> status = new HashMap<>();
        status.put("draw", draw);
        status.put("totalPlayers", totalPlayers);
        status.put("drawnPlayers", results.size());
        status.put("hasDrawn", results.stream().anyMatch(r -> Boolean.FALSE.equals(r.getIsAutoAssigned())));
        status.put("results", results);
        Map<Long, String> uname = userService.list().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        List<Map<String, Object>> resultRows = new ArrayList<>();
        for (TournamentDrawResult r : results) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", r.getUserId());
            row.put("username", uname.getOrDefault(r.getUserId(), "?"));
            row.put("groupId", r.getGroupId());
            row.put("groupName", groupIdToName.getOrDefault(r.getGroupId(), "组#" + r.getGroupId()));
            row.put("tierNumber", r.getTierNumber());
            Integer tn = r.getTierNumber();
            row.put("tierLabel", tn != null ? ("第" + tn + "档") : "-");
            row.put("isSeed", r.getIsSeed());
            row.put("groupSlotIndex", r.getGroupSlotIndex());
            row.put("drawOrder", r.getDrawOrder());
            row.put("isAutoAssigned", r.getIsAutoAssigned());
            resultRows.add(row);
        }
        status.put("resultRows", resultRows);
        status.putAll(config);

        return status;
    }

    private void checkAndCompleteDraw(Long tournamentId, TournamentDraw draw, int totalPlayers) {
        DrawPool pool = draw.getDrawPool() != null ? DrawPool.fromParam(draw.getDrawPool()) : DrawPool.MAIN;
        long cnt = drawResultMapper.selectCount(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name()));
        if (cnt >= totalPlayers && draw.getStatus() != null && !"COMPLETED".equals(draw.getStatus())) {
            draw.setStatus("COMPLETED");
            draw.setUpdatedAt(LocalDateTime.now());
            drawMapper.updateById(draw);
            notifyDrawCompleted(tournamentId, pool);
        }
    }

    public void openPendingDrawsAfterDeadline(LocalDateTime now) {
        List<TournamentDraw> pending = drawMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentDraw>lambdaQuery()
                        .isNull(TournamentDraw::getDrawOpenedAt));
        for (TournamentDraw d : pending) {
            com.example.entity.TournamentRegistrationSetting reg = registrationService.getSetting(d.getTournamentId());
            if (reg == null || reg.getDeadline() == null) {
                continue;
            }
            if (now.isBefore(reg.getDeadline())) {
                continue;
            }
            d.setDrawOpenedAt(reg.getDeadline());
            d.setUpdatedAt(now);
            drawMapper.updateById(d);
            notifyDrawOpened(d.getTournamentId(), DrawPool.fromParam(d.getDrawPool()));
        }
    }

    private List<Long> buildDrawNotificationRecipients(Long tournamentId, DrawPool pool) {
        Map<String, Object> cfg = getDrawConfig(tournamentId, pool);
        @SuppressWarnings("unchecked")
        List<Long> parts = (List<Long>) cfg.get("drawParticipants");
        LinkedHashSet<Long> ids = new LinkedHashSet<>(parts != null ? parts : List.of());
        Tournament t = tournamentService.getById(tournamentId);
        if (t != null && t.getHostUserId() != null) {
            ids.add(t.getHostUserId());
        }
        return new ArrayList<>(ids);
    }

    private void notifyDrawOpened(Long tournamentId, DrawPool pool) {
        Tournament t = tournamentService.getById(tournamentId);
        String label = t != null && t.getLevelCode() != null ? t.getLevelCode() : ("赛事#" + tournamentId);
        String suffix = pool == DrawPool.QUALIFIER ? "?pool=QUALIFIER" : "";
        String md = "抽签已开始，请从下列链接进入抽签页面。\n\n[进入抽签](/tournament/" + tournamentId + "/draw" + suffix + ")";
        notificationService.sendNotificationToUserIds(
                "抽签开始：" + label + (pool == DrawPool.QUALIFIER ? "（资格赛池）" : ""),
                md,
                SRC_DRAW_OPEN,
                tournamentId,
                buildDrawNotificationRecipients(tournamentId, pool));
    }

    private void notifyDrawCompleted(Long tournamentId, DrawPool pool) {
        Tournament t = tournamentService.getById(tournamentId);
        String label = t != null && t.getLevelCode() != null ? t.getLevelCode() : ("赛事#" + tournamentId);
        String suffix = pool == DrawPool.QUALIFIER ? "?pool=QUALIFIER" : "";
        String md = "所有选手已完成抽签。管理员可导入小组名单。\n\n[查看抽签](/tournament/" + tournamentId + "/draw" + suffix + ")";
        notificationService.sendNotificationToUserIds(
                "抽签已完成：" + label + (pool == DrawPool.QUALIFIER ? "（资格赛池）" : ""),
                md,
                SRC_DRAW_COMPLETE,
                tournamentId,
                buildDrawNotificationRecipients(tournamentId, pool));
    }

    /**
     * 已有小组名单时，仅当正赛+资格赛且资格赛抽签/导入流程未全部完成时仍允许进入抽签页。
     */
    public boolean allowDrawPageWhenGroupMembersPresent(Long tournamentId) {
        if (tournamentId == null) {
            return false;
        }
        TournamentRegistrationSetting s = registrationService.getSetting(tournamentId);
        if (s == null || s.getMode() == null || s.getMode() != 1) {
            return false;
        }
        return !isQualifierDrawPipelineComplete(tournamentId);
    }

    /**
     * 资格赛侧已全部落位（抽签完成且晋级选手已导入资格赛席）或无人走资格赛通道。
     */
    public boolean isQualifierDrawPipelineComplete(Long tournamentId) {
        long t2 = tournamentEntryService.lambdaQuery()
                .eq(TournamentEntry::getTournamentId, tournamentId)
                .eq(TournamentEntry::getEntryType, 2)
                .count();
        if (t2 <= 0) {
            return true;
        }
        TournamentDraw dq = loadDraw(tournamentId, DrawPool.QUALIFIER);
        if (dq == null || !"COMPLETED".equals(dq.getStatus())) {
            return false;
        }
        int qp = qualifierSlotsPerGroup(tournamentId);
        if (qp <= 0) {
            return false;
        }
        List<TournamentEntry> entries = tournamentEntryService.lambdaQuery()
                .eq(TournamentEntry::getTournamentId, tournamentId)
                .eq(TournamentEntry::getEntryType, 2)
                .list();
        for (TournamentEntry e : entries) {
            Long uid = e.getUserId();
            if (uid == null) {
                continue;
            }
            List<TournamentGroupMember> mems = groupMemberMapper.selectList(
                    Wrappers.<TournamentGroupMember>lambdaQuery()
                            .eq(TournamentGroupMember::getTournamentId, tournamentId)
                            .eq(TournamentGroupMember::getUserId, uid));
            boolean placed = false;
            for (TournamentGroupMember m : mems) {
                if (m.getSlotIndex() != null && m.getSlotIndex() <= qp) {
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将抽签结果导入 tournament_group_member，并把赛事状态设为进行中。
     */
    @Transactional
    public int importDrawResultsToGroups(Long tournamentId) {
        return importDrawResultsToGroups(tournamentId, DrawPool.MAIN);
    }

    @Transactional
    public int importDrawResultsToGroups(Long tournamentId, DrawPool pool) {
        Map<String, Object> cfg = getDrawConfig(tournamentId, pool);
        int expected = cfg.get("totalDrawPlayers") instanceof Number ? ((Number) cfg.get("totalDrawPlayers")).intValue() : 0;
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) cfg.get("drawParticipants");
        TournamentDraw draw = loadDraw(tournamentId, pool);
        if (draw == null) {
            throw new IllegalStateException("抽签配置不存在");
        }

        List<TournamentDrawResult> drawResults = drawResultMapper.selectList(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name()));

        autoFillUndrawnIntoSingleOpenGroupIfApplicable(tournamentId, pool, draw, drawParticipants, expected, drawResults);

        drawResults = drawResultMapper.selectList(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name()));

        if (drawResults.isEmpty()) {
            throw new IllegalStateException("没有抽签结果可导入");
        }
        if (expected > 0 && drawResults.size() < expected) {
            throw new IllegalStateException("抽签尚未全部完成，无法导入");
        }

        List<TournamentGroup> groups = groupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tournamentId)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, Integer> groupOrder = new HashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            groupOrder.put(groups.get(i).getId(), i);
        }

        drawResults.sort(Comparator
                .comparing((TournamentDrawResult r) -> groupOrder.getOrDefault(r.getGroupId(), 999))
                .thenComparing(r -> r.getGroupSlotIndex() != null ? r.getGroupSlotIndex() : 999)
                .thenComparing(TournamentDrawResult::getDrawOrder, Comparator.nullsLast(Integer::compareTo)));

        int qp = qualifierSlotsPerGroup(tournamentId);

        if (pool == DrawPool.MAIN) {
            if (qp <= 0) {
                groupMemberMapper.delete(
                        Wrappers.<TournamentGroupMember>lambdaQuery()
                                .eq(TournamentGroupMember::getTournamentId, tournamentId));
            } else {
                groupMemberMapper.delete(
                        Wrappers.<TournamentGroupMember>lambdaQuery()
                                .eq(TournamentGroupMember::getTournamentId, tournamentId)
                                .and(w -> w.isNull(TournamentGroupMember::getSlotIndex)
                                        .or()
                                        .gt(TournamentGroupMember::getSlotIndex, qp)));
            }
        } else {
            if (qp <= 0) {
                throw new IllegalStateException("赛事未配置资格赛席位数，无法导入资格赛抽签");
            }
            groupMemberMapper.delete(
                    Wrappers.<TournamentGroupMember>lambdaQuery()
                            .eq(TournamentGroupMember::getTournamentId, tournamentId)
                            .le(TournamentGroupMember::getSlotIndex, qp));
        }

        long existing = groupMemberMapper.selectCount(
                Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tournamentId));
        int seedNo = (int) existing + 1;
        LocalDateTime now = LocalDateTime.now();
        for (TournamentDrawResult dr : drawResults) {
            groupMemberMapper.delete(Wrappers.<TournamentGroupMember>lambdaQuery()
                    .eq(TournamentGroupMember::getTournamentId, tournamentId)
                    .eq(TournamentGroupMember::getUserId, dr.getUserId()));
            TournamentGroupMember member = new TournamentGroupMember();
            member.setTournamentId(tournamentId);
            member.setGroupId(dr.getGroupId());
            member.setUserId(dr.getUserId());
            member.setSlotIndex(dr.getGroupSlotIndex());
            member.setSeedNo(seedNo++);
            member.setCreatedAt(now);
            groupMemberMapper.insert(member);
        }

        Tournament t = tournamentService.getById(tournamentId);
        if (t != null) {
            t.setStatus(1);
            tournamentService.updateById(t);
        }

        return drawResults.size();
    }
}
