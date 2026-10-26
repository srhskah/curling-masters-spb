package com.example.service;

import com.example.ranking.RankingCriterion;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排名参考优先级：全局有序「决胜项」队列，按系列开关在小组赛排名中生效。
 */
public interface RankingPriorityService {

    String CONFIG_APPLY_TO_NEW_SERIES = "ranking_priority_apply_to_new_series";

    /** 当前队列（优先级降序） */
    List<Map<String, Object>> listQueue();

    /** 候选池：尚未入队的决胜项 */
    List<Map<String, Object>> listCandidates();

    /**
     * 保存队列并同步系列开关。
     * @param orderedCriterionCodes 队内代码（第 1 个优先级最高）
     * @param enabledSeriesIds 勾选启用的既往系列；未勾选的既往系列关闭
     */
    void saveQueueAndSeriesFlags(List<String> orderedCriterionCodes, List<Long> enabledSeriesIds);

    boolean applyToNewSeries();

    /**
     * 系列已启用且已配置队列时返回决胜顺序；未启用返回 null（沿用代码默认顺序）。
     */
    List<RankingCriterion> criteriaOrderForTournament(Long tournamentId);

    List<RankingCriterion> criteriaOrderForSeries(Long seriesId);

    /** 启用自定义优先级时，应显示的数据列代码集合；未启用返回 null（全部显示） */
    Set<String> visibleColumnCodesForTournament(Long tournamentId);
}
