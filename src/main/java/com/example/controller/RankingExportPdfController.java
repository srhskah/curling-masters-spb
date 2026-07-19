package com.example.controller;

import com.example.dto.*;
import com.example.dto.h2h.H2hLevelNode;
import com.example.dto.h2h.H2hMatchRow;
import com.example.dto.h2h.H2hPayload;
import com.example.dto.h2h.H2hPlayerExportStats;
import com.example.dto.h2h.H2hSeasonNode;
import com.example.dto.h2h.H2hSeriesNode;
import com.example.entity.Season;
import com.example.entity.User;
import com.example.service.HeadToHeadQueryService;
import com.example.service.ITournamentCompetitionService;
import com.example.service.MatchPerformancePdfAssembler;
import com.example.service.RankingService;
import com.example.service.SeasonService;
import com.example.service.TournamentMedalStandingsService;
import com.example.service.UserService;
import com.example.service.impl.RankingExportPdfService;
import com.example.util.PdfExportSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/ranking/export/pdf")
public class RankingExportPdfController {

    @Autowired private RankingService rankingService;
    @Autowired private SeasonService seasonService;
    @Autowired private RankingExportPdfService rankingExportPdfService;
    @Autowired private RankingApiController rankingApiController;
    @Autowired private ITournamentCompetitionService tournamentCompetitionService;
    @Autowired private UserService userService;
    @Autowired private TournamentMedalStandingsService tournamentMedalStandingsService;
    @Autowired private HeadToHeadQueryService headToHeadQueryService;
    @Autowired private MatchPerformancePdfAssembler matchPerformancePdfAssembler;

