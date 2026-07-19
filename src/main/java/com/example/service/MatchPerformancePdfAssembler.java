package com.example.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.Match;
import com.example.entity.MatchAcceptance;
import com.example.entity.MatchScoreEditLog;
import com.example.entity.SetScore;
import com.example.entity.User;
import com.example.mapper.MatchAcceptanceMapper;
import com.example.mapper.MatchScoreEditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 组装「单场战绩 PDF」所需的 {@code matchDetails} 中单条结构，供
 * {@link com.example.controller.RankingApiController#getMatchPerformance}、
 * {@link com.example.controller.RankingExportPdfController} H2H 导出等复用。
 */
@Service
public class MatchPerformancePdfAssembler {

    @Autowired private IMatchService matchService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private UserService userService;
    @Autowired private MatchAcceptanceMapper matchAcceptanceMapper;
    @Autowired private MatchScoreEditLogMapper matchScoreEditLogMapper;

    /**
     * 与历史 API 行为一致：签名在 PDF 中尽量以内联 data URI 展示。
     */
    public static String normalizeSignatureForPdf(String signature) {
        if (signature == null) {
            return "";
        }
        String raw = signature.trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.startsWith("data:image/")) {
            return raw;
        }
        if (!raw.contains(":") && !raw.contains("/") && raw.length() > 80 && raw.matches("^[A-Za-z0-9+/=]+$")) {
            return "data:image/png;base64," + raw;
        }
        return raw;
    }

    /**
     * @return 与 {@code getMatchPerformance} 中 {@code matchDetails[0]} 同结构的 Map；场次不存在时返回 {@code null}
     */
    public LinkedHashMap<String, Object> buildDetailMapForMatchId(Long matchId) {
        if (matchId == null) {
            return null;
        }
        Match m = matchService.getById(matchId);
        if (m == null) {
            return null;
        }
        return buildDetailMap(m);
    }

    public LinkedHashMap<String, Object> buildDetailMap(Match m) {
        List<SetScore> ss = setScoreService.lambdaQuery()
                .eq(SetScore::getMatchId, m.getId())
                .orderByAsc(SetScore::getSetNumber)
                .list();
        int t1 = ss.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
        int t2 = ss.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
        User u1 = m.getPlayer1Id() == null ? null : userService.getById(m.getPlayer1Id());
        User u2 = m.getPlayer2Id() == null ? null : userService.getById(m.getPlayer2Id());
        String p1n = u1 != null && u1.getUsername() != null && !u1.getUsername().isBlank() ? u1.getUsername() : "待定";
        String p2n = u2 != null && u2.getUsername() != null && !u2.getUsername().isBlank() ? u2.getUsername() : "待定";

        List<Map<String, Object>> sets = new ArrayList<>();
        for (SetScore s : ss) {
            String p1 = Boolean.TRUE.equals(s.getPlayer1IsX()) ? "X" : String.valueOf(s.getPlayer1Score() == null ? 0 : s.getPlayer1Score());
            String p2 = Boolean.TRUE.equals(s.getPlayer2IsX()) ? "X" : String.valueOf(s.getPlayer2Score() == null ? 0 : s.getPlayer2Score());
            String hammer = Objects.equals(s.getHammerPlayerId(), m.getPlayer1Id()) ? p1n
                    : (Objects.equals(s.getHammerPlayerId(), m.getPlayer2Id()) ? p2n : "-");
            LinkedHashMap<String, Object> setRow = new LinkedHashMap<>();
            setRow.put("setNumber", s.getSetNumber());
            setRow.put("player1ScoreText", p1);
            setRow.put("player2ScoreText", p2);
            setRow.put("hammer", hammer);
            sets.add(setRow);
        }

        List<Map<String, Object>> accepts = new ArrayList<>();
        for (MatchAcceptance a : matchAcceptanceMapper.selectList(
                Wrappers.<MatchAcceptance>lambdaQuery()
                        .eq(MatchAcceptance::getMatchId, m.getId())
                        .orderByAsc(MatchAcceptance::getAcceptedAt))) {
            User au = a.getUserId() == null ? null : userService.getById(a.getUserId());
            String un = au != null && au.getUsername() != null ? au.getUsername() : "未知";
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("username", un);
            row.put("signature", normalizeSignatureForPdf(a.getSignature()));
            row.put("acceptedAt", a.getAcceptedAt());
            accepts.add(row);
        }

        List<Map<String, Object>> editLogs = new ArrayList<>();
        for (MatchScoreEditLog l : matchScoreEditLogMapper.selectList(
                Wrappers.<MatchScoreEditLog>lambdaQuery()
                        .eq(MatchScoreEditLog::getMatchId, m.getId())
                        .orderByDesc(MatchScoreEditLog::getEditedAt))) {
            User eu = l.getEditorUserId() == null ? null : userService.getById(l.getEditorUserId());
            String editor = eu != null && eu.getUsername() != null ? eu.getUsername() : "未知";
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("setNumber", l.getSetNumber());
            row.put("editorUsername", editor);
            row.put("oldScore", (Boolean.TRUE.equals(l.getOldPlayer1IsX()) ? "X" : String.valueOf(l.getOldPlayer1Score() == null ? 0 : l.getOldPlayer1Score()))
                    + ":" +
                    (Boolean.TRUE.equals(l.getOldPlayer2IsX()) ? "X" : String.valueOf(l.getOldPlayer2Score() == null ? 0 : l.getOldPlayer2Score())));
            row.put("newScore", (Boolean.TRUE.equals(l.getNewPlayer1IsX()) ? "X" : String.valueOf(l.getNewPlayer1Score() == null ? 0 : l.getNewPlayer1Score()))
                    + ":" +
                    (Boolean.TRUE.equals(l.getNewPlayer2IsX()) ? "X" : String.valueOf(l.getNewPlayer2Score() == null ? 0 : l.getNewPlayer2Score())));
            row.put("editedAt", l.getEditedAt());
            editLogs.add(row);
        }

        LinkedHashMap<String, Object> matchDetail = new LinkedHashMap<>();
        matchDetail.put("matchId", m.getId());
        matchDetail.put("phaseCode", m.getPhaseCode());
        matchDetail.put("category", m.getCategory());
        matchDetail.put("round", m.getRound());
        matchDetail.put("player1Id", m.getPlayer1Id());
        matchDetail.put("player2Id", m.getPlayer2Id());
        matchDetail.put("player1Name", p1n);
        matchDetail.put("player2Name", p2n);
        matchDetail.put("totalText", t1 + ":" + t2);
        matchDetail.put("player1Total", t1);
        matchDetail.put("player2Total", t2);
        matchDetail.put("sets", sets);
        matchDetail.put("acceptances", accepts);
        matchDetail.put("editLogs", editLogs);
        return matchDetail;
    }
}
