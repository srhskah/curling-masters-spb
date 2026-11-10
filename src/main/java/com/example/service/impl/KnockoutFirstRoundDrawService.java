package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.dto.DrawPool;
import com.example.entity.Tournament;
import com.example.entity.TournamentCompetitionConfig;
import com.example.entity.TournamentDraw;
import com.example.entity.TournamentDrawResult;
import com.example.entity.User;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.mapper.TournamentDrawMapper;
import com.example.mapper.TournamentDrawResultMapper;
import com.example.service.TournamentService;
import com.example.service.UserService;
import com.example.service.knockout.KnockoutFirstRoundDrawPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class KnockoutFirstRoundDrawService {

    @Autowired private TournamentCompetitionConfigMapper configMapper;
    @Autowired private TournamentDrawMapper drawMapper;
    @Autowired private TournamentDrawResultMapper drawResultMapper;
    @Autowired private KnockoutBracketService knockoutBracketService;
    @Autowired private UserService userService;
    @Autowired private TournamentService tournamentService;

    public boolean isEnabled(TournamentCompetitionConfig cfg) {
        return cfg != null && Boolean.TRUE.equals(cfg.getKnockoutFirstDrawEnabled());
    }

    public Map<String, Object> pageModel(Long tournamentId, User viewer) {
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("enabled", isEnabled(cfg));
        if (!isEnabled(cfg)) {
            model.put("blockedReason", "本届未启用淘汰赛首轮抽签");
            return model;
        }
        boolean locked = knockoutBracketService.isGroupStageFullyLocked(tournamentId);
        model.put("groupLocked", locked);
        boolean qualifierDraw = Boolean.TRUE.equals(cfg.getKnockoutQualifierDraw())
                && knockoutBracketService.isFirstRoundMountedQualifier(cfg);
        model.put("qualifierDraw", qualifierDraw);
        model.put("mountedQualifier", knockoutBracketService.isFirstRoundMountedQualifier(cfg));
        if (!locked) {
            model.put("blockedReason", "小组赛全部验收后才能抽签");
            return model;
        }
        Board board = board(cfg, tournamentId);
        model.put("bracketSize", board.bracketSize);
        model.put("qualifyCount", board.qualifyCount);
        model.put("pots", potViews(board, viewer == null ? null : viewer.getId()));
        TournamentDraw draw = loadDraw(tournamentId);
        model.put("status", draw == null ? "PENDING" : draw.getStatus());
        model.put("completed", draw != null && "COMPLETED".equals(draw.getStatus()));
        if (viewer != null) {
            Integer rank = board.rankByUser.get(viewer.getId());
            model.put("myRank", rank);
            model.put("canDraw", rank != null && board.participantRanks.contains(rank)
                    && !board.prizeByUser.containsKey(viewer.getId())
                    && (draw == null || !"COMPLETED".equals(draw.getStatus())));
            Integer mine = board.prizeByUser.get(viewer.getId());
            model.put("myPrize", mine == null ? null
                    : KnockoutFirstRoundDrawPlan.prizeLabel(mine, board.bracketSize, board.shownQualify));
        } else {
            model.put("canDraw", false);
        }
        model.put("rows", resultRows(board));
        return model;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> performDraw(User user, Long tournamentId) {
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("请先登录");
        }
        Board board = requireOpenBoard(tournamentId);
        TournamentDraw existingDraw = loadDraw(tournamentId);
        if (existingDraw != null && "COMPLETED".equals(existingDraw.getStatus())) {
            throw new IllegalStateException("抽签已结束");
        }
        Integer rank = board.rankByUser.get(user.getId());
        if (rank == null || !board.participantRanks.contains(rank)) {
            throw new IllegalStateException("你不在本届淘汰赛首轮抽签名单中");
        }
        if (board.prizeByUser.containsKey(user.getId())) {
            throw new IllegalStateException("你已经抽过签");
        }
        KnockoutFirstRoundDrawPlan.Pot pot = potOf(board, rank);
        int prize = takePrize(board, pot);
        insertResult(tournamentId, user.getId(), rank, prize, false);
        board.prizeByRank.put(rank, prize);
        board.prizeByUser.put(user.getId(), prize);
        autoFillLast(tournamentId, board, pot);
        String applyMessage = tryApply(user, tournamentId, board);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("prize", KnockoutFirstRoundDrawPlan.prizeLabel(prize, board.bracketSize, board.shownQualify));
        if (applyMessage != null) {
            out.put("message", applyMessage);
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reset(User operator, Long tournamentId) {
        requireEnabled(tournamentId);
        drawResultMapper.delete(Wrappers.<TournamentDrawResult>lambdaQuery()
                .eq(TournamentDrawResult::getTournamentId, tournamentId)
                .eq(TournamentDrawResult::getDrawPool, DrawPool.KNOCKOUT.name()));
        TournamentDraw draw = ensureDraw(tournamentId);
        draw.setStatus("IN_PROGRESS");
        draw.setUpdatedAt(LocalDateTime.now());
        drawMapper.updateById(draw);
    }

    @Transactional(rollbackFor = Exception.class)
    public String applyAgain(User operator, Long tournamentId) {
        TournamentDraw existingDraw = loadDraw(tournamentId);
        if (existingDraw != null && "COMPLETED".equals(existingDraw.getStatus())) {
            throw new IllegalStateException("对阵已经写入。若要重抽，请先重置抽签");
        }
        Board board = requireOpenBoard(tournamentId);
        if (!boardComplete(board)) {
            throw new IllegalStateException("还有选手未抽签");
        }
        return tryApply(operator, tournamentId, board);
    }

    private String tryApply(User operator, Long tournamentId, Board board) {
        if (!boardComplete(board)) {
            return null;
        }
        List<int[]> mains = KnockoutFirstRoundDrawPlan.mainMatches(board.bracketSize, board.prizeByRank);
        List<int[]> qualifiers = qualifierPairs(board);
        knockoutBracketService.applyDrawnFirstRound(operator, tournamentId, mains, qualifiers);
        TournamentDraw draw = ensureDraw(tournamentId);
        draw.setStatus("COMPLETED");
        draw.setUpdatedAt(LocalDateTime.now());
        drawMapper.updateById(draw);
        return "抽签已完成，首轮对阵已按抽到的名次写入";
    }

    private List<int[]> qualifierPairs(Board board) {
        if (board.qualifyCount <= 0) {
            return List.of();
        }
        if (!board.qualifierDraw) {
            return KnockoutFirstRoundDrawPlan.originalQualifierPairs(board.bracketSize, board.qualifyCount);
        }
        KnockoutFirstRoundDrawPlan.Pot pot = KnockoutFirstRoundDrawPlan.qualifierPot(board.bracketSize, board.qualifyCount);
        List<int[]> out = new ArrayList<>();
        for (int drawer : pot.drawers()) {
            Integer prize = board.prizeByRank.get(drawer);
            if (prize == null) {
                throw new IllegalStateException("第 " + drawer + " 名的资格赛尚未抽签");
            }
            out.add(new int[]{drawer, prize});
        }
        return out;
    }

    private void autoFillLast(Long tournamentId, Board board, KnockoutFirstRoundDrawPlan.Pot pot) {
        List<Integer> leftPrizes = remainingPrizes(board, pot);
        List<Integer> leftDrawers = new ArrayList<>();
        for (int drawer : pot.drawers()) {
            if (!board.prizeByRank.containsKey(drawer)) {
                leftDrawers.add(drawer);
            }
        }
        if (leftDrawers.size() != 1 || leftPrizes.size() != 1) {
            return;
        }
        int drawer = leftDrawers.get(0);
        int prize = leftPrizes.get(0);
        Long userId = board.userByRank.get(drawer);
        if (userId == null) {
            return;
        }
        insertResult(tournamentId, userId, drawer, prize, true);
        board.prizeByRank.put(drawer, prize);
        board.prizeByUser.put(userId, prize);
    }

    private int takePrize(Board board, KnockoutFirstRoundDrawPlan.Pot pot) {
        List<Integer> left = remainingPrizes(board, pot);
        if (left.isEmpty()) {
            throw new IllegalStateException("这一档的签已经抽完");
        }
        return left.get(ThreadLocalRandom.current().nextInt(left.size()));
    }

    private List<Integer> remainingPrizes(Board board, KnockoutFirstRoundDrawPlan.Pot pot) {
        Set<Integer> taken = new LinkedHashSet<>();
        for (int drawer : pot.drawers()) {
            Integer prize = board.prizeByRank.get(drawer);
            if (prize != null) {
                taken.add(prize);
            }
        }
        List<Integer> left = new ArrayList<>();
        for (int prize : pot.prizes()) {
            if (!taken.contains(prize)) {
                left.add(prize);
            }
        }
        return left;
    }

    private boolean boardComplete(Board board) {
        return board.prizeByRank.keySet().containsAll(board.participantRanks);
    }

    private KnockoutFirstRoundDrawPlan.Pot potOf(Board board, int rank) {
        for (KnockoutFirstRoundDrawPlan.Pot pot : board.pots) {
            for (int drawer : pot.drawers()) {
                if (drawer == rank) {
                    return pot;
                }
            }
        }
        throw new IllegalStateException("没有找到你的抽签档");
    }

    private Board requireOpenBoard(Long tournamentId) {
        TournamentCompetitionConfig cfg = requireEnabled(tournamentId);
        if (!knockoutBracketService.isGroupStageFullyLocked(tournamentId)) {
            throw new IllegalStateException("小组赛须全部验收后才可抽签");
        }
        Tournament tournament = loadTournament(tournamentId);
        if (tournament.getStatus() == null || (tournament.getStatus() != 0 && tournament.getStatus() != 1)) {
            throw new IllegalStateException("仅筹备中或进行中的赛事可抽签");
        }
        ensureDraw(tournamentId);
        return board(cfg, tournamentId);
    }

    private Tournament loadTournament(Long tournamentId) {
        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            throw new IllegalStateException("赛事不存在");
        }
        return tournament;
    }

    private TournamentCompetitionConfig requireEnabled(Long tournamentId) {
        TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
        if (!isEnabled(cfg)) {
            throw new IllegalStateException("本届未启用淘汰赛首轮抽签");
        }
        if (cfg.getKnockoutStartRound() == null) {
            throw new IllegalStateException("请先配置淘汰赛首轮");
        }
        return cfg;
    }

    private Board board(TournamentCompetitionConfig cfg, Long tournamentId) {
        int bracketSize = KnockoutBracketService.playersInFirstKnockoutRound(cfg.getKnockoutStartRound());
        int qualifyCount = 0;
        boolean mounted = knockoutBracketService.isFirstRoundMountedQualifier(cfg);
        if (mounted) {
            qualifyCount = knockoutBracketService.mountedQualifyCount(cfg, tournamentId);
        }
        boolean qualifierDraw = mounted && Boolean.TRUE.equals(cfg.getKnockoutQualifierDraw());
        List<Long> ranked = knockoutBracketService.loadOverallRankedUserIdsForKnockout(tournamentId);
        int need = bracketSize + (mounted ? qualifyCount : 0);
        if (ranked.size() < need) {
            throw new IllegalStateException("人数不足（需至少 " + need + " 人，当前 " + ranked.size() + "）");
        }
        List<KnockoutFirstRoundDrawPlan.Pot> pots = new ArrayList<>(KnockoutFirstRoundDrawPlan.mainPots(bracketSize));
        if (qualifierDraw) {
            KnockoutFirstRoundDrawPlan.Pot extra = KnockoutFirstRoundDrawPlan.qualifierPot(bracketSize, qualifyCount);
            if (extra != null) {
                pots.add(extra);
            }
        }
        Board board = new Board();
        board.bracketSize = bracketSize;
        board.qualifyCount = qualifyCount;
        board.qualifierDraw = qualifierDraw;
        board.shownQualify = mounted ? qualifyCount : 0;
        board.pots = pots;
        board.userByRank = new LinkedHashMap<>();
        board.rankByUser = new LinkedHashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            Long userId = ranked.get(i);
            if (userId == null) {
                continue;
            }
            board.userByRank.put(i + 1, userId);
            board.rankByUser.putIfAbsent(userId, i + 1);
        }
        board.participantRanks = new LinkedHashSet<>();
        for (KnockoutFirstRoundDrawPlan.Pot pot : pots) {
            for (int drawer : pot.drawers()) {
                if (board.participantRanks.contains(drawer)) {
                    throw new IllegalStateException("抽签名次重叠，无法同时抽首轮和资格赛");
                }
                board.participantRanks.add(drawer);
            }
        }
        board.prizeByRank = new LinkedHashMap<>();
        board.prizeByUser = new LinkedHashMap<>();
        board.autoByUser = new LinkedHashMap<>();
        List<TournamentDrawResult> rows = drawResultMapper.selectList(Wrappers.<TournamentDrawResult>lambdaQuery()
                .eq(TournamentDrawResult::getTournamentId, tournamentId)
                .eq(TournamentDrawResult::getDrawPool, DrawPool.KNOCKOUT.name()));
        for (TournamentDrawResult row : rows) {
            if (row.getUserId() == null || row.getGroupSlotIndex() == null) {
                continue;
            }
            Integer rank = board.rankByUser.get(row.getUserId());
            if (rank == null || !board.participantRanks.contains(rank)) {
                continue;
            }
            board.prizeByRank.put(rank, row.getGroupSlotIndex());
            board.prizeByUser.put(row.getUserId(), row.getGroupSlotIndex());
            board.autoByUser.put(row.getUserId(), Boolean.TRUE.equals(row.getIsAutoAssigned()));
        }
        return board;
    }

    private List<Map<String, Object>> potViews(Board board, Long viewerId) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (KnockoutFirstRoundDrawPlan.Pot pot : board.pots) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("qualifier", pot.qualifier());
            view.put("title", pot.qualifier() ? "资格赛" : "首轮");
            List<String> drawers = new ArrayList<>();
            for (int rank : pot.drawers()) {
                drawers.add(rankText(board, rank));
            }
            List<String> prizes = new ArrayList<>();
            for (int rank : pot.prizes()) {
                String label = KnockoutFirstRoundDrawPlan.prizeLabel(rank, board.bracketSize, board.shownQualify);
                if (containsPrize(board, pot, rank)) {
                    label = label + "（已抽出）";
                }
                prizes.add(label);
            }
            view.put("drawers", String.join("、", drawers));
            view.put("prizes", String.join("、", prizes));
            view.put("mine", viewerId != null && containsDrawerUser(board, pot, viewerId));
            views.add(view);
        }
        return views;
    }

    private boolean containsDrawerUser(Board board, KnockoutFirstRoundDrawPlan.Pot pot, Long userId) {
        Integer rank = board.rankByUser.get(userId);
        if (rank == null) {
            return false;
        }
        for (int drawer : pot.drawers()) {
            if (drawer == rank) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPrize(Board board, KnockoutFirstRoundDrawPlan.Pot pot, int prize) {
        for (int drawer : pot.drawers()) {
            if (prize == board.prizeByRank.getOrDefault(drawer, -1)) {
                return true;
            }
        }
        return false;
    }

    private String rankText(Board board, int rank) {
        Long userId = board.userByRank.get(rank);
        String name = username(userId);
        return "第" + rank + "名" + (name.isEmpty() ? "" : " " + name);
    }

    private List<Map<String, Object>> resultRows(Board board) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>(board.participantRanks);
        ranks.sort(Integer::compareTo);
        for (Integer rank : ranks) {
            Integer prize = board.prizeByRank.get(rank);
            if (prize == null) {
                continue;
            }
            Long userId = board.userByRank.get(rank);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank);
            row.put("username", username(userId));
            row.put("prize", KnockoutFirstRoundDrawPlan.prizeLabel(prize, board.bracketSize, board.shownQualify));
            row.put("auto", Boolean.TRUE.equals(board.autoByUser.get(userId)));
            rows.add(row);
        }
        return rows;
    }

    private String username(Long userId) {
        if (userId == null) {
            return "";
        }
        User user = userService.getById(userId);
        if (user == null || user.getUsername() == null) {
            return "";
        }
        return user.getUsername();
    }

    private void insertResult(Long tournamentId, Long userId, int rank, int prize, boolean auto) {
        TournamentDrawResult existing = drawResultMapper.selectOne(Wrappers.<TournamentDrawResult>lambdaQuery()
                .eq(TournamentDrawResult::getTournamentId, tournamentId)
                .eq(TournamentDrawResult::getDrawPool, DrawPool.KNOCKOUT.name())
                .eq(TournamentDrawResult::getUserId, userId));
        if (existing != null) {
            throw new IllegalStateException("该选手已完成抽签");
        }
        long count = drawResultMapper.selectCount(Wrappers.<TournamentDrawResult>lambdaQuery()
                .eq(TournamentDrawResult::getTournamentId, tournamentId)
                .eq(TournamentDrawResult::getDrawPool, DrawPool.KNOCKOUT.name()));
        TournamentDrawResult row = new TournamentDrawResult();
        row.setTournamentId(tournamentId);
        row.setDrawPool(DrawPool.KNOCKOUT.name());
        row.setUserId(userId);
        row.setGroupId(0L);
        row.setTierNumber(rank);
        row.setGroupSlotIndex(prize);
        row.setDrawOrder((int) count + 1);
        row.setIsAutoAssigned(auto);
        row.setIsSeed(false);
        row.setCreatedAt(LocalDateTime.now());
        drawResultMapper.insert(row);
    }

    private TournamentDraw loadDraw(Long tournamentId) {
        return drawMapper.selectOne(Wrappers.<TournamentDraw>lambdaQuery()
                .eq(TournamentDraw::getTournamentId, tournamentId)
                .eq(TournamentDraw::getDrawPool, DrawPool.KNOCKOUT.name()));
    }

    private TournamentDraw ensureDraw(Long tournamentId) {
        TournamentDraw draw = loadDraw(tournamentId);
        if (draw != null) {
            if (draw.getDrawOpenedAt() == null) {
                draw.setDrawOpenedAt(LocalDateTime.now());
                draw.setUpdatedAt(LocalDateTime.now());
                drawMapper.updateById(draw);
            }
            return draw;
        }
        LocalDateTime now = LocalDateTime.now();
        draw = new TournamentDraw();
        draw.setTournamentId(tournamentId);
        draw.setDrawPool(DrawPool.KNOCKOUT.name());
        draw.setDrawType("RANDOM");
        draw.setGroupCount(1);
        draw.setStatus("IN_PROGRESS");
        draw.setDrawOpenedAt(now);
        draw.setCreatedAt(now);
        draw.setUpdatedAt(now);
        drawMapper.insert(draw);
        return draw;
    }

    private static final class Board {
        int bracketSize;
        int qualifyCount;
        int shownQualify;
        boolean qualifierDraw;
        List<KnockoutFirstRoundDrawPlan.Pot> pots;
        Map<Integer, Long> userByRank;
        Map<Long, Integer> rankByUser;
        Set<Integer> participantRanks;
        Map<Integer, Integer> prizeByRank;
        Map<Long, Integer> prizeByUser;
        Map<Long, Boolean> autoByUser;
    }
}
