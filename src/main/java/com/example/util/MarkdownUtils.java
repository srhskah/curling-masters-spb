package com.example.util;

import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Markdown → 安全 HTML（与 Toast UI Editor / GFM 常用语法对齐）。
 */
public final class MarkdownUtils {

    /**
     * 补偿换行丢失：正文被压成一行时，CommonMark 会把从首行 {@code ###} 直到文本末尾
     * 全部当作同一个小节标题，结果详情页呈「一整块 H3」。
     * <p>
     * 规则尽量保守，避免破坏代码块（通知正文通常无围栏代码块）。
     */
    private static final Pattern ATX_NEED_SPACE_AFTER_HASHES = Pattern.compile("(?m)^(#{1,6})([^\\s#])");
    private static final Pattern ATX_AFTER_NON_NEWLINE = Pattern.compile("([^\\n])(#{1,6}\\s)");
    private static final Pattern HEADING_THEN_BULLET = Pattern.compile("(#{1,6}\\s[^\\n#]+?)\\s+(\\*\\s)");
    private static final Pattern HEADING_THEN_DASH_LIST = Pattern.compile("(#{1,6}\\s[^\\n#]+?)\\s+(-\\s)");
    /** 句号/右括号后的「1. 」「2. 」有序列表（常见为正文压行后编号贴在上句末尾） */
    private static final Pattern SENTENCE_THEN_ORDERED_ITEM = Pattern.compile(
            "([）\\)\\u3002．\\.!！?？])\\s*(\\d{1,2}\\.\\s)");

    /**
     * 多条无序列表被压成一行时（如 {@code * 行1 * 行2}、{@code * 行1；* 行2}），整段会变成一个 {@code li}，第二颗 * 以字面形式显示。
     * 在换行恢复的第一步拆开，使每条 {@code * …} 单独成行。
     * <p>
     * 不作用于 {@code - * 内容1 * 内容2}：此处首颗 * 表示强调起点、与收尾 * 成对，不应拆成两条以 * 开头的列表。
     */
    private static final Pattern SAME_LINE_JOINED_UL_STAR_ITEMS = Pattern.compile(
            "(\\*\\s+[^\\n*]+?)(?:[\\s；;，,]*)(\\*\\s+\\S)");

    /** 减号/加号列表项内以 {@code * } 开头，视为强调而非新列表项（见 {@link #SAME_LINE_JOINED_UL_STAR_ITEMS}） */
    private static final Pattern LINE_LEADING_DASH_OR_PLUS_THEN_STAR_EMPHASIS = Pattern.compile(
            "^\\s*(?:[-+]\\s+\\*\\s).+");

    /** 无序/有序列表项行首（仅用于续行缩进推断） */
    private static final Pattern LIST_MARKER_LINE = Pattern.compile(
            "^ {0,3}(?:[-*+\\u2022\\u2023\\u25E6\\u2043\\u2219\\u00B7\\u30FB]|\\d{1,3}\\.)\\s+\\S.*");

    /**
     * 行首无缩进的无序列表标记（含 Word/IME 常用项目符号）；有序见 {@link #ROOT_ORDERED_LIST_MARKER}。
     */
    private static final Pattern ROOT_UNORDERED_LIST_MARKER = Pattern.compile(
            "^[-*+\\u2022\\u2023\\u25E6\\u2043\\u2219\\u00B7\\u30FB]\\s+\\S.*");

    private static final Pattern ROOT_ORDERED_LIST_MARKER = Pattern.compile("^\\d{1,3}\\.\\s+\\S.*");

    /** 两段列表之间的装饰行（仅 * / - / + / • 等），顶格；不计入列表项 */
    private static final Pattern BETWEEN_LIST_DECORATIVE_LINE = Pattern.compile(
            "^(?:[-*+\\u2022\\u2023\\u25E6\\u2043\\u2219\\u00B7\\u30FB]\\s*)+$");

