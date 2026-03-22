package com.example.controller;

import com.example.dto.*;
import com.example.entity.Season;
import com.example.service.RankingService;
import com.example.service.SeasonService;
import com.example.service.impl.RankingExportPdfService;
import com.example.controller.RankingApiController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/ranking/export/pdf")
public class RankingExportPdfController {

    @Autowired private RankingService rankingService;
    @Autowired private SeasonService seasonService;
    @Autowired private RankingExportPdfService rankingExportPdfService;
    @Autowired private RankingApiController rankingApiController;

    @GetMapping(value = "/total", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTotalPdf(
            @RequestParam(required = false) String limit
    ) {
        Integer parsedLimit = parseLimit(limit);
        List<RankingEntry> entries = rankingService.getTotalRanking(parsedLimit);
        List<RankingListEntryDto> totalRanking = toRankedList(entries);

        byte[] pdfBytes = rankingExportPdfService.renderPdf(
                "pdf/pdf-total-ranking",
                java.util.Map.of(
                        "title", "总排名",
                        "logoDataUri", buildLogoDataUri(),
                        "exportedAt", nowExportedAt(),
                        "totalRanking", totalRanking
                )
        );

        return toPdfResponse(pdfBytes, "总排名.pdf");
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

        byte[] pdfBytes = rankingExportPdfService.renderPdf(
                "pdf/pdf-season-ranking",
                java.util.Map.of(
                        "title", seasonLabel + " 赛季排名",
                        "logoDataUri", buildLogoDataUri(),
                        "exportedAt", nowExportedAt(),
                        "seasonRanking", seasonRanking
                )
        );

        return toPdfResponse(pdfBytes, seasonLabel + "-赛季排名.pdf");
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

        byte[] pdfBytes = rankingExportPdfService.renderPdf(
                "pdf/pdf-multi-ranking",
                java.util.Map.of(
                        "title", "排名导出",
                        "logoDataUri", buildLogoDataUri(),
                        "exportedAt", nowExportedAt(),
                        "totalRanking", totalRanking,
                        "seasonRanking", seasonRanking,
                        "seriesTournamentRanking", seriesTournamentRanking
                )
        );

        return toPdfResponse(pdfBytes, "排名-多合一.pdf");
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

        byte[] pdfBytes = rankingExportPdfService.renderPdf(
                "pdf/pdf-series-summary",
                java.util.Map.of(
                        "title", "系列积分汇总 - " + seriesLabel,
                        "logoDataUri", buildLogoDataUri(),
                        "exportedAt", nowExportedAt(),
                        "showFinalRank", summary.get("showFinalRank"),
                        "columns", summary.get("columns"),
                        "rows", summary.get("rows")
                )
        );

        String filename = (seasonLabel.isEmpty() ? "赛季" : seasonLabel) + "-" +
                (seriesName.isEmpty() ? "系列" : seriesName) +
                "-积分汇总.pdf";
        return toPdfResponse(pdfBytes, filename);
    }

    private static String nowExportedAt() {
        ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        return zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String buildLogoDataUri() {
        try {
            ClassPathResource r = new ClassPathResource("static/images/Logo Trsp Stripe.png");
            byte[] bytes = r.getInputStream().readAllBytes();
            String b64 = Base64.getEncoder().encodeToString(bytes);
            return "data:image/png;base64," + b64;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 单个赛事排名PDF
     */
    @GetMapping(value = "/tournament/{tournamentId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentRankingPdf(@PathVariable Long tournamentId) {
        java.util.Map<String, Object> data = rankingApiController.getTournamentRanking(tournamentId);
        String seasonLabel = data.get("seasonLabel") != null ? data.get("seasonLabel").toString() : "";
        String levelName = data.get("levelName") != null ? data.get("levelName").toString() : "";
        String levelCode = data.get("levelCode") != null ? data.get("levelCode").toString() : "";
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

        byte[] pdfBytes = rankingExportPdfService.renderPdf(
                "pdf/pdf-tournament-ranking",
                java.util.Map.of(
                        "title", title,
                        "logoDataUri", buildLogoDataUri(),
                        "exportedAt", nowExportedAt(),
                        "rankings", data.get("rankings"),
                        "matchDetails", data.get("matchDetails")
                )
        );

        return toPdfResponse(pdfBytes, title + ".pdf");
    }

    @GetMapping(value = "/tournament/{tournamentId}/group-ranking", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentGroupRankingPdf(@PathVariable Long tournamentId) {
        java.util.Map<String, Object> data = rankingApiController.getTournamentGroupRanking(tournamentId);
        byte[] pdfBytes = renderGroupRankingPdf(
                "小组赛排名与对阵明细",
                data.get("groups"),
                data.get("pseudoGroups"),
                data.get("groupMatches")
        );
        return toPdfResponse(pdfBytes, "小组赛排名与对阵明细.pdf");
    }

    @GetMapping(value = "/tournament/{tournamentId}/group-overall-ranking", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentGroupOverallRankingPdf(@PathVariable Long tournamentId) {
        java.util.Map<String, Object> data = rankingApiController.getTournamentGroupOverallRanking(tournamentId);
        byte[] pdfBytes = rankingExportPdfService.renderPdf(
                "pdf/pdf-tournament-group-overall-ranking",
                java.util.Map.of(
                        "title", "小组赛总排名",
                        "logoDataUri", buildLogoDataUri(),
                        "exportedAt", nowExportedAt(),
                        "rows", data.get("overallRanking")
                )
        );
        return toPdfResponse(pdfBytes, "小组赛总排名.pdf");
    }

    @GetMapping(value = "/tournament/{tournamentId}/group/{groupId}/ranking", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportTournamentOneGroupRankingPdf(@PathVariable Long tournamentId, @PathVariable Long groupId) {
        java.util.Map<String, Object> data = rankingApiController.getTournamentOneGroupRanking(tournamentId, groupId);
        String groupName = data.get("groupName") != null ? data.get("groupName").toString() : "分组";
        byte[] pdfBytes = renderGroupRankingPdf(
                groupName + " 排名与对阵明细",
                java.util.List.of(java.util.Map.of("groupName", groupName, "ranking", data.get("ranking"))),
                data.get("pseudoGroups"),
                data.get("matches")
        );
        return toPdfResponse(pdfBytes, groupName + "-排名与对阵明细.pdf");
    }

    private byte[] renderGroupRankingPdf(String title, Object groups, Object pseudoGroups, Object groupMatches) {
        return rankingExportPdfService.renderPdf(
                "pdf/pdf-tournament-group-ranking",
                java.util.Map.of(
                        "title", title,
                        "logoDataUri", buildLogoDataUri(),
                        "exportedAt", nowExportedAt(),
                        "groups", groups,
                        "pseudoGroups", pseudoGroups,
                        "groupMatches", groupMatches
                )
        );
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

    private static ResponseEntity<byte[]> toPdfResponse(byte[] pdfBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}

