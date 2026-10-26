package com.example.ranking;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 小组排名 / 跨组总排名 / 伪小组 共用的决胜项（与 {@code GroupRankingCalculator} 历史顺序一致）。
 */
public enum RankingCriterion {
    POINTS("POINTS", "积分", true),
    HEAD_TO_HEAD("HEAD_TO_HEAD", "交锋（胜负关系）", false),
    NET("NET", "净胜分", true),
    TOTAL_SCORE("TOTAL_SCORE", "总分", true),
    MATCH_GE2("MATCH_GE2", "≥2局数总和", true),
    MATCH_MAX("MATCH_MAX", "单局最高", true),
    STEAL_COUNT("STEAL_COUNT", "偷分次数", true),
    STEAL_MAX("STEAL_MAX", "偷分最高", true),
    USERNAME("USERNAME", "用户名", false);

    private final String code;
    private final String label;
    /** 是否在排名表中有对应数据列（踢出后对该列「不再显示」） */
    private final boolean hasDisplayColumn;

    RankingCriterion(String code, String label, boolean hasDisplayColumn) {
        this.code = code;
        this.label = label;
        this.hasDisplayColumn = hasDisplayColumn;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isHasDisplayColumn() {
        return hasDisplayColumn;
    }

    /** 与改造前硬编码决胜顺序一致 */
    public static final List<RankingCriterion> DEFAULT_ORDER = List.of(
            POINTS, HEAD_TO_HEAD, NET, TOTAL_SCORE, MATCH_GE2, MATCH_MAX, STEAL_COUNT, STEAL_MAX, USERNAME
    );

    public static Optional<RankingCriterion> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.code.equalsIgnoreCase(code.trim())).findFirst();
    }

    public static Map<String, Object> toUiMap(RankingCriterion c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", c.code);
        m.put("label", c.label);
        m.put("hasDisplayColumn", c.hasDisplayColumn);
        return m;
    }
}