    /**
     * 单独成行，CommonMark 会解析为独立 {@code <p>}，从而<strong>可靠地</strong>结束上一列表并开启下一列表；
     * 不可依赖内嵌 HTML（易被当作文本或清洗），且避免与「空行」混淆。
     */
    private static final char MARKDOWN_UL_SPLIT_ZWSP = '\u200C';

    /** 与 {@link #MARKDOWN_UL_SPLIT_ZWSP} 同源，{@link #indentImplicitListContinuations} 中勿当续行缩进 */
    private static final String MARKDOWN_UL_SPLIT_LINE = String.valueOf(MARKDOWN_UL_SPLIT_ZWSP);

    /**
     * CommonMark 有时仍把「仅含 ZWNJ 的段落」收进上一 {@code <ul>} 的 {@code <li>}，导致多段列表合并；
     * 在清洗后收拢成固定形态再拆回两个 {@code <ul>}。
     */
    private static final Pattern HTML_UL_FAKE_SPLIT_LI = Pattern.compile(
            "</li>\\s*<li>\\s*<p>\\s*(?:&#8204;|&#x200c;|" + MARKDOWN_UL_SPLIT_ZWSP + ")\\s*</p>\\s*</li>\\s*(?=<li>)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML_UL_FAKE_SPLIT_LI_TIGHT = Pattern.compile(
            "</li>\\s*<li>\\s*(?:&#8204;|&#x200c;|" + MARKDOWN_UL_SPLIT_ZWSP + ")\\s*</li>\\s*(?=<li>)",
            Pattern.CASE_INSENSITIVE);

