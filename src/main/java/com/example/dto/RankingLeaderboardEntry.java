package com.example.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 排行榜条目 DTO
 */
@Getter
@Setter
public class RankingLeaderboardEntry {
    /** 排名 */
    private Integer rank;
    /** 用户ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 统计值（胜率、最高分、偷分次数、偷分最高分等） */
    private Double value;
    /** 统计值作为整数显示 */
    private Integer intValue;
    /** 显示文本（用于格式化显示） */
    private String displayValue;
}