    private static LinkedHashMap<String, Object> basePdfModel() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        PdfExportSupport.addStandardPdfHeaderFields(m);
        return m;
    }

    @GetMapping(value = "/total", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTotalPdf(
            @RequestParam(required = false) String limit
    ) {
        Integer parsedLimit = parseLimit(limit);
        List<RankingEntry> entries = rankingService.getTotalRanking(parsedLimit);
        List<RankingListEntryDto> totalRanking = toRankedList(entries);

        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", "总排名");
        model.put("totalRanking", totalRanking);
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-total-ranking", model);

        return PdfExportSupport.attachmentPdf(pdfBytes, "总排名.pdf");
    }

    @GetMapping(value = "/season/{seasonId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportSeasonPdf(
            @PathVariable Long seasonId,
            @RequestParam(required = false) String limit
    ) {
        Integer parsedLimit = parseLimit(limit);
        Season season = seasonService.getById(seasonId);

        List<RankingEntry> entries = rankingService.getSeasonRanking(seasonId, parsedLimit);
        List<RankingListEntryDto> seasonRanking = toRankedList(entries);

        String seasonLabel = season == null
                ? ("赛季 " + seasonId)
                : (season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"));

        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", seasonLabel + " 赛季排名");
        model.put("seasonRanking", seasonRanking);
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-season-ranking", model);

        return PdfExportSupport.attachmentPdf(pdfBytes, seasonLabel + "-赛季排名.pdf");
    }

    @GetMapping(value = "/multi", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportMultiPdf(
            @RequestParam Long seasonId,
            @RequestParam Long seriesId,
            @RequestParam String totalLimit,
            @RequestParam String seasonLimit
    ) {
        Integer parsedTotalLimit = parseLimit(totalLimit);
        Integer parsedSeasonLimit = parseLimit(seasonLimit);

        List<RankingEntry> totalEntries = rankingService.getTotalRanking(parsedTotalLimit);
        List<RankingListEntryDto> totalRanking = toRankedList(totalEntries);

        List<RankingEntry> seasonEntries = rankingService.getSeasonRanking(seasonId, parsedSeasonLimit);
        List<RankingListEntryDto> seasonRanking = toRankedList(seasonEntries);

        SeriesTournamentRankingDto seriesTournamentRanking = rankingApiController.getSeriesTournamentRankings(seriesId);

        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", "排名导出");
        model.put("totalRanking", totalRanking);
        model.put("seasonRanking", seasonRanking);
        model.put("seriesTournamentRanking", seriesTournamentRanking);
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-multi-ranking", model);

        return PdfExportSupport.attachmentPdf(pdfBytes, "排名-多合一.pdf");
    }

    /**
     * 系列多合一PDF（仅导出本系列数据，按指定表格格式）
     */
    @GetMapping(value = "/series/{seriesId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportSeriesSummaryPdf(@PathVariable Long seriesId) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> summary = rankingApiController.getSeriesPointsSummary(seriesId);
        String seasonLabel = summary.get("seasonLabel") != null ? summary.get("seasonLabel").toString() : "";
        String seriesName = summary.get("seriesName") != null ? summary.get("seriesName").toString() : "";
        String seriesLabel = summary.get("seriesLabel") != null ? summary.get("seriesLabel").toString() : "";

        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", "系列积分汇总 - " + seriesLabel);
        model.put("showFinalRank", summary.get("showFinalRank"));
        model.put("columns", summary.get("columns"));
        model.put("rows", summary.get("rows"));
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-series-summary", model);

        String filename = (seasonLabel.isEmpty() ? "赛季" : seasonLabel) + "-" +
                (seriesName.isEmpty() ? "系列" : seriesName) +
                "-积分汇总.pdf";
        return PdfExportSupport.attachmentPdf(pdfBytes, filename);
    }

    /**
     * 单个赛事排名PDF
     */
    @GetMapping(value = "/tournament/{tournamentId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentRankingPdf(
            @PathVariable Long tournamentId,
            @RequestParam(defaultValue = "true") boolean includeMatchDetails
    ) {
        java.util.Map<String, Object> data = rankingApiController.getTournamentRanking(tournamentId);
        String seasonLabel = data.get("seasonLabel") != null ? data.get("seasonLabel").toString() : "";
        String levelName = data.get("levelName") != null ? data.get("levelName").toString() : "";
        Integer edition = null;
        try { edition = data.get("edition") instanceof Number ? ((Number) data.get("edition")).intValue() : null; } catch (Exception ignored) {}
        Integer seasonYear = null;
        try { seasonYear = data.get("seasonYear") instanceof Number ? ((Number) data.get("seasonYear")).intValue() : null; } catch (Exception ignored) {}

        String title;
        boolean isSeasonFinalAorB = levelName.contains("赛季总决赛（A）") || levelName.contains("赛季总决赛(A)") ||
                levelName.contains("赛季总决赛（B）") || levelName.contains("赛季总决赛(B)");
        boolean isYearFinal = levelName.contains("年终总决赛");

        if (isYearFinal && seasonYear != null) {
            title = seasonYear + "-" + levelName;
        } else if (isSeasonFinalAorB) {
            title = (seasonLabel.isEmpty() ? "赛季" : seasonLabel) + "-" + levelName;
        } else {
            title = (seasonLabel.isEmpty() ? "赛季" : seasonLabel) + "-" + levelName +
                    (edition != null ? ("-" + edition) : "") +
                    "-排名";
        }

        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", title);
        model.put("rankings", data.get("rankings"));
        model.put("matchDetails", data.get("matchDetails"));
        model.put("includeMatchDetails", includeMatchDetails);
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-tournament-ranking", model);

        return PdfExportSupport.attachmentPdf(pdfBytes, title + ".pdf");
    }

    @GetMapping(value = "/tournament/{tournamentId}/group-ranking", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentGroupRankingPdf(@PathVariable Long tournamentId) {
        String editionTitle = buildTournamentEditionTitle(tournamentId);
        java.util.Map<String, Object> data = rankingApiController.getTournamentGroupRanking(tournamentId);
        byte[] pdfBytes = renderGroupRankingPdf(
                editionTitle + "-小组赛排名与对阵明细",
                data.get("groups"),
                data.get("pseudoGroups"),
                data.get("groupMatches")
        );
        return PdfExportSupport.attachmentPdf(pdfBytes, editionTitle + "-小组赛排名与对阵明细.pdf");
    }

    @GetMapping(value = "/tournament/{tournamentId}/group-overall-ranking", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentGroupOverallRankingPdf(@PathVariable Long tournamentId) {
        String editionTitle = buildTournamentEditionTitle(tournamentId);
        java.util.Map<String, Object> data = rankingApiController.getTournamentGroupOverallRanking(tournamentId);
        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", editionTitle + "-小组赛总排名");
        model.put("rows", data.get("overallRanking"));
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-tournament-group-overall-ranking", model);
        return PdfExportSupport.attachmentPdf(pdfBytes, editionTitle + "-小组赛总排名.pdf");
    }

    @GetMapping(value = "/tournament/{tournamentId}/group/{groupId}/ranking", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentOneGroupRankingPdf(@PathVariable Long tournamentId, @PathVariable Long groupId) {
        String editionTitle = buildTournamentEditionTitle(tournamentId);
        java.util.Map<String, Object> data = rankingApiController.getTournamentOneGroupRanking(tournamentId, groupId);
        String groupName = data.get("groupName") != null ? data.get("groupName").toString() : "分组";
        byte[] pdfBytes = renderGroupRankingPdf(
                editionTitle + "-" + groupName + "排名与对阵明细",
                java.util.List.of(java.util.Map.of("groupName", groupName, "ranking", data.get("ranking"))),
                data.get("pseudoGroups"),
                data.get("matches")
        );
        return PdfExportSupport.attachmentPdf(pdfBytes, editionTitle + "-" + groupName + "-排名与对阵明细.pdf");
    }

    @GetMapping(value = "/tournament/{tournamentId}/user/{userId}/performance", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentUserPerformancePdf(@PathVariable Long tournamentId, @PathVariable Long userId) {
        String editionTitle = buildTournamentEditionTitle(tournamentId);
        java.util.Map<String, Object> data = rankingApiController.getTournamentUserPerformance(tournamentId, userId);
        String username = data.get("username") != null ? data.get("username").toString() : ("用户" + userId);
        String title = editionTitle + "-" + username + "-赛事战绩";
        byte[] pdfBytes = renderPerformancePdf(title, data.get("matchDetails"));
        return PdfExportSupport.attachmentPdf(pdfBytes, title + ".pdf");
    }

    /**
     * H2H 页面导出：版式与单场战绩 PDF 一致（LOGO、字体、表头风格），A4 纵向，由模板 {@code pdf/pdf-h2h-export} 控制。
     *
     * @param statsPerspective 双方 H2H 时：1 = userId1 对 userId2 视角；2 = userId2 对 userId1（与页面「交换视角」一致）
     */
    @GetMapping(value = "/h2h", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportH2hPdf(
            @RequestParam Long userId1,
            @RequestParam(required = false) Long userId2,
            @RequestParam(defaultValue = "1") int statsPerspective
    ) {
        if (userId2 != null && userId2.equals(userId1)) {
            return ResponseEntity.badRequest().build();
        }
        int pv = statsPerspective == 2 ? 2 : 1;

        H2hPayload payload = headToHeadQueryService.buildPayload(userId1, userId2);
        User u1 = userService.getById(userId1);
        User u2 = userId2 != null ? userService.getById(userId2) : null;
        String name1 = u1 != null && u1.getUsername() != null && !u1.getUsername().isBlank()
                ? u1.getUsername()
                : ("用户" + userId1);
        String name2 = u2 != null && u2.getUsername() != null && !u2.getUsername().isBlank()
                ? u2.getUsername()
                : (userId2 != null ? ("用户" + userId2) : null);

        H2hPlayerExportStats stats = resolveH2hPdfStats(payload, userId2, pv);

        String title;
        String subtitle;
        String statsIntro;
        if (userId2 == null) {
            title = "H2H 交手记录 - " + name1;
            subtitle = "导出范围：该选手在系统中的全部历史场次（与 H2H 页面列表一致）";
            statsIntro = "以下汇总为该选手在上述场次的合计统计。";
        } else {
            title = "H2H 交手记录 - " + name1 + " vs " + name2;
            subtitle = "导出范围：双方直接交手场次（与 H2H 页面列表一致）";
            if (pv == 1) {
                statsIntro = "汇总视角：" + name1 + " 对 " + name2 + "（胜/平/负为前者相对后者）。";
            } else {
                statsIntro = "汇总视角：" + name2 + " 对 " + name1 + "（胜/平/负为前者相对后者）。";
            }
        }

        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", title);
        model.put("subtitle", subtitle);
        model.put("statsIntro", statsIntro);
        model.put("stats", stats);
        model.put("matchDetails", buildH2hMatchDetailsForPdf(payload, matchPerformancePdfAssembler));

        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-h2h-export", model);
        String filename = userId2 == null
                ? ("H2H-" + sanitizePdfFilenameSegment(name1) + ".pdf")
                : ("H2H-" + sanitizePdfFilenameSegment(name1) + "-vs-" + sanitizePdfFilenameSegment(name2 != null ? name2 : String.valueOf(userId2)) + ".pdf");
        return PdfExportSupport.attachmentPdf(pdfBytes, filename);
    }

    private static String sanitizePdfFilenameSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "user";
        }
        String t = raw.replace('/', '_').replace('\\', '_').replace(':', '_').trim();
        return t.isEmpty() ? "user" : t;
    }

    private static H2hPlayerExportStats resolveH2hPdfStats(H2hPayload payload, Long userId2, int perspective) {
        if (payload == null) {
            return H2hPlayerExportStats.empty();
        }
        if (userId2 == null) {
            return payload.soloStats() != null ? payload.soloStats() : H2hPlayerExportStats.empty();
        }
        if (perspective == 2) {
            return payload.h2hStatsUser2VsUser1() != null ? payload.h2hStatsUser2VsUser1() : H2hPlayerExportStats.empty();
        }
        return payload.h2hStatsUser1VsUser2() != null ? payload.h2hStatsUser1VsUser2() : H2hPlayerExportStats.empty();
    }

    private static List<LinkedHashMap<String, Object>> buildH2hMatchDetailsForPdf(
            H2hPayload payload,
            MatchPerformancePdfAssembler assembler) {
        List<LinkedHashMap<String, Object>> out = new ArrayList<>();
        if (payload == null || payload.seasons() == null) {
            return out;
        }
        for (H2hSeasonNode season : payload.seasons()) {
            if (season == null || season.series() == null) {
                continue;
            }
            for (H2hSeriesNode ser : season.series()) {
                if (ser == null || ser.levels() == null) {
                    continue;
                }
                for (H2hLevelNode lvl : ser.levels()) {
                    if (lvl == null || lvl.matches() == null) {
                        continue;
                    }
                    for (H2hMatchRow row : lvl.matches()) {
                        if (row == null) {
                            continue;
                        }
                        LinkedHashMap<String, Object> detail = assembler.buildDetailMapForMatchId(row.matchId());
                        if (detail == null) {
                            continue;
                        }
                        detail.put("h2hPathLine", buildH2hPathLine(season, ser, lvl, row));
                        out.add(detail);
                    }
                }
            }
        }
        return out;
    }

    private static String buildH2hPathLine(H2hSeasonNode season, H2hSeriesNode ser, H2hLevelNode lvl, H2hMatchRow row) {
        StringBuilder sb = new StringBuilder();
        sb.append(nz(season.seasonLabel(), "—")).append(" · ");
        sb.append(nz(ser.seriesLabel(), "—")).append(" · ");
        sb.append(nz(lvl.levelName(), "—"));
        if (row.tournamentTitle() != null && !row.tournamentTitle().isBlank()) {
            sb.append(" | ").append(row.tournamentTitle());
        }
        sb.append(" | 场次 #").append(row.matchId());
        return sb.toString();
    }

    private static String nz(String s, String d) {
        return s != null && !s.isBlank() ? s : d;
    }

    @GetMapping(value = "/match/{matchId}/performance", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportSingleMatchPerformancePdf(@PathVariable Long matchId) {
        java.util.Map<String, Object> data = rankingApiController.getMatchPerformance(matchId);
        Long tournamentId = null;
        try {
            tournamentId = data.get("tournamentId") instanceof Number ? ((Number) data.get("tournamentId")).longValue() : null;
        } catch (Exception ignored) {
        }
        String editionTitle = tournamentId != null ? buildTournamentEditionTitle(tournamentId) : null;
        String rawTitle = data.get("title") != null ? data.get("title").toString() : ("单场比赛战绩-" + matchId);
        String title = (editionTitle != null && !editionTitle.isBlank()) ? (editionTitle + "-" + rawTitle) : rawTitle;
        byte[] pdfBytes = renderPerformancePdf(title, data.get("matchDetails"));
        return PdfExportSupport.attachmentPdf(pdfBytes, title + ".pdf");
    }

    @GetMapping(value = "/tournament/{tournamentId}/disqualification", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentDisqualificationPdf(@PathVariable Long tournamentId) {
        String title = buildTournamentEditionTitle(tournamentId) + "-取消资格记录";
        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", title);
        model.put("rows", tournamentCompetitionService.listGroupDisqualifications(tournamentId));
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-tournament-disqualification", model);
        return PdfExportSupport.attachmentPdf(pdfBytes, title + ".pdf");
    }

    /**
     * 用户详情页：各级别奖牌汇总、奖牌明细、历届赛事名次（规则与全站奖牌榜一致）。
     */
    @GetMapping(value = "/user/{userId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportUserCareerPdf(@PathVariable Long userId) {
        User u = userService.getById(userId);
        if (u == null) {
            return ResponseEntity.notFound().build();
        }
        LinkedHashMap<String, Object> model = basePdfModel();
        String title = u.getUsername() + " - 生涯战绩";
        model.put("title", title);
        model.put("username", u.getUsername());
        model.put("userId", userId);
        model.put("medalsByLevel", tournamentMedalStandingsService.summarizeUserMedalsByLevel(userId));
        model.put("medalEvents", tournamentMedalStandingsService.listUserMedalEvents(userId));
        model.put("tournamentHistory", tournamentMedalStandingsService.buildUserTournamentPlacementHistory(userId));
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-user-profile", model);
        return PdfExportSupport.attachmentPdf(pdfBytes, u.getUsername() + "-生涯战绩.pdf");
    }

    private byte[] renderGroupRankingPdf(String title, Object groups, Object pseudoGroups, Object groupMatches) {
        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", title);
        model.put("groups", groups);
        model.put("pseudoGroups", pseudoGroups);
        model.put("groupMatches", groupMatches);
        return rankingExportPdfService.renderPdf("pdf/pdf-tournament-group-ranking", model);
    }

    private byte[] renderPerformancePdf(String title, Object matchDetails) {
        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", title);
        model.put("matchDetails", matchDetails);
        return rankingExportPdfService.renderPdf("pdf/pdf-user-match-performance", model);
    }

    private String buildTournamentEditionTitle(Long tournamentId) {
        java.util.Map<String, Object> data = rankingApiController.getTournamentRanking(tournamentId);
        String seasonLabel = data.get("seasonLabel") != null ? data.get("seasonLabel").toString() : "赛季";
        String levelName = data.get("levelName") != null ? data.get("levelName").toString() : "赛事等级";
        Integer edition = null;
        try {
            edition = data.get("edition") instanceof Number ? ((Number) data.get("edition")).intValue() : null;
        } catch (Exception ignored) {
        }
        return seasonLabel + "-" + levelName + "-" + (edition == null ? "?" : edition);
    }

    private static Integer parseLimit(String limit) {
        if (limit == null) return null;
        String t = limit.trim();
        if (t.isEmpty()) return null;
        if ("all".equalsIgnoreCase(t)) return null;
        if ("24".equalsIgnoreCase(t)) return 24;
        try {
            int v = Integer.parseInt(t);
            return v > 0 ? v : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<RankingListEntryDto> toRankedList(List<RankingEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        List<RankingListEntryDto> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankingEntry e = entries.get(i);
            result.add(new RankingListEntryDto(i + 1, e.getUserId(), e.getUsername(), e.getPoints()));
        }
        return result;
    }
}
