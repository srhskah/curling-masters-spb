package com.example.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

/**
 * 全站 PDF 导出共用：Logo 内联、导出时间、HTTP 附件响应。
 * 各业务控制器组装自己的 Thymeleaf model 后，调用
 * {@link #addStandardPdfHeaderFields(Map)} 再 {@code renderPdf}，最后用 {@link #attachmentPdf(byte[], String)} 返回。
 */
public final class PdfExportSupport {

    private PdfExportSupport() {
    }

    public static String buildLogoDataUri() {
        try {
            ClassPathResource r = new ClassPathResource("static/images/Logo Trsp Stripe.png");
            byte[] bytes = r.getInputStream().readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    public static String exportedAtShanghai() {
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 若 model 中尚未设置，则补充 {@code logoDataUri}、{@code exportedAt}。
     */
    public static void addStandardPdfHeaderFields(Map<String, Object> model) {
        if (model == null) {
            return;
        }
        model.putIfAbsent("logoDataUri", buildLogoDataUri());
        model.putIfAbsent("exportedAt", exportedAtShanghai());
    }

    public static ResponseEntity<byte[]> attachmentPdf(byte[] pdfBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
