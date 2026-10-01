package com.example.controller;

import com.example.service.TournamentCompetitionConfigExportAssembler;
import com.example.service.impl.RankingExportPdfService;
import com.example.util.PdfExportSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 赛事配置 PDF 导出（无权限限制，所有用户均可访问）
 */
@RestController
@RequestMapping("/tournament/detail")
public class TournamentCompetitionConfigPdfController {

    @Autowired
    private RankingExportPdfService rankingExportPdfService;
    @Autowired
    private TournamentCompetitionConfigExportAssembler configExportAssembler;
    @Autowired
    private RankingApiController rankingApiController;

    @GetMapping(value = "/{id}/export/competition-config/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        Map<String, Object> data = configExportAssembler.assemble(id);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> model = new LinkedHashMap<>();
        String tournamentEditionTitle = buildTournamentEditionTitle(id);
        model.put("title", tournamentEditionTitle + "-赛事配置");
        model.putAll(data);
        PdfExportSupport.addStandardPdfHeaderFields(model);
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-tournament-competition-config", model);
        return PdfExportSupport.attachmentPdf(pdfBytes, tournamentEditionTitle + "-赛事配置.pdf");
    }

    private String buildTournamentEditionTitle(Long tournamentId) {
        Map<String, Object> data = rankingApiController.getTournamentRanking(tournamentId);
        String seasonLabel = data.get("seasonLabel") != null ? data.get("seasonLabel").toString() : "赛季";
        String levelName = data.get("levelName") != null ? data.get("levelName").toString() : "赛事等级";
        Integer edition = null;
        try {
            edition = data.get("edition") instanceof Number ? ((Number) data.get("edition")).intValue() : null;
        } catch (Exception ignored) {
        }
        return seasonLabel + "-" + levelName + "-" + (edition == null ? "?" : edition);
    }
}