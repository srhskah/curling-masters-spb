package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.dto.TournamentRegistrationPreviewDto;
import com.example.dto.TournamentRegistrationRowDto;
import com.example.dto.RankingEntry;
import com.example.entity.*;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.mapper.TournamentRegistrationMapper;
import com.example.mapper.TournamentRegistrationSettingMapper;
import com.example.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TournamentRegistrationServiceImpl implements ITournamentRegistrationService {

    @Autowired
    private TournamentRegistrationSettingMapper settingMapper;
    @Autowired
    private TournamentRegistrationMapper registrationMapper;
    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private SeriesService seriesService;
    @Autowired
    private UserService userService;
    @Autowired
    private RankingService rankingService;
    @Autowired
    private UserTournamentPointsService userTournamentPointsService;
    @Autowired
    private TournamentEntryService tournamentEntryService;
    @Autowired
    private ITournamentLevelService tournamentLevelService;
    @Autowired
    private INotificationService notificationService;
    @Autowired
    private TournamentCompetitionConfigMapper competitionConfigMapper;
    @Autowired
    private TournamentRankingRosterService tournamentRankingRosterService;

    @Override
    public TournamentRegistrationSetting getSetting(Long tournamentId) {
        if (tournamentId == null) return null;
        return settingMapper.selectById(tournamentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSetting(User editor, TournamentRegistrationSetting form) {
        if (form == null || form.getTournamentId() == null) {
            throw new IllegalArgumentException("缺少赛事 ID");
        }
        Long tid = form.getTournamentId();
        Tournament t = tournamentService.getById(tid);
        if (t == null) {
            throw new IllegalStateException("赛事不存在");
        }
        if (t.getStatus() == null || t.getStatus() != 0) {
            throw new IllegalStateException("仅「筹备中」赛事可配置报名接龙");
        }
        if (!canManage(editor, tid)) {
            throw new SecurityException("无权修改报名配置");
        }
        LocalDateTime now = LocalDateTime.now();
        TournamentRegistrationSetting db = settingMapper.selectById(tid);
        if (db == null) {
            db = new TournamentRegistrationSetting();
            db.setTournamentId(tid);
            db.setCreatedAt(now);
        }
        if (form.getEnabled() != null) {
            db.setEnabled(form.getEnabled());
        }
        if (form.getDeadline() != null) {
            db.setDeadline(form.getDeadline());
        }
        if (form.getQuotaN() != null) {
            db.setQuotaN(form.getQuotaN());
        }
        if (form.getMode() != null) {
            db.setMode(form.getMode());
        }
        if (form.getMainDirectM() != null) {
            db.setMainDirectM(form.getMainDirectM());
        }
        if (form.getQualifierSeedCount() != null) {
            db.setQualifierSeedCount(form.getQualifierSeedCount());
        }
        if (form.getBanTotalRankTop() != null) {
            db.setBanTotalRankTop(form.getBanTotalRankTop());
        }
        if (form.getBanOtherTournamentId() != null) {
            db.setBanOtherTournamentId(form.getBanOtherTournamentId());
        }
        if (form.getBanOtherTournamentTop() != null) {
            db.setBanOtherTournamentTop(form.getBanOtherTournamentTop());
        }
        validateSetting(db, t);
        // 截止后：仅当「有效通过人数 < n」时允许延长截止或调整名额（与业务说明一致）
        TournamentRegistrationSetting previous = settingMapper.selectById(tid);
        boolean wasEnabled = previous != null && Boolean.TRUE.equals(previous.getEnabled());
        if (previous != null && previous.getDeadline() != null && !now.isBefore(previous.getDeadline())) {
            boolean deadlineExtended = db.getDeadline() != null && previous.getDeadline() != null
                    && db.getDeadline().isAfter(previous.getDeadline());
            boolean quotaChanged = !Objects.equals(previous.getQuotaN(), db.getQuotaN())
                    || !Objects.equals(previous.getMainDirectM(), db.getMainDirectM())
                    || !Objects.equals(previous.getQualifierSeedCount(), db.getQualifierSeedCount());
            if (deadlineExtended || quotaChanged) {
                if (!allowAdjustAfterShortfall(tid, db, now)) {
                    throw new IllegalStateException("报名已截止且名额已满时，不可延长截止或修改名额；请先确认有效通过人数是否小于 n");
                }
            }
        }
        db.setUpdatedAt(now);
        if (settingMapper.selectById(tid) == null) {
            settingMapper.insert(db);
        } else {
            settingMapper.updateById(db);
        }
        boolean enabledNow = Boolean.TRUE.equals(db.getEnabled());
        if (!wasEnabled && enabledNow) {
            String title = "赛事报名已开启";
            String markdown = "赛事 **" + safeTournamentName(t) + "** 已开启报名。\n\n"
                    + "请前往赛事页面查看并完成报名。";
            notificationService.sendSystemNotification(title, markdown, "REGISTRATION_OPEN", tid);
        }
    }

    private static String safeTournamentName(Tournament tournament) {
        if (tournament == null) return "未命名赛事";
        String lv = tournament.getLevelCode() == null ? "赛事" : tournament.getLevelCode();
        return lv + (tournament.getId() != null ? (" #" + tournament.getId()) : "");
    }

    private void validateSetting(TournamentRegistrationSetting s, Tournament t) {
        if (Boolean.TRUE.equals(s.getEnabled())) {
            if (s.getDeadline() == null) {
                throw new IllegalArgumentException("开启报名须设置截止时间");
            }
            if (s.getQuotaN() == null || s.getQuotaN() < 1) {
                throw new IllegalArgumentException("正赛名额 n 须 ≥ 1");
            }
        }
        int n = s.getQuotaN() != null ? s.getQuotaN() : 32;
        int mode = s.getMode() != null ? s.getMode() : 0;
        if (mode == 1) {
            Integer m = s.getMainDirectM();
            if (m == null) {
                throw new IllegalArgumentException("正赛-资格赛模式须设置直通车人数 m");
            }
            if (m < 0 || m >= n) {
                throw new IllegalArgumentException("须满足 0 ≤ m < n");
            }
            int defaultSeed = n - m;
            int seed = s.getQualifierSeedCount() != null ? s.getQualifierSeedCount() : defaultSeed;
            if (seed < 0) {
                throw new IllegalArgumentException("资格赛种子位数不能为负");
            }
        }
        if (appliesBanRules(t.getLevelCode())) {
            if (s.getBanOtherTournamentId() != null && (s.getBanOtherTournamentTop() == null || s.getBanOtherTournamentTop() < 1)) {
                throw new IllegalArgumentException("指定禁报参照赛事时须填写该赛事排名前 K（K≥1）");
            }
        }
    }

    private static boolean appliesBanRules(String levelCode) {
        if (levelCode == null) return false;
        return levelCode.contains("500") || levelCode.contains("250");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(Long tournamentId, Long userId, LocalDateTime now) {
        String err = validateRegister(tournamentId, userId, now);
        if (err != null) {
            throw new IllegalStateException(err);
        }
        TournamentRegistration r = new TournamentRegistration();
        r.setTournamentId(tournamentId);
        r.setUserId(userId);
        r.setStatus(0);
        r.setRegisteredAt(now);
        registrationMapper.insert(r);
        maybeSnapQualifierRegistrationWhenFull(tournamentId, now);
    }

    private Integer qualifierQuotaFromCompetition(Long tournamentId) {
        TournamentCompetitionConfig c = competitionConfigMapper.selectById(tournamentId);
        if (c == null || c.getEntryMode() == null || c.getEntryMode() != 1) {
            return null;
        }
        return c.getKnockoutQualifyCount();
    }

    private int qualifierSideRegistrantCount(Long tournamentId, TournamentRegistrationSetting s, LocalDateTime now) {
        List<TournamentRegistration> ordered = loadOrderedEligible(tournamentId, s, now);
        int m = s.getMainDirectM() != null ? s.getMainDirectM() : 0;
        return Math.max(0, ordered.size() - m);
    }

    private boolean qualifierRegistrationStillOpenDueToShortfall(Long tournamentId, TournamentRegistrationSetting s, LocalDateTime now) {
        if (s.getMode() == null || s.getMode() != 1) {
            return false;
        }
        Integer k = qualifierQuotaFromCompetition(tournamentId);
        if (k == null || k < 1) {
            return false;
        }
        return qualifierSideRegistrantCount(tournamentId, s, now) < k;
    }

    private void maybeSnapQualifierRegistrationWhenFull(Long tournamentId, LocalDateTime now) {
        TournamentRegistrationSetting s = getSetting(tournamentId);
        if (s == null || s.getMode() == null || s.getMode() != 1) {
            return;
        }
        Integer k = qualifierQuotaFromCompetition(tournamentId);
        if (k == null || k < 1) {
            return;
        }
        int q = qualifierSideRegistrantCount(tournamentId, s, now);
        if (q < k) {
            return;
        }
        s.setDeadline(now);
        settingMapper.updateById(s);
        List<TournamentRegistration> ordered = loadOrderedEligible(tournamentId, s, now);
        int m = s.getMainDirectM() != null ? s.getMainDirectM() : 0;
        for (int i = m; i < ordered.size(); i++) {
            Long uid = ordered.get(i).getUserId();
            if (uid == null) {
                continue;
            }
            if (tournamentEntryService.lambdaQuery()
                    .eq(TournamentEntry::getTournamentId, tournamentId)
                    .eq(TournamentEntry::getUserId, uid)
                    .count() > 0) {
                continue;
            }
            TournamentEntry e = new TournamentEntry();
            e.setTournamentId(tournamentId);
            e.setUserId(uid);
            e.setEntryType(2);
            e.setCreatedAt(now);
            tournamentEntryService.save(e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long tournamentId, Long userId, LocalDateTime now) {
        TournamentRegistrationSetting s = getSetting(tournamentId);
        Tournament t = tournamentService.getById(tournamentId);
        if (!isRegistrationOpen(t, now) || s == null || !Boolean.TRUE.equals(s.getEnabled())) {
            throw new IllegalStateException("当前不可撤销报名（可能已截止或未开启）");
        }
        registrationMapper.delete(Wrappers.<TournamentRegistration>lambdaQuery()
                .eq(TournamentRegistration::getTournamentId, tournamentId)
                .eq(TournamentRegistration::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long tournamentId, Long targetUserId, User reviewer, LocalDateTime now) {
        if (!canManage(reviewer, tournamentId)) {
            throw new SecurityException("无权审批");
        }
        TournamentRegistration r = registrationMapper.selectOne(Wrappers.<TournamentRegistration>lambdaQuery()
                .eq(TournamentRegistration::getTournamentId, tournamentId)
                .eq(TournamentRegistration::getUserId, targetUserId));
        if (r == null) {
            throw new IllegalStateException("无此报名记录");
        }
        r.setStatus(1);
        r.setReviewedAt(now);
        r.setReviewedByUserId(reviewer.getId());
        registrationMapper.updateById(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long tournamentId, Long targetUserId, User reviewer, LocalDateTime now) {
        if (!canManage(reviewer, tournamentId)) {
            throw new SecurityException("无权审批");
        }
        registrationMapper.delete(Wrappers.<TournamentRegistration>lambdaQuery()
                .eq(TournamentRegistration::getTournamentId, tournamentId)
                .eq(TournamentRegistration::getUserId, targetUserId));
    }

    /**
     * 业务规则：截止后且（有效通过人数 &lt; n）时可延长截止或改名额
     */
    private boolean allowAdjustAfterShortfall(Long tournamentId, TournamentRegistrationSetting s, LocalDateTime now) {
        if (s.getDeadline() == null || s.getQuotaN() == null) return false;
        if (now.isBefore(s.getDeadline())) {
            return false;
        }
        int n = s.getQuotaN();
        List<TournamentRegistration> ordered = loadOrderedEligible(tournamentId, s, now);
        return ordered.size() < n;
    }

    @Override
    public List<TournamentRegistrationRowDto> listRows(Long tournamentId, LocalDateTime now) {
        TournamentRegistrationSetting s = getSetting(tournamentId);
        List<TournamentRegistration> list = registrationMapper.selectList(Wrappers.<TournamentRegistration>lambdaQuery()
                .eq(TournamentRegistration::getTournamentId, tournamentId));
        Map<Long, Integer> totalRankByUser = buildUserIdToTotalRankMap();
        sortRegistrationsByTotalRankThenTime(list, totalRankByUser);
        List<Long> uids = list.stream().map(TournamentRegistration::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> users = uids.isEmpty() ? Map.of() : userService.listByIds(uids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<TournamentRegistrationRowDto> out = new ArrayList<>();
        for (TournamentRegistration r : list) {
            User u = users.get(r.getUserId());
            out.add(TournamentRegistrationRowDto.builder()
                    .registrationId(r.getId())
                    .userId(r.getUserId())
                    .username(u != null ? u.getUsername() : "?")
                    .totalRankPosition(totalRankByUser.get(r.getUserId()))
                    .status(r.getStatus())
                    .registeredAt(r.getRegisteredAt())
                    .effectiveApproved(isEffectiveApproved(r, s, now))
                    .seriesCrossNote(buildSeriesCrossNote(r.getUserId(), tournamentId))
                    .build());
        }
        return out;
    }

    @Override
    public TournamentRegistrationPreviewDto preview(Long tournamentId, LocalDateTime now) {
        TournamentRegistrationSetting s = getSetting(tournamentId);
        if (s == null || !Boolean.TRUE.equals(s.getEnabled())) {
            return TournamentRegistrationPreviewDto.builder()
                    .mainDirectUsernames(List.of())
                    .qualifierSeedUsernames(List.of())
                    .modeDescription("未开启报名")
                    .build();
        }
        List<TournamentRegistration> ordered = loadOrderedEligible(tournamentId, s, now);
        int n = s.getQuotaN() != null ? s.getQuotaN() : 32;
        int mode = s.getMode() != null ? s.getMode() : 0;
        Map<Long, String> names = loadUsernames(ordered.stream().map(TournamentRegistration::getUserId).toList());

        if (s.getDeadline() != null && now.isBefore(s.getDeadline())) {
            return TournamentRegistrationPreviewDto.builder()
                    .mainDirectUsernames(List.of())
                    .qualifierSeedUsernames(List.of())
                    .modeDescription("报名尚未截止，截止后按总排名优先、无名者按报名时间先后，并结合审批结果产生名单")
                    .build();
        }

        List<String> main = new ArrayList<>();
        List<String> seeds = new ArrayList<>();
        String desc;
        if (mode == 0) {
            int take = Math.min(n, ordered.size());
            for (int i = 0; i < take; i++) {
                main.add(names.getOrDefault(ordered.get(i).getUserId(), "?"));
            }
            desc = "默认模式：正赛直通车最多 " + n + " 人，顺序为总排名优先、无名者按报名时间先后（含截止时待审视同同意）";
        } else {
            int m = s.getMainDirectM() != null ? s.getMainDirectM() : 0;
            int seedCount = s.getQualifierSeedCount() != null ? s.getQualifierSeedCount() : (n - m);
            int takeMain = Math.min(m, ordered.size());
            for (int i = 0; i < takeMain; i++) {
                main.add(names.getOrDefault(ordered.get(i).getUserId(), "?"));
            }
            for (int i = takeMain; i < Math.min(takeMain + seedCount, ordered.size()); i++) {
                seeds.add(names.getOrDefault(ordered.get(i).getUserId(), "?"));
            }
            desc = "正赛-资格赛：直通车 " + m + " 人，资格赛种子约 " + seedCount + " 人，正赛总名额 " + n + "；顺序为总排名优先、无名者按报名时间先后";
        }
        return TournamentRegistrationPreviewDto.builder()
                .mainDirectUsernames(main)
                .qualifierSeedUsernames(seeds)
                .modeDescription(desc)
                .build();
    }

    @Override
    public String validateRegister(Long tournamentId, Long userId, LocalDateTime now) {
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) return "赛事不存在";
        if (t.getStatus() == null || t.getStatus() != 0) return "仅筹备中赛事开放报名";
        TournamentRegistrationSetting s = getSetting(tournamentId);
        if (s == null || !Boolean.TRUE.equals(s.getEnabled())) return "报名未开启";
        if (s.getDeadline() != null && !now.isBefore(s.getDeadline())
                && !qualifierRegistrationStillOpenDueToShortfall(tournamentId, s, now)) {
            return "报名已截止";
        }
        long existing = registrationMapper.selectCount(Wrappers.<TournamentRegistration>lambdaQuery()
                .eq(TournamentRegistration::getTournamentId, tournamentId)
                .eq(TournamentRegistration::getUserId, userId));
        if (existing > 0) return "您已报名";
        if (appliesBanRules(t.getLevelCode())) {
            Integer banTop = s.getBanTotalRankTop();
            if (banTop != null && banTop > 0) {
                Integer pos = getUserTotalRankPosition(userId);
                if (pos != null && pos <= banTop) {
                    return "您在总排名前 " + banTop + " 名，本赛事禁止报名";
                }
            }
            Long otherTid = s.getBanOtherTournamentId();
            Integer otherTop = s.getBanOtherTournamentTop();
            if (otherTid != null && otherTop != null && otherTop > 0) {
                Integer rnk = getUserRankInTournament(userId, otherTid);
                if (rnk != null && rnk <= otherTop) {
                    return "您在指定参照赛事中排名前 " + otherTop + " ，禁止报名";
                }
            }
        }
        return null;
    }

    @Override
    public boolean canManage(User user, Long tournamentId) {
        if (user == null) return false;
        if (user.getRole() != null && user.getRole() <= 1) return true;
        Tournament t = tournamentService.getById(tournamentId);
        return t != null && t.getHostUserId() != null && t.getHostUserId().equals(user.getId());
    }

    @Override
    public boolean isRegistrationOpen(Tournament tournament, LocalDateTime now) {
        if (tournament == null || tournament.getStatus() == null || tournament.getStatus() != 0) {
            return false;
        }
        TournamentRegistrationSetting s = getSetting(tournament.getId());
        if (s == null || !Boolean.TRUE.equals(s.getEnabled()) || s.getDeadline() == null) {
            return false;
        }
        if (now.isBefore(s.getDeadline())) {
            return true;
        }
        return qualifierRegistrationStillOpenDueToShortfall(tournament.getId(), s, now);
    }

    @Override
    public boolean registrationModuleActive(Tournament tournament, LocalDateTime now) {
        return tournament != null && tournament.getStatus() != null && tournament.getStatus() == 0;
    }

    @Override
    public boolean isRegistrationEnabled(Tournament tournament) {
        if (tournament == null || tournament.getId() == null) {
            return false;
        }
        TournamentRegistrationSetting s = getSetting(tournament.getId());
        return s != null && Boolean.TRUE.equals(s.getEnabled());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int materializeMainDirectEntries(User operator, Long tournamentId, LocalDateTime now) {
        if (!canManage(operator, tournamentId)) {
            throw new SecurityException("无权写入正赛入选名单");
        }
        TournamentRegistrationSetting s = getSetting(tournamentId);
        if (s == null || !Boolean.TRUE.equals(s.getEnabled()) || s.getDeadline() == null) {
            throw new IllegalStateException("报名未配置或未开启");
        }
        if (now.isBefore(s.getDeadline())) {
            throw new IllegalStateException("报名尚未截止，无法写入正赛名单");
        }
        List<TournamentRegistration> ordered = loadOrderedEligible(tournamentId, s, now);
        int mode = s.getMode() != null ? s.getMode() : 0;
        int n = s.getQuotaN() != null ? s.getQuotaN() : 32;
        int take;
        if (mode == 0) {
            take = Math.min(n, ordered.size());
        } else {
            int m = s.getMainDirectM() != null ? s.getMainDirectM() : 0;
            take = Math.min(m, ordered.size());
        }
        int added = 0;
        for (int i = 0; i < take; i++) {
            Long uid = ordered.get(i).getUserId();
            long cnt = tournamentEntryService.lambdaQuery()
                    .eq(TournamentEntry::getTournamentId, tournamentId)
                    .eq(TournamentEntry::getUserId, uid)
                    .count();
            if (cnt == 0) {
                TournamentEntry e = new TournamentEntry();
                e.setTournamentId(tournamentId);
                e.setUserId(uid);
                e.setEntryType(1);
                e.setCreatedAt(now);
                tournamentEntryService.save(e);
                added++;
            }
        }
        return added;
    }

    @Override
    public List<Tournament> listOpenRegistrationTournaments(Integer limit, LocalDateTime now) {
        List<TournamentRegistrationSetting> sets = settingMapper.selectList(Wrappers.<TournamentRegistrationSetting>lambdaQuery()
                .eq(TournamentRegistrationSetting::getEnabled, true));
        List<Long> ids = sets.stream()
                .filter(s -> s.getDeadline() != null && now.isBefore(s.getDeadline()))
                .map(TournamentRegistrationSetting::getTournamentId)
                .toList();
        if (ids.isEmpty()) return List.of();
        List<Tournament> ts = tournamentService.listByIds(ids);
        List<Tournament> filtered = ts.stream()
                .filter(t -> t.getStatus() != null && t.getStatus() == 0)
                .sorted(Comparator.comparing(Tournament::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        if (limit != null && limit > 0 && filtered.size() > limit) {
            return filtered.subList(0, limit);
        }
        return filtered;
    }

    @Override
    public boolean hasRegistration(Long tournamentId, Long userId) {
        if (tournamentId == null || userId == null) return false;
        return registrationMapper.selectCount(Wrappers.<TournamentRegistration>lambdaQuery()
                .eq(TournamentRegistration::getTournamentId, tournamentId)
                .eq(TournamentRegistration::getUserId, userId)) > 0;
    }

    @Override
    public List<Tournament> listOpenRegistrationForSeason(Long seasonId, LocalDateTime now) {
        if (seasonId == null) return List.of();
        List<Series> seriesList = seriesService.lambdaQuery().eq(Series::getSeasonId, seasonId).list();
        Set<Long> seriesIds = seriesList.stream().map(Series::getId).collect(Collectors.toSet());
        return listOpenRegistrationTournaments(null, now).stream()
                .filter(t -> t.getSeriesId() != null && seriesIds.contains(t.getSeriesId()))
                .collect(Collectors.toList());
    }

    private List<TournamentRegistration> loadOrderedEligible(Long tournamentId, TournamentRegistrationSetting s, LocalDateTime now) {
        List<TournamentRegistration> list = registrationMapper.selectList(Wrappers.<TournamentRegistration>lambdaQuery()
                .eq(TournamentRegistration::getTournamentId, tournamentId)
                .in(TournamentRegistration::getStatus, 0, 1));
        Map<Long, Integer> totalRankByUser = buildUserIdToTotalRankMap();
        list = list.stream()
                .filter(r -> isEffectiveApproved(r, s, now))
                .collect(Collectors.toList());
        sortRegistrationsByTotalRankThenTime(list, totalRankByUser);
        return list;
    }

    /** 与 {@link #getUserTotalRankPosition(Long)} 一致：全站总排名名次（1 起） */
    private Map<Long, Integer> buildUserIdToTotalRankMap() {
        List<RankingEntry> all = rankingService.getTotalRanking(null);
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            Long uid = all.get(i).getUserId();
            if (uid != null) {
                map.put(uid, i + 1);
            }
        }
        return map;
    }

    /**
     * 有总排名名次的优先且按名次升序；榜上无名者排在后面，彼此间按报名时间先后。
     */
    private static void sortRegistrationsByTotalRankThenTime(List<TournamentRegistration> list, Map<Long, Integer> totalRankByUser) {
        Comparator<TournamentRegistration> cmp = (a, b) -> {
            Integer pa = totalRankByUser.get(a.getUserId());
            Integer pb = totalRankByUser.get(b.getUserId());
            boolean ha = pa != null;
            boolean hb = pb != null;
            if (ha && hb) {
                int byRank = Integer.compare(pa, pb);
                if (byRank != 0) {
                    return byRank;
                }
                Long ua = a.getUserId();
                Long ub = b.getUserId();
                if (ua == null && ub == null) {
                    return 0;
                }
                if (ua == null) {
                    return 1;
                }
                if (ub == null) {
                    return -1;
                }
                return Long.compare(ua, ub);
            }
            if (ha != hb) {
                return ha ? -1 : 1;
            }
            int byTime = Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())
                    .compare(a.getRegisteredAt(), b.getRegisteredAt());
            if (byTime != 0) {
                return byTime;
            }
            Long ua = a.getUserId();
            Long ub = b.getUserId();
            if (ua == null && ub == null) {
                return 0;
            }
            if (ua == null) {
                return 1;
            }
            if (ub == null) {
                return -1;
            }
            return Long.compare(ua, ub);
        };
        list.sort(cmp);
    }

    private static boolean isEffectiveApproved(TournamentRegistration r, TournamentRegistrationSetting s, LocalDateTime now) {
        if (r.getStatus() == null) return false;
        if (r.getStatus() == 2) return false;
        if (r.getStatus() == 1) return true;
        // 待审：截止后视同同意
        return s.getDeadline() != null && !now.isBefore(s.getDeadline());
    }

    private String buildSeriesCrossNote(Long userId, Long currentTournamentId) {
        Tournament t = tournamentService.getById(currentTournamentId);
        if (t == null || t.getSeriesId() == null) return "";
        List<Tournament> siblings = tournamentService.lambdaQuery()
                .eq(Tournament::getSeriesId, t.getSeriesId())
                .ne(Tournament::getId, currentTournamentId)
                .list();
        List<String> parts = new ArrayList<>();
        for (Tournament ot : siblings) {
            List<UserTournamentPoints> vis = tournamentRankingRosterService.filterUtpsForDisplay(ot.getId(),
                    userTournamentPointsService.lambdaQuery()
                            .eq(UserTournamentPoints::getTournamentId, ot.getId())
                            .list());
            boolean hasPoints = vis.stream().anyMatch(u -> userId.equals(u.getUserId()));
            boolean registered = registrationMapper.selectCount(Wrappers.<TournamentRegistration>lambdaQuery()
                    .eq(TournamentRegistration::getTournamentId, ot.getId())
                    .eq(TournamentRegistration::getUserId, userId)) > 0;
            if (hasPoints) {
                parts.add("已在「" + formatTournamentLabel(ot) + "」有成绩");
            } else if (registered) {
                parts.add("已报名「" + formatTournamentLabel(ot) + "」");
            }
        }
        return String.join("；", parts);
    }

    private String formatTournamentLabel(Tournament ot) {
        String levelName = ot.getLevelCode();
        TournamentLevel lvl = tournamentLevelService.lambdaQuery()
                .eq(TournamentLevel::getCode, ot.getLevelCode())
                .last("LIMIT 1")
                .one();
        if (lvl != null && lvl.getName() != null) {
            levelName = lvl.getName();
        }
        if (ot.getStartDate() != null) {
            return levelName + " " + ot.getStartDate();
        }
        return levelName != null ? levelName : "赛事" + ot.getId();
    }

    private Map<Long, String> loadUsernames(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userService.listByIds(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private Integer getUserTotalRankPosition(Long userId) {
        List<RankingEntry> all = rankingService.getTotalRanking(null);
        for (int i = 0; i < all.size(); i++) {
            if (userId.equals(all.get(i).getUserId())) {
                return i + 1;
            }
        }
        return null;
    }

    private Integer getUserRankInTournament(Long userId, Long tournamentId) {
        List<UserTournamentPoints> list = tournamentRankingRosterService.filterUtpsForDisplay(tournamentId,
                userTournamentPointsService.lambdaQuery()
                        .eq(UserTournamentPoints::getTournamentId, tournamentId)
                        .orderByDesc(UserTournamentPoints::getPoints)
                        .list());
        for (int i = 0; i < list.size(); i++) {
            if (userId.equals(list.get(i).getUserId())) {
                return i + 1;
            }
        }
        return null;
    }
}
