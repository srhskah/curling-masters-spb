package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.dto.GroupImportResult;
import com.example.dto.TournamentRegistrationRowDto;
import com.example.entity.*;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.mapper.MatchAcceptanceMapper;
import com.example.mapper.MatchScoreEditLogMapper;
import com.example.mapper.TournamentGroupMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TournamentCompetitionServiceImpl implements ITournamentCompetitionService {

    @Autowired private TournamentCompetitionConfigMapper configMapper;
    @Autowired private TournamentGroupMapper groupMapper;
    @Autowired private TournamentGroupMemberMapper groupMemberMapper;
    @Autowired private TournamentService tournamentService;
    @Autowired private IMatchService matchService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private ITournamentRegistrationService registrationService;
    @Autowired private MatchAcceptanceMapper matchAcceptanceMapper;
    @Autowired private MatchScoreEditLogMapper matchScoreEditLogMapper;
    @Autowired private UserService userService;
    @Autowired private KnockoutBracketService knockoutBracketService;

    private boolean canManage(User u, Long tournamentId) {
        if (u == null) return false;
        if (u.getRole() != null && u.getRole() <= 1) return true;
        Tournament t = tournamentService.getById(tournamentId);
        return t != null && Objects.equals(t.getHostUserId(), u.getId());
    }

    @Override
    public TournamentCompetitionConfig getConfig(Long tournamentId) {
        if (tournamentId == null) return null;
        return configMapper.selectById(tournamentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TournamentCompetitionConfig saveConfig(User operator, TournamentCompetitionConfig form) {
        if (form == null || form.getTournamentId() == null) throw new IllegalArgumentException("缺少赛事ID");
        Long tid = form.getTournamentId();
        Tournament t = tournamentService.getById(tid);
        if (t == null) throw new IllegalStateException("赛事不存在");
        if (t.getStatus() == null || (t.getStatus() != 0 && t.getStatus() != 1)) {
            throw new IllegalStateException("仅筹备中或进行中赛事可使用此配置");
        }
        if (!canManage(operator, tid)) throw new SecurityException("无权操作");

        TournamentCompetitionConfig db = configMapper.selectById(tid);
        LocalDateTime now = LocalDateTime.now();
        if (db == null) {
            db = new TournamentCompetitionConfig();
            db.setTournamentId(tid);
            db.setCreatedAt(now);
        }
        if (form.getParticipantCount() != null) db.setParticipantCount(form.getParticipantCount());
        if (form.getEntryMode() != null) db.setEntryMode(form.getEntryMode());
        if (form.getEntryMode() != null) {
            if (Objects.equals(form.getEntryMode(), 1)) {
                db.setKnockoutQualifyCount(form.getKnockoutQualifyCount());
            } else {
                db.setKnockoutQualifyCount(null);
            }
        }
        if (form.getMatchMode() != null) db.setMatchMode(form.getMatchMode());
        // 保留淘汰赛首轮/资格赛挂载的最近一次选择（即使当前切到小组赛模式）。
        // 真正执行淘汰赛业务时再按 matchMode 判断是否使用这两个字段。
        if (form.getMatchMode() != null) {
            db.setKnockoutStartRound(form.getKnockoutStartRound());
            db.setQualifierRound(form.getQualifierRound());
        }
        if (form.getGroupMode() != null) db.setGroupMode(form.getGroupMode());
        if (form.getGroupSize() != null) db.setGroupSize(form.getGroupSize());
        if (form.getGroupAllowDraw() != null) db.setGroupAllowDraw(form.getGroupAllowDraw());
        if (form.getGroupStageDeadline() != null) db.setGroupStageDeadline(form.getGroupStageDeadline());
        if (form.getGroupStageSets() != null) db.setGroupStageSets(form.getGroupStageSets());
        if (form.getKnockoutStageSets() != null) db.setKnockoutStageSets(form.getKnockoutStageSets());
        if (form.getFinalStageSets() != null) db.setFinalStageSets(form.getFinalStageSets());
        if (form.getManualLocked() != null) db.setManualLocked(form.getManualLocked());
        if (form.getQualifierSets() != null) {
            if (form.getQualifierSets() < 1 || form.getQualifierSets() > 20) {
                throw new IllegalArgumentException("资格赛局数须在 1～20 之间");
            }
            db.setQualifierSets(form.getQualifierSets());
        }
        if (form.getKnockoutBracketMode() != null) {
            int km = form.getKnockoutBracketMode();
            if (km < 0 || km > 2) {
                throw new IllegalArgumentException("淘汰赛对阵模式须为 0～2");
            }
            db.setKnockoutBracketMode(km);
        }
        if (form.getKnockoutAutoFromGroup() != null) {
            db.setKnockoutAutoFromGroup(form.getKnockoutAutoFromGroup());
        }
        validateConfig(db);
        if (Objects.equals(db.getMatchMode(), 3) && db.getParticipantCount() != null && db.getGroupSize() != null && db.getGroupSize() > 0) {
            int groupCount = db.getParticipantCount() / db.getGroupSize();
            if (groupCount > 0) {
                ensureGroups(tid, groupCount);
            }
        }
        db.setUpdatedAt(now);
        if (configMapper.selectById(tid) == null) configMapper.insert(db);
        else configMapper.updateById(db);
        return db;
    }

    private void validateConfig(TournamentCompetitionConfig c) {
        if (c.getParticipantCount() == null || c.getParticipantCount() < 2) throw new IllegalArgumentException("参赛人数至少2");
        if (Objects.equals(c.getEntryMode(), 1)) {
            Integer q = c.getKnockoutQualifyCount();
            if (q == null || q < 1) throw new IllegalArgumentException("正赛+资格赛模式须填写资格赛名额数（1～参赛人数）");
            if (q > c.getParticipantCount()) throw new IllegalArgumentException("资格赛名额数不能大于参赛人数");
            if (c.getQualifierSets() == null) {
                c.setQualifierSets(8);
            }
        }
        if (c.getMatchMode() == null || c.getMatchMode() < 1 || c.getMatchMode() > 3) throw new IllegalArgumentException("赛事模式不合法");
        Set<Integer> allowedRounds = Set.of(16, 8, 4, 2);
        if (!Objects.equals(c.getMatchMode(), 3)) {
            if (c.getKnockoutStartRound() == null || !allowedRounds.contains(c.getKnockoutStartRound())) {
                throw new IllegalArgumentException("请选择淘汰赛首轮类别（1/16、1/8、1/4、半决赛）");
            }
            if (c.getQualifierRound() != null) {
                if (!allowedRounds.contains(c.getQualifierRound())) {
                    throw new IllegalArgumentException("资格赛轮次不合法");
                }
                // 规则：同赛事只能设置一轮资格赛；多轮淘汰时仅首轮允许挂资格赛
                if (!Objects.equals(c.getQualifierRound(), c.getKnockoutStartRound())) {
                    throw new IllegalArgumentException("仅首轮淘汰赛可添加资格赛，请将资格赛轮次设置为首轮");
                }
            }
        }
        if (Objects.equals(c.getMatchMode(), 3)) {
            if (c.getGroupMode() == null || c.getGroupMode() < 1 || c.getGroupMode() > 3) throw new IllegalArgumentException("请选择小组赛模式");
            if (c.getGroupSize() == null || c.getGroupSize() < 2 || c.getParticipantCount() % c.getGroupSize() != 0) throw new IllegalArgumentException("每组人数必须是参赛人数的因数");
            if (Objects.equals(c.getGroupMode(), 2) && c.getGroupSize() % 2 == 0) throw new IllegalArgumentException("单循环主客场要求每组人数为奇数");
            if (c.getGroupAllowDraw() == null) c.setGroupAllowDraw(true);
        }
    }

    @Override
    public List<Integer> calcGroupSizeOptions(Integer participantCount) {
        if (participantCount == null || participantCount < 2) return List.of();
        List<Integer> options = new ArrayList<>();
        for (int i = 2; i <= participantCount; i++) if (participantCount % i == 0) options.add(i);
        return options;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoFillGroupMembersFromRegistration(User operator, Long tournamentId) {
        if (!canManage(operator, tournamentId)) throw new SecurityException("无权操作");
        TournamentCompetitionConfig cfg = requireConfig(tournamentId);
        if (!Objects.equals(cfg.getMatchMode(), 3)) throw new IllegalStateException("仅小组赛可自动分组");
        int m = cfg.getParticipantCount();
        int groupSize = cfg.getGroupSize();
        int groupCount = m / groupSize;
        List<TournamentRegistrationRowDto> rows = registrationService.listRows(tournamentId, LocalDateTime.now());
        List<Long> userIds = rows.stream().filter(TournamentRegistrationRowDto::isEffectiveApproved).map(TournamentRegistrationRowDto::getUserId).filter(Objects::nonNull).distinct().limit(m).toList();
        if (userIds.isEmpty()) throw new IllegalStateException("报名接龙暂无可用名单，请手动录入");
        List<TournamentGroup> groups = ensureGroups(tournamentId, groupCount);
        groupMemberMapper.delete(Wrappers.<TournamentGroupMember>lambdaQuery().eq(TournamentGroupMember::getTournamentId, tournamentId));
        int idx = 0;
        for (Long uid : userIds) {
            TournamentGroupMember gm = new TournamentGroupMember();
            gm.setTournamentId(tournamentId);
            gm.setGroupId(groups.get(idx % groupCount).getId());
            gm.setUserId(uid);
            gm.setSeedNo(idx + 1);
            gm.setCreatedAt(LocalDateTime.now());
            groupMemberMapper.insert(gm);
            idx++;
        }
    }

    private TournamentCompetitionConfig requireConfig(Long tournamentId) {
        TournamentCompetitionConfig c = getConfig(tournamentId);
        if (c == null) throw new IllegalStateException("请先保存赛事运行配置");
        return c;
    }

    private List<TournamentGroup> ensureGroups(Long tournamentId, int groupCount) {
        List<TournamentGroup> existing = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery().eq(TournamentGroup::getTournamentId, tournamentId).orderByAsc(TournamentGroup::getGroupOrder));
        if (existing.size() == groupCount) return existing;
        groupMapper.delete(Wrappers.<TournamentGroup>lambdaQuery().eq(TournamentGroup::getTournamentId, tournamentId));
        List<TournamentGroup> groups = new ArrayList<>();
        for (int i = 0; i < groupCount; i++) {
            TournamentGroup g = new TournamentGroup();
            g.setTournamentId(tournamentId);
            g.setGroupOrder(i + 1);
            g.setGroupName((char) ('A' + i) + "组");
            g.setCreatedAt(LocalDateTime.now());
            groupMapper.insert(g);
            groups.add(g);
        }
        return groups;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGroupMembers(User operator, Long tournamentId, Map<Long, List<Long>> groupUserMap) {
        if (!canManage(operator, tournamentId)) throw new SecurityException("无权操作");
        TournamentCompetitionConfig cfg = requireConfig(tournamentId);
        if (!Objects.equals(cfg.getMatchMode(), 3)) throw new IllegalStateException("仅小组赛可录入分组名单");
        groupMemberMapper.delete(Wrappers.<TournamentGroupMember>lambdaQuery().eq(TournamentGroupMember::getTournamentId, tournamentId));
        Set<Long> unique = new HashSet<>();
        int total = 0;
        for (Map.Entry<Long, List<Long>> e : groupUserMap.entrySet()) {
            for (Long uid : e.getValue() == null ? List.<Long>of() : e.getValue()) {
                if (uid == null || !unique.add(uid)) continue;
                TournamentGroupMember gm = new TournamentGroupMember();
                gm.setTournamentId(tournamentId);
                gm.setGroupId(e.getKey());
                gm.setUserId(uid);
                gm.setCreatedAt(LocalDateTime.now());
                groupMemberMapper.insert(gm);
                total++;
            }
        }
        boolean qualifierEntryMode = cfg.getEntryMode() != null && cfg.getEntryMode() == 1;
        boolean hasRegistrationResult = registrationService.listRows(tournamentId, LocalDateTime.now()).stream()
                .anyMatch(TournamentRegistrationRowDto::isEffectiveApproved);
        if (qualifierEntryMode && hasRegistrationResult) {
            if (total > (cfg.getParticipantCount() == null ? Integer.MAX_VALUE : cfg.getParticipantCount())) {
                throw new IllegalArgumentException("名单人数不能超过参赛人数");
            }
            if (total < 1) {
                throw new IllegalArgumentException("至少需要录入1名选手");
            }
        } else {
            if (cfg.getParticipantCount() != null && total > cfg.getParticipantCount()) {
                throw new IllegalArgumentException("名单人数不能超过参赛人数（当前 " + total + " 人，上限 " + cfg.getParticipantCount() + " 人）");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGroupMembersForGroup(User operator, Long tournamentId, Long groupId, List<Long> memberUserIds) {
        if (!canManage(operator, tournamentId)) throw new SecurityException("无权操作");
        TournamentCompetitionConfig cfg = requireConfig(tournamentId);
        if (!Objects.equals(cfg.getMatchMode(), 3)) throw new IllegalStateException("仅小组赛可录入分组名单");
        TournamentGroup g = groupMapper.selectById(groupId);
        if (g == null || !Objects.equals(g.getTournamentId(), tournamentId)) {
            throw new IllegalArgumentException("分组不存在");
        }
        LinkedHashSet<Long> dedup = new LinkedHashSet<>();
        if (memberUserIds != null) {
            for (Long uid : memberUserIds) {
                if (uid != null) dedup.add(uid);
            }
        }
        int groupSizeCap = cfg.getGroupSize() != null && cfg.getGroupSize() > 0 ? cfg.getGroupSize() : Integer.MAX_VALUE;
        if (dedup.size() > groupSizeCap) {
            throw new IllegalArgumentException("本组人数不能超过每组人数（" + groupSizeCap + "）");
        }
        List<TournamentGroupMember> others = groupMemberMapper.selectList(Wrappers.<TournamentGroupMember>lambdaQuery()
                .eq(TournamentGroupMember::getTournamentId, tournamentId)
                .ne(TournamentGroupMember::getGroupId, groupId));
        Set<Long> otherUserIds = others.stream().map(TournamentGroupMember::getUserId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        for (Long uid : dedup) {
            if (otherUserIds.contains(uid)) {
                User u = userService.getById(uid);
                throw new IllegalArgumentException("选手已在其他小组：" + (u != null ? u.getUsername() : uid));
            }
        }
        Set<Long> allAfter = new HashSet<>(dedup);
        allAfter.addAll(otherUserIds);
        boolean qualifierEntryMode = cfg.getEntryMode() != null && cfg.getEntryMode() == 1;
        boolean hasRegistrationResult = registrationService.listRows(tournamentId, LocalDateTime.now()).stream()
                .anyMatch(TournamentRegistrationRowDto::isEffectiveApproved);
        if (qualifierEntryMode && hasRegistrationResult) {
            if (allAfter.size() > (cfg.getParticipantCount() == null ? Integer.MAX_VALUE : cfg.getParticipantCount())) {
                throw new IllegalArgumentException("名单总人数不能超过参赛人数");
            }
        } else {
            if (cfg.getParticipantCount() != null && allAfter.size() > cfg.getParticipantCount()) {
                throw new IllegalArgumentException("名单总人数不能超过参赛人数");
            }
        }
        groupMemberMapper.delete(Wrappers.<TournamentGroupMember>lambdaQuery()
                .eq(TournamentGroupMember::getTournamentId, tournamentId)
                .eq(TournamentGroupMember::getGroupId, groupId));
        int seed = 1;
        for (Long uid : dedup) {
            TournamentGroupMember gm = new TournamentGroupMember();
            gm.setTournamentId(tournamentId);
            gm.setGroupId(groupId);
            gm.setUserId(uid);
            gm.setSeedNo(seed++);
            gm.setCreatedAt(LocalDateTime.now());
            groupMemberMapper.insert(gm);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAllGroupsRosterAndGenerateMatches(User operator, Long tournamentId, Map<Long, String> groupIdToRawText) {
        if (!canManage(operator, tournamentId)) throw new SecurityException("无权操作");
        TournamentCompetitionConfig cfg = requireConfig(tournamentId);
        if (!Objects.equals(cfg.getMatchMode(), 3)) throw new IllegalStateException("仅小组赛可录入分组名单");
        List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, String> raw = groupIdToRawText != null ? groupIdToRawText : Map.of();
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        int groupSizeCap = cfg.getGroupSize() != null && cfg.getGroupSize() > 0 ? cfg.getGroupSize() : Integer.MAX_VALUE;
        for (TournamentGroup g : groups) {
            String text = raw.getOrDefault(g.getId(), "");
            GroupImportResult parsed = userService.resolveGroupImport(text);
            unknown.addAll(parsed.getUnknownUsernames());
            List<Long> ids = parsed.getUserIds();
            if (ids.size() > groupSizeCap) {
                throw new IllegalArgumentException(g.getGroupName() + " 人数超过每组人数上限（" + groupSizeCap + "）");
            }
            map.put(g.getId(), ids);
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("以下用户名不存在：" + String.join("、", unknown));
        }
        saveGroupMembers(operator, tournamentId, map);
        generateGroupMatches(operator, tournamentId);
    }

    private static String orderedPairKey(long p1, long p2) {
        return p1 + ":" + p2;
    }

    private static long[] parseOrderedPairKey(String k) {
        String[] p = k.split(":", 2);
        return new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])};
    }

    private boolean matchHasNoEnteredScores(Long matchId) {
        List<SetScore> ss = setScoreService.lambdaQuery().eq(SetScore::getMatchId, matchId).list();
        if (ss.isEmpty()) return true;
        for (SetScore s : ss) {
            if (Boolean.TRUE.equals(s.getPlayer1IsX()) || Boolean.TRUE.equals(s.getPlayer2IsX())) return false;
            int p1 = s.getPlayer1Score() == null ? 0 : s.getPlayer1Score();
            int p2 = s.getPlayer2Score() == null ? 0 : s.getPlayer2Score();
            if (p1 != 0 || p2 != 0) return false;
        }
        return true;
    }

    private Match pickMatchToRemove(List<Match> candidates) {
        if (candidates.size() == 1) return candidates.getFirst();
        Optional<Match> best = candidates.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getResultLocked()) && matchHasNoEnteredScores(m.getId()))
                .findFirst();
        if (best.isPresent()) return best.get();
        best = candidates.stream().filter(m -> matchHasNoEnteredScores(m.getId())).findFirst();
        if (best.isPresent()) return best.get();
        return candidates.get(candidates.size() - 1);
    }

    private void deleteMatchFully(Long matchId) {
        if (matchId == null) return;
        matchAcceptanceMapper.delete(Wrappers.<MatchAcceptance>lambdaQuery().eq(MatchAcceptance::getMatchId, matchId));
        matchScoreEditLogMapper.delete(Wrappers.<MatchScoreEditLog>lambdaQuery().eq(MatchScoreEditLog::getMatchId, matchId));
        setScoreService.lambdaUpdate().eq(SetScore::getMatchId, matchId).remove();
        matchService.removeById(matchId);
    }

    private int nextGlobalGroupRound(Long tournamentId) {
        List<Match> all = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .list();
        return all.stream().mapToInt(m -> m.getRound() == null ? 0 : m.getRound()).max().orElse(0) + 1;
    }

    private int nthOccurrenceDisplayRound(List<long[]> pairs, long p1, long p2, int zeroBasedFilledCount) {
        int seen = 0;
        for (int i = 0; i < pairs.size(); i++) {
            if (pairs.get(i)[0] == p1 && pairs.get(i)[1] == p2) {
                if (seen == zeroBasedFilledCount) return i + 1;
                seen++;
            }
        }
        return pairs.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncGroupMatchesForGroup(User operator, Long tournamentId, Long groupId) {
        if (!canManage(operator, tournamentId)) throw new SecurityException("无权操作");
        TournamentCompetitionConfig cfg = requireConfig(tournamentId);
        if (!Objects.equals(cfg.getMatchMode(), 3)) throw new IllegalStateException("仅小组赛可同步对阵");
        TournamentGroup g = groupMapper.selectById(groupId);
        if (g == null || !Objects.equals(g.getTournamentId(), tournamentId)) {
            throw new IllegalArgumentException("分组不存在");
        }
        List<Long> users = groupMemberMapper.selectList(Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tournamentId)
                        .eq(TournamentGroupMember::getGroupId, groupId)
                        .orderByAsc(TournamentGroupMember::getSeedNo)
                        .orderByAsc(TournamentGroupMember::getId))
                .stream().map(TournamentGroupMember::getUserId).filter(Objects::nonNull).toList();
        List<Match> existingAll = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getGroupId, groupId)
                .eq(Match::getPhaseCode, "GROUP")
                .list();
        if (users.size() < 2) {
            for (Match m : existingAll) deleteMatchFully(m.getId());
            return;
        }
        List<long[]> pairs = buildPairs(users, cfg.getGroupMode());
        Map<String, Integer> needCount = new HashMap<>();
        for (long[] p : pairs) {
            String k = orderedPairKey(p[0], p[1]);
            needCount.merge(k, 1, Integer::sum);
        }
        Map<String, List<Match>> existingByKey = new HashMap<>();
        for (Match m : existingAll) {
            if (m.getPlayer1Id() == null || m.getPlayer2Id() == null) continue;
            String k = orderedPairKey(m.getPlayer1Id(), m.getPlayer2Id());
            existingByKey.computeIfAbsent(k, x -> new ArrayList<>()).add(m);
        }
        Set<String> allKeys = new HashSet<>(needCount.keySet());
        allKeys.addAll(existingByKey.keySet());
        for (String k : allKeys) {
            int need = needCount.getOrDefault(k, 0);
            List<Match> have = new ArrayList<>(existingByKey.getOrDefault(k, List.of()));
            while (have.size() > need) {
                Match victim = pickMatchToRemove(have);
                deleteMatchFully(victim.getId());
                have.remove(victim);
            }
            long[] ab = parseOrderedPairKey(k);
            long p1 = ab[0], p2 = ab[1];
            while (have.size() < need) {
                int displayIdx = nthOccurrenceDisplayRound(pairs, p1, p2, have.size());
                int nextRound = nextGlobalGroupRound(tournamentId);
                Match m = new Match();
                m.setTournamentId(tournamentId);
                m.setCategory(g.getGroupName() + " 第" + Math.max(1, displayIdx) + "场");
                m.setPhaseCode("GROUP");
                m.setGroupId(groupId);
                m.setRound(nextRound);
                m.setPlayer1Id(p1);
                m.setPlayer2Id(p2);
                m.setHomeUserId(p1);
                m.setAwayUserId(p2);
                m.setStatus((byte) 0);
                m.setResultLocked(false);
                m.setCreatedAt(LocalDateTime.now());
                m.setUpdatedAt(LocalDateTime.now());
                matchService.save(m);
                int groupSets = (cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;
                for (int setNo = 1; setNo <= groupSets; setNo++) {
                    SetScore ss = new SetScore();
                    ss.setMatchId(m.getId());
                    ss.setSetNumber(setNo);
                    ss.setPlayer1Score(0);
                    ss.setPlayer2Score(0);
                    ss.setCreatedAt(LocalDateTime.now());
                    setScoreService.save(ss);
                }
                have.add(m);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateGroupMatches(User operator, Long tournamentId) {
        if (!canManage(operator, tournamentId)) throw new SecurityException("无权操作");
        TournamentCompetitionConfig cfg = requireConfig(tournamentId);
        if (!Objects.equals(cfg.getMatchMode(), 3)) throw new IllegalStateException("仅小组赛可自动生成对阵");
        List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery().eq(TournamentGroup::getTournamentId, tournamentId).orderByAsc(TournamentGroup::getGroupOrder));
        matchService.lambdaUpdate().eq(Match::getTournamentId, tournamentId).eq(Match::getPhaseCode, "GROUP").remove();
        int globalRound = 1;
        for (TournamentGroup g : groups) {
            List<Long> users = groupMemberMapper.selectList(Wrappers.<TournamentGroupMember>lambdaQuery().eq(TournamentGroupMember::getTournamentId, tournamentId).eq(TournamentGroupMember::getGroupId, g.getId()).orderByAsc(TournamentGroupMember::getSeedNo).orderByAsc(TournamentGroupMember::getId)).stream().map(TournamentGroupMember::getUserId).toList();
            if (users.size() < 2) continue;
            List<long[]> pairs = buildPairs(users, cfg.getGroupMode());
            int localRound = 1;
            for (long[] p : pairs) {
                Match m = new Match();
                m.setTournamentId(tournamentId);
                m.setCategory(g.getGroupName() + " 第" + localRound + "场");
                m.setPhaseCode("GROUP");
                m.setGroupId(g.getId());
                m.setRound(globalRound++);
                m.setPlayer1Id(p[0]);
                m.setPlayer2Id(p[1]);
                m.setHomeUserId(p[0]);
                m.setAwayUserId(p[1]);
                m.setStatus((byte) 0);
                m.setResultLocked(false);
                m.setCreatedAt(LocalDateTime.now());
                m.setUpdatedAt(LocalDateTime.now());
                matchService.save(m);
                int groupSets = (cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;
                for (int setNo = 1; setNo <= groupSets; setNo++) {
                    SetScore ss = new SetScore();
                    ss.setMatchId(m.getId());
                    ss.setSetNumber(setNo);
                    ss.setPlayer1Score(0);
                    ss.setPlayer2Score(0);
                    ss.setCreatedAt(LocalDateTime.now());
                    setScoreService.save(ss);
                }
                localRound++;
            }
        }
    }

    private List<long[]> buildPairs(List<Long> users, Integer groupMode) {
        List<long[]> base = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) for (int j = i + 1; j < users.size(); j++) base.add(new long[]{users.get(i), users.get(j)});
        if (Objects.equals(groupMode, 1)) return base;
        List<long[]> doubled = new ArrayList<>(base);
        for (long[] p : base) doubled.add(new long[]{p[1], p[0]});
        if (Objects.equals(groupMode, 2)) return doubled;
        if (Objects.equals(groupMode, 3)) { List<long[]> twice = new ArrayList<>(doubled); twice.addAll(doubled); return twice; }
        return base;
    }

    @Override
    public boolean canEditMatchScore(User operator, Match match) {
        if (operator == null || match == null) return false;
        // 验收锁定后：仅超级管理员允许继续修改（其余角色保持只读）
        if (Boolean.TRUE.equals(match.getResultLocked())) {
            return operator.getRole() != null && operator.getRole() == 0;
        }
        boolean selfAccepted = matchAcceptanceMapper.selectCount(Wrappers.<MatchAcceptance>lambdaQuery()
                .eq(MatchAcceptance::getMatchId, match.getId())
                .eq(MatchAcceptance::getUserId, operator.getId())) > 0;
        if (selfAccepted) return false;
        if (operator.getRole() != null && operator.getRole() <= 1) return true;
        Tournament t = tournamentService.getById(match.getTournamentId());
        if (t != null && Objects.equals(t.getHostUserId(), operator.getId())) return true;
        return Objects.equals(match.getPlayer1Id(), operator.getId()) || Objects.equals(match.getPlayer2Id(), operator.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatchScore(User operator, Long matchId, Integer firstEndHammer, List<String> player1Scores, List<String> player2Scores,
                               Boolean autoAccept, String signature) {
        Match match = matchService.getById(matchId);
        if (match == null) throw new IllegalStateException("比赛不存在");
        if (!canEditMatchScore(operator, match)) throw new SecurityException("无权修改比分");
        boolean wasLocked = Boolean.TRUE.equals(match.getResultLocked());
        boolean wasLockedBySuperAdmin = wasLocked
                && operator != null
                && operator.getRole() != null
                && operator.getRole() == 0;
        List<String> p1 = player1Scores == null ? List.of() : player1Scores;
        List<String> p2 = player2Scores == null ? List.of() : player2Scores;
        int n = Math.max(p1.size(), p2.size());
        if (n < 1) throw new IllegalArgumentException("至少录入1局比分");
        TournamentCompetitionConfig cfg = getConfig(match.getTournamentId());
        int configuredGroupSets = (cfg != null && cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;
        boolean groupDrawAllowed = !Objects.equals(match.getPhaseCode(), "GROUP")
                || cfg == null
                || !Boolean.FALSE.equals(cfg.getGroupAllowDraw());
        boolean groupDeadlinePassed = Objects.equals(match.getPhaseCode(), "GROUP")
                && cfg != null
                && cfg.getGroupStageDeadline() != null
                && LocalDateTime.now().isAfter(cfg.getGroupStageDeadline());
        if (groupDeadlinePassed && !isStaff(operator, match.getTournamentId())) {
            throw new SecurityException("小组赛截止后仅办赛人员可录入比分");
        }
        int xCutoff = detectXCutoff(p1, p2);
        boolean hasX = xCutoff >= 0;
        if (hasX && !Boolean.TRUE.equals(autoAccept)) {
            throw new IllegalArgumentException("存在X比分时，需确认“是否验收”后才能保存");
        }
        if (hasX && requiresSignature(match) && (signature == null || signature.trim().isEmpty())) {
            throw new IllegalArgumentException("存在X比分时，需填写电子签名并验收");
        }
        List<SetScore> oldScores = setScoreService.lambdaQuery()
                .eq(SetScore::getMatchId, matchId)
                .orderByAsc(SetScore::getSetNumber)
                .list();
        Map<Integer, SetScore> oldBySetNo = new HashMap<>();
        for (SetScore old : oldScores) oldBySetNo.put(old.getSetNumber(), old);
        setScoreService.lambdaUpdate().eq(SetScore::getMatchId, matchId).remove();
        int t1 = 0, t2 = 0;
        Integer currentHammerSide = normalizeHammerSide(firstEndHammer);

        // 超级管理员：即使已验收锁定，也允许修改并“撤回”上次验收
        if (wasLockedBySuperAdmin) {
            match.setResultLocked(false);
            match.setAcceptedByUserId(null);
            match.setAcceptedAt(null);
            // 撤回该超级管理员在该场比赛的验收记录
            matchAcceptanceMapper.delete(Wrappers.<MatchAcceptance>lambdaQuery()
                    .eq(MatchAcceptance::getMatchId, matchId)
                    .eq(MatchAcceptance::getUserId, operator.getId()));
        }

        for (int i = 0; i < n; i++) {
            ScoreToken tkn1 = parseScoreToken(i < p1.size() ? p1.get(i) : null);
            ScoreToken tkn2 = parseScoreToken(i < p2.size() ? p2.get(i) : null);
            boolean isXEnd = hasX && i == xCutoff;
            boolean forceZeroAfterX = hasX && i > xCutoff;
            // 规则：一旦录入X，X端及之后局都视为“未得分=0”，且不再继续推进先后手计算
            boolean forceZero = hasX && i >= xCutoff;
            int s1 = forceZero ? 0 : tkn1.score();
            int s2 = forceZero ? 0 : tkn2.score();
            // 仅在“首个X”那一局标记X；其余后续局显示为0
            boolean x1 = isXEnd;
            boolean x2 = isXEnd;
            SetScore ss = new SetScore();
            ss.setMatchId(matchId);
            ss.setSetNumber(i + 1);
            ss.setPlayer1Score(s1);
            ss.setPlayer2Score(s2);
            ss.setPlayer1IsX(x1);
            ss.setPlayer2IsX(x2);
            ss.setHammerPlayerId(resolveHammerPlayerId(match, currentHammerSide));
            ss.setIsBlankEnd((byte) ((s1 == 0 && s2 == 0) ? 1 : 0));
            ss.setCreatedAt(LocalDateTime.now());
            setScoreService.save(ss);
            writeEditLog(operator, matchId, i + 1, oldBySetNo.get(i + 1), ss);
            t1 += s1;
            t2 += s2;
            if (!forceZeroAfterX) {
                // X端之后不再继续推进（避免“录X后先后手/局分丢失”的展示错乱）
                currentHammerSide = calcNextHammerSide(currentHammerSide, s1, s2);
            }
        }
        // 小组赛禁平局：常规局数打平时自动追加1局（加局），且先后手顺延交换（含0-0也交换）。
        if (!hasX && Objects.equals(match.getPhaseCode(), "GROUP") && !groupDrawAllowed
                && n >= configuredGroupSets && t1 == t2) {
            SetScore extra = new SetScore();
            extra.setMatchId(matchId);
            extra.setSetNumber(n + 1);
            extra.setPlayer1Score(0);
            extra.setPlayer2Score(0);
            extra.setPlayer1IsX(false);
            extra.setPlayer2IsX(false);
            extra.setHammerPlayerId(resolveHammerPlayerId(match, currentHammerSide));
            extra.setIsBlankEnd((byte) 1);
            extra.setCreatedAt(LocalDateTime.now());
            setScoreService.save(extra);
            writeEditLog(operator, matchId, n + 1, oldBySetNo.get(n + 1), extra);
        }
        match.setFirstEndHammer(firstEndHammer == null ? null : firstEndHammer.byteValue());
        if (t1 > t2) match.setWinnerId(match.getPlayer1Id());
        else if (t2 > t1) match.setWinnerId(match.getPlayer2Id());
        else match.setWinnerId(null);
        match.setStatus((byte) 2);
        match.setUpdatedAt(LocalDateTime.now());
        matchService.updateById(match);
        if (hasX && Boolean.TRUE.equals(autoAccept)) {
            acceptMatchScore(operator, matchId, signature);
        } else if (!wasLockedBySuperAdmin && groupDeadlinePassed) {
            acceptMatchScore(operator, matchId, signature);
        }

        if (!wasLockedBySuperAdmin && groupDeadlinePassed && !Boolean.TRUE.equals(match.getResultLocked())) {
            User superAdmin = findSuperAdmin();
            if (superAdmin != null) {
                acceptMatchScore(superAdmin, matchId, "SYSTEM_AUTO_ACCEPT");
            }
        }
    }

    private Integer normalizeHammerSide(Integer hammerSide) {
        if (hammerSide == null || (hammerSide != 1 && hammerSide != 2)) {
            return null;
        }
        return hammerSide;
    }

    private Integer calcNextHammerSide(Integer currentHammerSide, int player1Score, int player2Score) {
        if (currentHammerSide == null) return null;
        if (player1Score == 0 && player2Score == 0) {
            // 按业务规则：0-0 也交换先后手
            return currentHammerSide == 1 ? 2 : 1;
        }
        if (player1Score > player2Score) {
            // player1 得分，则下一局由 player2 后手（持锤）
            return 2;
        }
        if (player2Score > player1Score) {
            // player2 得分，则下一局由 player1 后手（持锤）
            return 1;
        }
        // 异常并列比分（非0-0）兜底：交换先后手
        return currentHammerSide == 1 ? 2 : 1;
    }

    private Long resolveHammerPlayerId(Match match, Integer hammerSide) {
        if (match == null || hammerSide == null) {
            return null;
        }
        return hammerSide == 1 ? match.getPlayer1Id() : match.getPlayer2Id();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptMatchScore(User operator, Long matchId, String signature) {
        Match match = matchService.getById(matchId);
        if (match == null) throw new IllegalStateException("比赛不存在");
        if (operator == null) throw new SecurityException("未登录");
        if (requiresSignature(match) && (signature == null || signature.trim().isEmpty())) {
            throw new IllegalArgumentException("该阶段验收必须提供电子签名");
        }
        if (!canAcceptMatchScore(operator, match)) throw new SecurityException("无权验收此比赛");
        MatchAcceptance acceptance = matchAcceptanceMapper.selectOne(Wrappers.<MatchAcceptance>lambdaQuery()
                .eq(MatchAcceptance::getMatchId, matchId)
                .eq(MatchAcceptance::getUserId, operator.getId())
                .last("LIMIT 1"));
        if (acceptance == null) {
            acceptance = new MatchAcceptance();
            acceptance.setMatchId(matchId);
            acceptance.setUserId(operator.getId());
            acceptance.setAcceptedAt(LocalDateTime.now());
            acceptance.setSignature(signature == null ? "" : signature.trim());
            matchAcceptanceMapper.insert(acceptance);
        } else {
            acceptance.setAcceptedAt(LocalDateTime.now());
            acceptance.setSignature(signature == null ? "" : signature.trim());
            matchAcceptanceMapper.updateById(acceptance);
        }
        if (!isAcceptanceCompleted(match)) return;
        match.setResultLocked(true);
        match.setAcceptedByUserId(operator.getId());
        match.setAcceptedAt(LocalDateTime.now());
        match.setUpdatedAt(LocalDateTime.now());
        matchService.updateById(match);
        knockoutBracketService.tryAutoGenerateFromGroupStage(operator, match.getTournamentId());
        knockoutBracketService.onKnockoutMatchLocked(operator, match);
    }

    public List<MatchAcceptance> listAcceptances(Long matchId) {
        return matchAcceptanceMapper.selectList(Wrappers.<MatchAcceptance>lambdaQuery()
                .eq(MatchAcceptance::getMatchId, matchId)
                .orderByAsc(MatchAcceptance::getAcceptedAt));
    }

    public List<MatchScoreEditLog> listScoreEditLogs(Long matchId) {
        return matchScoreEditLogMapper.selectList(Wrappers.<MatchScoreEditLog>lambdaQuery()
                .eq(MatchScoreEditLog::getMatchId, matchId)
                .orderByDesc(MatchScoreEditLog::getEditedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public void autoAcceptOverdueGroupMatches(Long tournamentId) {
        TournamentCompetitionConfig cfg = getConfig(tournamentId);
        if (cfg == null || cfg.getGroupStageDeadline() == null || LocalDateTime.now().isBefore(cfg.getGroupStageDeadline())) return;
        User superAdmin = findSuperAdmin();
        if (superAdmin == null) return;
        List<Match> matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .eq(Match::getResultLocked, false)
                .eq(Match::getStatus, (byte) 2)
                .list();
        for (Match m : matches) {
            acceptMatchScore(superAdmin, m.getId(), "SYSTEM_AUTO_ACCEPT");
        }
    }

    @Override
    public boolean canAcceptMatchScore(User u, Match match) {
        if (u == null || match == null) return false;
        if (isStaff(u, match.getTournamentId())) return true;
        return Objects.equals(u.getId(), match.getPlayer1Id()) || Objects.equals(u.getId(), match.getPlayer2Id());
    }

    private boolean isAcceptanceCompleted(Match match) {
        List<MatchAcceptance> accepts = listAcceptances(match.getId());
        Set<Long> acceptedUserIds = accepts.stream().map(MatchAcceptance::getUserId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        boolean p1Accepted = acceptedUserIds.contains(match.getPlayer1Id());
        boolean p2Accepted = acceptedUserIds.contains(match.getPlayer2Id());
        if (!p1Accepted || !p2Accepted) return false;
        if (Objects.equals(match.getPhaseCode(), "GROUP")) return true;
        Tournament t = tournamentService.getById(match.getTournamentId());
        if (t == null) return false;
        Set<Long> participantIds = Set.of(match.getPlayer1Id(), match.getPlayer2Id());
        List<User> staffAccepted = acceptedUserIds.stream()
                .map(userService::getById)
                .filter(Objects::nonNull)
                .filter(u -> isStaff(u, match.getTournamentId()))
                .toList();
        if (staffAccepted.isEmpty()) return false;
        boolean participantHasStaff = participantIds.stream()
                .map(userService::getById)
                .filter(Objects::nonNull)
                .anyMatch(u -> isStaff(u, match.getTournamentId()));
        if (!participantHasStaff) return true;
        return staffAccepted.stream().anyMatch(u -> !participantIds.contains(u.getId()));
    }

    private boolean requiresSignature(Match match) {
        if (match == null) return false;
        String phase = match.getPhaseCode();
        return "QUALIFIER".equalsIgnoreCase(phase)
                || "MAIN".equalsIgnoreCase(phase)
                || "FINAL".equalsIgnoreCase(phase);
    }

    private boolean isStaff(User u, Long tournamentId) {
        if (u == null) return false;
        if (u.getRole() != null && u.getRole() <= 1) return true;
        Tournament t = tournamentService.getById(tournamentId);
        return t != null && Objects.equals(t.getHostUserId(), u.getId());
    }

    private User findSuperAdmin() {
        return userService.lambdaQuery().eq(User::getRole, 0).last("LIMIT 1").one();
    }

    private record ScoreToken(int score, boolean isX) {}

    private ScoreToken parseScoreToken(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return new ScoreToken(0, false);
        if ("X".equalsIgnoreCase(s)) return new ScoreToken(0, true);
        int v;
        try {
            v = Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("比分仅允许0-8或X");
        }
        if (v < 0 || v > 8) throw new IllegalArgumentException("比分仅允许0-8或X");
        return new ScoreToken(v, false);
    }

    private int detectXCutoff(List<String> p1, List<String> p2) {
        int n = Math.max(p1.size(), p2.size());
        for (int i = 0; i < n; i++) {
            String a = i < p1.size() ? p1.get(i) : null;
            String b = i < p2.size() ? p2.get(i) : null;
            if ("X".equalsIgnoreCase(a == null ? "" : a.trim()) || "X".equalsIgnoreCase(b == null ? "" : b.trim())) {
                return i;
            }
        }
        return -1;
    }

    private void writeEditLog(User operator, Long matchId, int setNo, SetScore oldScore, SetScore newScore) {
        if (operator == null || matchId == null || newScore == null) return;
        boolean changed = oldScore == null
                || !Objects.equals(oldScore.getPlayer1Score(), newScore.getPlayer1Score())
                || !Objects.equals(oldScore.getPlayer2Score(), newScore.getPlayer2Score())
                || !Objects.equals(Boolean.TRUE.equals(oldScore.getPlayer1IsX()), Boolean.TRUE.equals(newScore.getPlayer1IsX()))
                || !Objects.equals(Boolean.TRUE.equals(oldScore.getPlayer2IsX()), Boolean.TRUE.equals(newScore.getPlayer2IsX()));
        if (!changed) return;
        MatchScoreEditLog log = new MatchScoreEditLog();
        log.setMatchId(matchId);
        log.setSetNumber(setNo);
        log.setEditorUserId(operator.getId());
        log.setOldPlayer1Score(oldScore == null ? null : oldScore.getPlayer1Score());
        log.setOldPlayer2Score(oldScore == null ? null : oldScore.getPlayer2Score());
        log.setNewPlayer1Score(newScore.getPlayer1Score());
        log.setNewPlayer2Score(newScore.getPlayer2Score());
        log.setOldPlayer1IsX(oldScore != null && Boolean.TRUE.equals(oldScore.getPlayer1IsX()));
        log.setOldPlayer2IsX(oldScore != null && Boolean.TRUE.equals(oldScore.getPlayer2IsX()));
        log.setNewPlayer1IsX(Boolean.TRUE.equals(newScore.getPlayer1IsX()));
        log.setNewPlayer2IsX(Boolean.TRUE.equals(newScore.getPlayer2IsX()));
        log.setEditedAt(LocalDateTime.now());
        matchScoreEditLogMapper.insert(log);
    }
}
