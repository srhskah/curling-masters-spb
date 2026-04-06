package com.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
class MarkdownUlSplitTest {

    private static int countOccurrences(String haystack, String needle) {
        int c = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            c++;
            i++;
        }
        return c;
    }

    @Test
    void twoBulletedGroupsWithBlankLine_producesTwoUl() {
        String md = "* first a\n* first b\n\n* second a\n";
        String html = MarkdownUtils.markdownToSafeHtml(md);
        assertEquals(2, countOccurrences(html, "<ul>"), html);
    }

    @Test
    void twoGroupsWithDecorativeStars_producesTwoUl() {
        String md = "* a\n*\n*\n* b\n";
        String html = MarkdownUtils.markdownToSafeHtml(md);
        assertEquals(2, countOccurrences(html, "<ul>"), html);
    }

    @Test
    void adjacentItems_sameUl() {
        String md = "* a\n* b\n* c\n";
        String html = MarkdownUtils.markdownToSafeHtml(md);
        assertEquals(1, countOccurrences(html, "<ul>"), html);
    }

    /** 换行丢失：* 行1 * 行2 在同一条物理行时应还原为两个 li，第二颗 * 不得当正文 */
    @Test
    void twoStarItemsOnOnePhysicalLine_becomeTwoLi() {
        String md = "* 行1 * 行2";
        String html = MarkdownUtils.markdownToSafeHtml(md);
        assertEquals(1, countOccurrences(html, "<ul>"), html);
        assertEquals(2, countOccurrences(html, "<li>"), html);
        assertEquals(0, countOccurrences(html, "行1 * 行"), html);
    }

    @Test
    void twoStarItemsWithChineseSemicolon_onOneLine() {
        String md = "* 行1；* 行2";
        String html = MarkdownUtils.markdownToSafeHtml(md);
        assertEquals(2, countOccurrences(html, "<li>"), html);
    }

    /** {@code - * A * B}：首段 *…* 为强调，不得拆成多行「* 列表」 */
    @Test
    void dashList_lineWithStarEmphasis_isNotStarSplit() {
        String md = "- * 内容1 * 内容2";
        String norm = MarkdownUtils.normalizeMarkdownLineBreaks(md);
        assertFalse(norm.contains("内容1\n* 内容2"), norm);
        assertTrue(norm.contains("- * 内容1 * 内容2") || norm.contains("内容1 * 内容2"), norm);
    }

    /**
     * 整段须保留四条文案；CommonMark 对「* 文 *」两侧空格 + CJK 不保证总能解析为 {@code <em>}，故不强求斜体标签。
     */
    @Test
    void dashEmphasisThenSeparateStarItems_preservesAllLinesAsListContent() {
        String md = "- * 内容1 * 内容2\n* 内容3\n* 内容4\n";
        String html = MarkdownUtils.markdownToSafeHtml(md);
        assertTrue(html.contains("内容1") && html.contains("内容2"), html);
        assertTrue(html.contains("内容3") && html.contains("内容4"), html);
        assertTrue(countOccurrences(html, "<li>") >= 2, html);
    }

    /** 紧凑 {@code *内容*} 时强调更稳定，应出现 {@code em} */
    @Test
    void dashList_tightStarEmphasis_rendersEm() {
        String md = "- *内容1* 内容2\n";
        String html = MarkdownUtils.markdownToSafeHtml(md);
        assertTrue(html.contains("<em>") && html.contains("内容1"), html);
    }
}
