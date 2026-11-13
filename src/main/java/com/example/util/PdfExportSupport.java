package com.example.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
     * 开局后手标记：PNG 小图标（不用 emoji/SVG）。
     * openhtmltopdf 仅引入 pdfbox 时对 SVG data-URI 常不绘制；签名已证明 PNG data-URI 可用。
     */
    public static String buildHammerIconDataUri() {
        try {
            int size = 28;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                // 锤头
                g.setColor(new Color(0x5D4037));
                g.fill(new RoundRectangle2D.Float(10f, 3f, 15f, 9f, 2.5f, 2.5f));
                g.setColor(new Color(0x3E2723));
                g.fill(new RoundRectangle2D.Float(11f, 5f, 5f, 5f, 1.5f, 1.5f));
                // 锤柄
                g.setColor(new Color(0xA1887F));
                g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(12, 12, 5, 24);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 若 model 中尚未设置，则补充 {@code logoDataUri}、{@code exportedAt}、{@code hammerIconDataUri}。
     */
    public static void addStandardPdfHeaderFields(Map<String, Object> model) {
        if (model == null) {
            return;
        }
        model.putIfAbsent("logoDataUri", buildLogoDataUri());
        model.putIfAbsent("exportedAt", exportedAtShanghai());
        model.putIfAbsent("hammerIconDataUri", buildHammerIconDataUri());
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
