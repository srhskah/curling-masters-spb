package com.example.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 全站 PDF 导出共用：Logo 内联、导出时间、HTTP 附件响应。
 * 各业务控制器组装自己的 Thymeleaf model 后，调用
 * {@link #addStandardPdfHeaderFields(Map)} 再 {@code renderPdf}，最后用 {@link #attachmentPdf(byte[], String)} 返回。
 */
public final class PdfExportSupport {

    private PdfExportSupport() {
    }

    /**
     * 横条 Logo。部署时图片在挂载的 {@code /app/static}，不在 WAR 的 classpath 里，
     * 只读 classpath 会让导出 PDF 的 Logo 整段消失。
     */
    public static String buildLogoDataUri() {
        byte[] bytes = readLogoBytes();
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] readLogoBytes() {
        String fileName = "Logo Trsp Stripe.png";
        List<Path> candidates = List.of(
                Path.of("/app/static/images", fileName),
                Path.of("static/images", fileName),
                Path.of(System.getProperty("user.dir", "."), "static/images", fileName)
        );
        for (Path path : candidates) {
            try {
                if (Files.isRegularFile(path)) {
                    return Files.readAllBytes(path);
                }
            } catch (Exception ignored) {
            }
        }
        try {
            ClassPathResource r = new ClassPathResource("static/images/" + fileName);
            if (r.exists()) {
                return r.getInputStream().readAllBytes();
            }
        } catch (Exception ignored) {
        }
        return null;
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
