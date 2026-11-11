package com.example.util;

import com.example.entity.NotificationMessage;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 无第三方依赖的 RSS 2.0 生成（消息通知订阅源）。
 * 兼容 Feedly / Inoreader 等：atom:link self、dc:creator（不用非法的裸 author）。
 */
public final class NotificationRssBuilder {
    private static final DateTimeFormatter RFC822 = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private NotificationRssBuilder() {
    }

    public static String build(String channelTitle,
                               String channelLink,
                               String channelDescription,
                               String selfFeedUrl,
                               String itemLinkPrefix,
                               List<NotificationMessage> items) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        sb.append("<channel>\n");
        sb.append("<title>").append(escape(channelTitle)).append("</title>\n");
        sb.append("<link>").append(escape(channelLink)).append("</link>\n");
        sb.append("<description>").append(escape(channelDescription)).append("</description>\n");
        sb.append("<language>zh-cn</language>\n");
        if (selfFeedUrl != null && !selfFeedUrl.isBlank()) {
            sb.append("<atom:link href=\"").append(escape(selfFeedUrl))
                    .append("\" rel=\"self\" type=\"application/rss+xml\"/>\n");
        }
        LocalDateTime newest = null;
        if (items != null) {
            for (NotificationMessage m : items) {
                if (m != null && m.getPublishedAt() != null
                        && (newest == null || m.getPublishedAt().isAfter(newest))) {
                    newest = m.getPublishedAt();
                }
            }
        }
        if (newest != null) {
            sb.append("<lastBuildDate>").append(escape(toRfc822(newest))).append("</lastBuildDate>\n");
        }
        if (items != null) {
            for (NotificationMessage m : items) {
                if (m == null || m.getId() == null) {
                    continue;
                }
                String title = m.getTitle() == null ? "消息通知" : m.getTitle();
                String link = itemLinkPrefix + m.getId();
                String desc = m.getContentHtml();
                if (desc == null || desc.isBlank()) {
                    desc = MarkdownUtils.markdownToSafeHtml(
                            m.getContentMarkdown() == null ? "" : m.getContentMarkdown());
                }
                sb.append("<item>\n");
                sb.append("<title>").append(escape(title)).append("</title>\n");
                sb.append("<link>").append(escape(link)).append("</link>\n");
                sb.append("<guid isPermaLink=\"true\">").append(escape(link)).append("</guid>\n");
                if (m.getPublishedAt() != null) {
                    sb.append("<pubDate>").append(escape(toRfc822(m.getPublishedAt()))).append("</pubDate>\n");
                }
                // RSS 2.0 的 author 必须是邮箱；用 dc:creator 放显示名，避免订阅器解析失败
                if (m.getAuthorUsername() != null && !m.getAuthorUsername().isBlank()) {
                    sb.append("<dc:creator>").append(escape(m.getAuthorUsername())).append("</dc:creator>\n");
                }
                sb.append("<description><![CDATA[").append(sanitizeCdata(desc)).append("]]></description>\n");
                sb.append("</item>\n");
            }
        }
        sb.append("</channel>\n");
        sb.append("</rss>\n");
        return sb.toString();
    }

    private static String toRfc822(LocalDateTime local) {
        return RFC822.format(local.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c != 0xFFFE && c != 0xFFFF)) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String sanitizeCdata(String html) {
        if (html == null) {
            return "";
        }
        return html.replace("]]>", "]]&gt;");
    }
}