    private static final List<?> MD_EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create(),
            TaskListItemsExtension.create()
    );

    @SuppressWarnings("unchecked")
    private static final Parser PARSER = Parser.builder()
            .extensions((Iterable<org.commonmark.parser.Parser.ParserExtension>) (Iterable<?>) MD_EXTENSIONS)
            .build();

    @SuppressWarnings("unchecked")
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions((Iterable<org.commonmark.renderer.html.HtmlRenderer.HtmlRendererExtension>) (Iterable<?>) MD_EXTENSIONS)
            /* 默认换行输出为纯 \n，在 HTML 里会被折成空格；通知正文在列表/条款里常按回车换行，需保留为可视换行 */
            .softbreak("<br />\n")
            .build();

    private MarkdownUtils() {
    }

    private static String splitJoinedUlStarItemsOnSamePhysicalLine(String md) {
        if (md == null || md.isEmpty()) {
            return md == null ? "" : md;
        }
        String[] lines = md.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String line = lines[i];
            if (LINE_LEADING_DASH_OR_PLUS_THEN_STAR_EMPHASIS.matcher(line).matches()) {
                sb.append(line);
                continue;
            }
            String out = line;
            String prev;
            do {
                prev = out;
                out = SAME_LINE_JOINED_UL_STAR_ITEMS.matcher(out).replaceAll("$1\n$2");
            } while (!out.equals(prev));
            sb.append(out);
        }
        return sb.toString();
    }

    /**
     * 在解析前恢复应有的换行，修复被压成单行的标题/列表结构。
     * 写入数据库前也可调用，使再次编辑时仍为多行 Markdown。
     */
    public static String normalizeMarkdownLineBreaks(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown == null ? "" : markdown;
        }
        String out = markdown.replace("\r\n", "\n").replace('\r', '\n');
        out = splitJoinedUlStarItemsOnSamePhysicalLine(out);
        // ATX 标题：行首 {@code ###} 后必须有空格，否则 commonmark 不当作标题
        out = ATX_NEED_SPACE_AFTER_HASHES.matcher(out).replaceAll("$1 $2");
        // 多个 {@code ###} 被拼在同一物理行
        out = ATX_AFTER_NON_NEWLINE.matcher(out).replaceAll("$1\n$2");
        // 标题与紧跟的无序列表之间应断开（例如「### 须知 * 条款」）
        out = HEADING_THEN_BULLET.matcher(out).replaceAll("$1\n\n$2");
        out = HEADING_THEN_DASH_LIST.matcher(out).replaceAll("$1\n\n$2");
        // 「……制） 1. 小节」→ 列表另起段
        out = SENTENCE_THEN_ORDERED_ITEM.matcher(out).replaceAll("$1\n\n$2");
        out = insertListSplitHtmlBlocks(out);
        return indentImplicitListContinuations(out);
    }

    /**
     * 在「本应独立」的两段根级列表之间插入 {@link #MARKDOWN_UL_SPLIT_ZWSP} 独立段落行，否则 CommonMark
     * 会把中间即便有空行仍合并为同一 {@code <ul>/<ol>}。
     * <p>
     * 间隙可为：纯空行、或仅含顶格「装饰」行（多行 {@code *} / {@code -} / {@code •} 等）。
     * 相邻两行列表项（中间无间隙）不插入，仍为同一列表。
     */
    private static String insertListSplitHtmlBlocks(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown == null ? "" : markdown;
        }
        String[] lines = markdown.split("\n", -1);
        Set<Integer> insertSeparatorBeforeLine = new HashSet<>();
        markListSplitBeforeIndices(lines, insertSeparatorBeforeLine, false);
        markListSplitBeforeIndices(lines, insertSeparatorBeforeLine, true);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            if (insertSeparatorBeforeLine.contains(i)) {
                /* 前后空行：保证成段，避免并进上一列表项 */
                sb.append("\n\n").append(MARKDOWN_UL_SPLIT_LINE).append("\n\n");
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /**
     * @param ordered true：根级有序列表且下一行以 {@code 1.} 重编时才拆；false：根级无序列表
     */
    private static void markListSplitBeforeIndices(String[] lines, Set<Integer> out, boolean ordered) {
        List<Integer> markerIndices = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (ordered) {
                if (isRootOrderedMarkdownListLine(lines[i])) {
                    markerIndices.add(i);
                }
            } else {
                if (isRootUnorderedMarkdownListLine(lines[i])) {
                    markerIndices.add(i);
                }
            }
        }
        for (int k = 1; k < markerIndices.size(); k++) {
            int prevLineIdx = markerIndices.get(k - 1);
            int curLineIdx = markerIndices.get(k);
            if (curLineIdx <= prevLineIdx + 1) {
                continue;
            }
            if (!listGapAllowsDocumentSplit(lines, prevLineIdx + 1, curLineIdx)) {
                continue;
            }
            if (ordered) {
                String next = lines[curLineIdx].stripLeading();
                if (!(next.startsWith("1.") && next.length() > 2 && Character.isWhitespace(next.charAt(2)))) {
                    continue;
                }
            }
            out.add(curLineIdx);
        }
    }

    /**
     * {@code gapStart} 含、{@code gapEnd} 不含；必须非空间隙，且只能含空行或顶格装饰行。
     */
    private static boolean listGapAllowsDocumentSplit(String[] lines, int gapStart, int gapEnd) {
        if (gapStart >= gapEnd) {
            return false;
        }
        for (int g = gapStart; g < gapEnd; g++) {
            String line = lines[g];
            if (line.isBlank()) {
                continue;
            }
            if (isBetweenListDecorativeLine(line)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isBetweenListDecorativeLine(String line) {
        if (line.isBlank()) {
            return false;
        }
        String t = line.stripLeading();
        return t.length() == line.length() && BETWEEN_LIST_DECORATIVE_LINE.matcher(t).matches();
    }

    private static boolean isRootUnorderedMarkdownListLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String t = line.stripLeading();
        return t.length() == line.length() && ROOT_UNORDERED_LIST_MARKER.matcher(t).matches();
    }

    private static boolean isRootOrderedMarkdownListLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String t = line.stripLeading();
        return t.length() == line.length() && ROOT_ORDERED_LIST_MARKER.matcher(t).matches();
    }

    /**
     * 用户在编辑器里按回车得到的「无缩进续行」常被 CommonMark 解析成列表外新段落，导致列表内换行丢失。
     * 为紧跟在列表项后的裸续行补 4 格缩进，使其并入上一列表项，从而生成 SoftBreak → {@code &lt;br /&gt;}（见下方 HtmlRenderer#softbreak）。
     */
    private static String indentImplicitListContinuations(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown == null ? "" : markdown;
        }
        String[] lines = markdown.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean pendingListItem = false;
        boolean inLooseItemBody = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) {
                sb.append('\n');
            }
            boolean blank = line.isBlank();
            if (blank) {
                pendingListItem = false;
                inLooseItemBody = false;
                sb.append(line);
                continue;
            }
            if (isMarkdownUlSplitParagraphPlaceholderLine(line)) {
                pendingListItem = false;
                inLooseItemBody = false;
                sb.append(line);
                continue;
            }
            if (LIST_MARKER_LINE.matcher(line).matches()) {
                pendingListItem = true;
                inLooseItemBody = false;
                sb.append(line);
                continue;
            }
            if (pendingListItem && needsAutoListContinuationIndent(line)) {
                sb.append("    ").append(line);
                pendingListItem = false;
                inLooseItemBody = true;
                continue;
            }
            if (inLooseItemBody && needsAutoListContinuationIndent(line)) {
                sb.append("    ").append(line);
                continue;
            }
            pendingListItem = false;
            inLooseItemBody = false;
            sb.append(line);
        }
        return sb.toString();
    }

    private static boolean needsAutoListContinuationIndent(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        int lead = line.length() - line.stripLeading().length();
        /* 已有缩进则交给解析器自行处理，避免在已合法的续行上再叠 4 格 */
        if (lead > 0) {
            return false;
        }
        String rest = line.stripLeading();
        if (rest.startsWith("<")) {
            return false;
        }
        if (rest.startsWith("```")) {
            return false;
        }
        if (rest.startsWith("|")) {
            return false;
        }
        if (rest.startsWith(">")) {
            return false;
        }
        if (rest.matches("^#{1,6}\\s.*")) {
            return false;
        }
        if (rest.matches("^[-*+\\u2022\\u2023\\u25E6\\u2043\\u2219\\u00B7\\u30FB]\\s.*")) {
            return false;
        }
        if (rest.matches("^\\d{1,3}\\.\\s.*")) {
            return false;
        }
        return !rest.matches("^[-*_]{3,}\\s*$");
    }

    private static boolean isMarkdownUlSplitParagraphPlaceholderLine(String line) {
        if (line == null) {
            return false;
        }
        String t = line.stripLeading().stripTrailing();
        return t.length() == 1 && t.charAt(0) == MARKDOWN_UL_SPLIT_ZWSP;
    }

    /**
     * 渲染为 HTML 并做 XSS 清洗；保留表格、代码块 class、任务列表 checkbox、删除线等。
     */
    public static String markdownToSafeHtml(String markdown) {
        String source = normalizeMarkdownLineBreaks(markdown == null ? "" : markdown);
        Node document = PARSER.parse(source);
        String unsafe = RENDERER.render(document);
        Safelist allow = Safelist.relaxed()
                .addTags("del", "input", "figure", "figcaption")
                .addAttributes("input", "type", "disabled", "checked", "class", "readonly")
                .addAttributes("del", "class")
                .addAttributes("th", "colspan", "rowspan", "align", "scope")
                .addAttributes("td", "colspan", "rowspan", "align");
        allow.addAttributes(":all", "class", "id");
        allow.addTags("br");
        String cleaned = Jsoup.clean(unsafe, allow);
        return repairSplitListPlaceholderInHtml(cleaned);
    }

    private static String repairSplitListPlaceholderInHtml(String html) {
        if (html == null || html.isEmpty()) {
            return html == null ? "" : html;
        }
        String s = HTML_UL_FAKE_SPLIT_LI.matcher(html).replaceAll("</li></ul><ul>");
        return HTML_UL_FAKE_SPLIT_LI_TIGHT.matcher(s).replaceAll("</li></ul><ul>");
    }

    /**
     * @deprecated 使用 {@link #markdownToSafeHtml(String)}
     */
    @Deprecated
    public static String renderToHtml(String markdown) {
        return markdownToSafeHtml(markdown);
    }
}
