package com.example.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final String LOGO_FILE_NAME = "Logo Trsp Stripe.png";

    /**
     * 与站点图片同一来源：优先读挂载目录 {@code /app/static/images} 和工程根 {@code static/images}，
     * 再回退到 classpath。
     */
    public static String buildLogoDataUri() {
        byte[] bytes = readLogoBytes();
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] readLogoBytes() {
        String userDir = System.getProperty("user.dir", ".");
        Path[] candidates = new Path[]{
                Path.of("/app/static/images", LOGO_FILE_NAME),
                Path.of(userDir, "static/images", LOGO_FILE_NAME),
                Path.of(userDir, "src/main/resources/static/images", LOGO_FILE_NAME)
        };
        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate)) {
                    return Files.readAllBytes(candidate);
                }
            } catch (Exception ignored) {
            }
        }
        try {
            ClassPathResource resource = new ClassPathResource("static/images/" + LOGO_FILE_NAME);
            if (!resource.exists()) {
                return null;
            }
            try (InputStream in = resource.getInputStream()) {
                return in.readAllBytes();
            }
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
