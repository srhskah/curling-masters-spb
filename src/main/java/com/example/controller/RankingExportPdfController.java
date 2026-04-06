package com.example.controller;

import com.example.dto.*;
import com.example.entity.Season;
import com.example.service.RankingService;
import com.example.service.SeasonService;
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
    public ResponseEntity<byte[]> exportTournamentRankingPdf(@PathVariable Long tournamentId) {
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

    private byte[] renderGroupRankingPdf(String title, Object groups, Object pseudoGroups, Object groupMatches) {
        LinkedHashMap<String, Object> model = basePdfModel();
        model.put("title", title);
        model.put("groups", groups);
        model.put("pseudoGroups", pseudoGroups);
        model.put("groupMatches", groupMatches);
        return rankingExportPdfService.renderPdf("pdf/pdf-tournament-group-ranking", model);
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
