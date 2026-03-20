package com.example.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Locale;
import java.util.Map;

@Service
public class RankingExportPdfService {

    private final TemplateEngine templateEngine;

    public RankingExportPdfService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] renderPdf(String templateName, Map<String, Object> model) {
        Context context = new Context(Locale.SIMPLIFIED_CHINESE);
        if (model != null) {
            context.setVariables(model);
        }

        // 先渲染为 HTML，再交给 openhtmltopdf 转 PDF
        String html = templateEngine.process(templateName, context);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // baseUrl 使用 file:/ 以便模板可引用系统字体/本地资源
            builder.withHtmlContent(html, "file:/");
            registerFonts(builder);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF 渲染失败：" + e.getMessage(), e);
        }
    }

    /**
     * 注册字体以支持 Unicode/中文/emoji。
     * - 容器内已安装文泉驿字体（中文）
     * - 尝试注册 noto emoji（若存在则用于 emoji 字符）
     */
    private static void registerFonts(PdfRendererBuilder builder) {
        // WenQuanYi（中文）
        tryUseFont(builder, "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", "WenQuanYi Micro Hei");
        tryUseFont(builder, "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", "WenQuanYi Zen Hei");

        // DejaVu（常见的 Unicode 覆盖；部分环境可显示部分 emoji 符号）
        tryUseFont(builder, "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "DejaVu Sans");
        tryUseFont(builder, "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf", "DejaVu Sans");

        // Emoji（尽力而为：只注册 PDFBox 可加载的单色字体；彩色字体通常不兼容）
        tryUseFont(builder, "/usr/share/fonts/truetype/noto/NotoEmoji-Regular.ttf", "Noto Emoji");
        tryUseFont(builder, "/usr/share/fonts/truetype/noto/NotoEmoji.ttf", "Noto Emoji");
        tryUseFont(builder, "/usr/share/fonts/truetype/noto/NotoSansSymbols2-Regular.ttf", "Noto Emoji");
        tryUseFont(builder, "/usr/share/fonts/truetype/noto/NotoSansSymbols-Regular.ttf", "Noto Emoji");
        tryUseFont(builder, "/usr/share/fonts/truetype/symbola/Symbola.ttf", "Noto Emoji");
        // 注意：NotoColorEmoji.ttf 为 COLR/CPAL 彩色字体，PDFBox/openhtmltopdf 往往无法作为 TrueType 正常加载，
        // 会导致 PDF 渲染 NPE。这里不注册，避免导出失败。
    }

    private static void tryUseFont(PdfRendererBuilder builder, String path, String family) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.isFile()) return;
            builder.useFont(f, family);
        } catch (Exception ignored) {
        }
    }
}

