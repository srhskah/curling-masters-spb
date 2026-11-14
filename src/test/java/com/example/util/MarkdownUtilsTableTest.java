package com.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownUtilsTableTest {

    @Test
    void restoresCollapsedAndEscapedToastTable() {
        String corrupted = "| 赛事等级 | 五壶保护 | 报名限制 | 小组赛局数 | 淘汰赛局数 | 半决赛局数 | 奖牌赛局数 |  | ---- | ---- | ---- | ----- | ----- | ----- | ----- |  | CM1000 | A/正常5壶\\, B/5\\+6 | - | 6 | 6 | A/6\\, B/8 | 8 |   | CM250 | 正常5壶 | TR16 / SR8 | 4 | 6 | 6 | 8 |   | CM500 | =CM1000 | TR8 / SR4 | 6 | 6 | =CM1000 | 8 |     ⚠️注意\n"
                + "* 上述安排，暂定从2026年下半年第6系列开始实行\n";
        String html = MarkdownUtils.markdownToSafeHtml(corrupted);
        assertTrue(html.contains("<table"), html);
        assertTrue(html.contains("<th"), html);
        assertTrue(html.contains("CM1000"), html);
        assertTrue(html.contains("A/正常5壶, B/5+6") || html.contains("5+6"), html);
        assertFalse(html.contains("\\|"), html);
        assertTrue(html.contains("注意") || html.contains("⚠️"), html);
    }

    @Test
    void restoresFullyEscapedPipesFromReedit() {
        String escaped = "\\| 赛事等级 \\| 五壶保护 \\| 报名限制 \\| 小组赛局数 \\| 淘汰赛局数 \\| 半决赛局数 \\| 奖牌赛局数 \\| \\| \\-\\-\\-\\- \\| \\-\\-\\-\\- \\| \\-\\-\\-\\- \\| \\-\\-\\-\\-\\- \\| \\-\\-\\-\\-\\- \\| \\-\\-\\-\\-\\- \\| \\-\\-\\-\\-\\- \\| \\| CM1000 \\| A/正常5壶\\, B/5\\+6 \\| \\- \\| 6 \\| 6 \\| A/6\\, B/8 \\| 8 \\|";
        String normalized = MarkdownUtils.normalizeMarkdownLineBreaks(escaped);
        assertTrue(normalized.contains("| 赛事等级 |"), normalized);
        assertTrue(normalized.contains("----"), normalized);
        assertFalse(normalized.contains("\\|"), normalized);
        String html = MarkdownUtils.markdownToSafeHtml(escaped);
        assertTrue(html.contains("<table"), html);
    }

    @Test
    void keepsRelativeMarkdownLinks() {
        String html = MarkdownUtils.markdownToSafeHtml("[查看赛事](/tnd/260206/CM1000)\n\n[下载抽签结果PDF](/tournament/1/draw/export/pdf?pool=MAIN)");
        assertTrue(html.contains("href=\"/tnd/260206/CM1000\""), html);
        assertTrue(html.contains("href=\"/tournament/1/draw/export/pdf?pool=MAIN\""), html);
        assertTrue(html.contains(">查看赛事</a>"), html);
        assertTrue(html.contains(">下载抽签结果PDF</a>"), html);
    }
}
