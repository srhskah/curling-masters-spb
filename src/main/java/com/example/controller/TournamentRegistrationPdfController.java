package com.example.controller;

import com.example.service.TournamentRegistrationExportAssembler;
import com.example.service.impl.RankingExportPdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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

    @GetMapping(value = "/{id}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        Map<String, Object> data = tournamentRegistrationExportAssembler.assemble(id, LocalDateTime.now());
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> model = new LinkedHashMap<>();
        Object level = data.get("levelCode");
        Object tid = data.get("tournamentId");
        model.put("title", data.get("documentTitle") + " · " + level + " · ID " + tid);
        model.put("logoDataUri", buildLogoDataUri());
        model.put("exportedAt", nowExportedAt());
        model.putAll(data);
        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-tournament-registration", model);
        String levelSafe = level != null ? level.toString().replaceAll("[^a-zA-Z0-9\\-_]", "_") : "tournament";
        return toPdfResponse(pdfBytes, "报名接龙-" + levelSafe + "-" + id + ".pdf");
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

    private static ResponseEntity<byte[]> toPdfResponse(byte[] pdfBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
