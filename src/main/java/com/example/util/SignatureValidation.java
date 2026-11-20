package com.example.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * 电子签名非空校验：拒绝空白画布 PNG 与空文字签名。
 */
public final class SignatureValidation {

    public static final String TEXT_SIG_PREFIX = "TEXT_SIG:";

    private SignatureValidation() {
    }

    /**
     * @return 修剪后的签名；非法时抛 {@link IllegalArgumentException}
     */
    public static String requireNonBlank(String signature) {
        return requireNonBlank(signature, "必须提供电子签名（手写或点击「使用文字签名」并呈现到画板）");
    }

    public static String requireNonBlank(String signature, String emptyMessage) {
        String sig = signature == null ? "" : signature.trim();
        if (sig.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (sig.startsWith(TEXT_SIG_PREFIX)) {
            if (sig.substring(TEXT_SIG_PREFIX.length()).trim().isEmpty()) {
                throw new IllegalArgumentException("文字签名不能为空，请手写或点击「使用文字签名」");
            }
            return sig;
        }
        if (sig.startsWith("data:image/")) {
            if (isVisuallyBlankDataUrl(sig)) {
                throw new IllegalArgumentException("签名不能为空白，请手写或使用文字签名并呈现到画板");
            }
            return sig;
        }
        // 兼容历史纯 base64
        if (sig.length() > 80 && !sig.contains(" ") && !sig.contains("SYSTEM_")) {
            return sig;
        }
        throw new IllegalArgumentException(emptyMessage);
    }

    /**
     * 采样像素：若几乎全为近白则视为空白签名图。
     * 解码失败时不误拦（避免损坏历史数据无法展示路径；提交端仍有前端校验）。
     */
    public static boolean isVisuallyBlankDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return true;
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || comma + 1 >= dataUrl.length()) {
            return true;
        }
        String b64 = dataUrl.substring(comma + 1).trim();
        if (b64.length() < 24) {
            return true;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                return false;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w < 2 || h < 2) {
                return true;
            }
            int stepX = Math.max(1, w / 40);
            int stepY = Math.max(1, h / 40);
            int dark = 0;
            int samples = 0;
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int argb = img.getRGB(x, y);
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >>> 16) & 0xFF;
                    int g = (argb >>> 8) & 0xFF;
                    int b = argb & 0xFF;
                    samples++;
                    if (a > 8 && (r < 245 || g < 245 || b < 245)) {
                        dark++;
                        if (dark >= 3) {
                            return false;
                        }
                    }
                }
            }
            return samples == 0 || dark < 3;
        } catch (IllegalArgumentException | java.io.IOException ex) {
            return false;
        }
    }
}
