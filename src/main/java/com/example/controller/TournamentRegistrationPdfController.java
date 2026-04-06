package com.example.controller;

import com.example.service.TournamentRegistrationExportAssembler;
import com.example.service.impl.RankingExportPdfService;
import com.example.util.PdfExportSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报名接龙 PDF（需登录；不放 /ranking/export/pdf 以免被匿名访问）。
 */
@RestController
@RequestMapping("/tournament/registration")
public class TournamentRegistrationPdfController {

    @Autowired
    private RankingExportPdfService rankingExportPdfService;
    @Autowired
    private TournamentRegistrationExportAssembler tournamentRegistrationExportAssembler;
    @Autowired
    private RankingApiController rankingApiController;

    @GetMapping(value = "/{id}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        Map<String, Object> data = tournamentRegistrationExportAssembler.assemble(id, LocalDateTime.now());
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> model = new LinkedHashMap<>();
        String tournamentEditionTitle = buildTournamentEditionTitle(id);
        model.put("title", tournamentEditionTitle + "-报名接龙");
        model.putAll(data);
        PdfExportSupport.addStandardPdfHeaderFields(model);
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-tournament-registration", model);
        return PdfExportSupport.attachmentPdf(pdfBytes, tournamentEditionTitle + "-报名接龙.pdf");
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
