package com.example.service;

import com.example.ranking.RankingCriterion;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排名参考优先级：全局有序「决胜项」队列，按系列开关作为默认；
 * 单届赛事可独立覆盖，不影响其他赛事。
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
     * 本届已独立覆盖则用本届队列；否则系列已启用且已配置队列时返回全局决胜顺序；
     * 未启用返回 null（沿用代码默认顺序）。
     */
    List<RankingCriterion> criteriaOrderForTournament(Long tournamentId);

    List<RankingCriterion> criteriaOrderForSeries(Long seriesId);

    boolean hasTournamentOverride(Long tournamentId);

    /**
     * 赛事详情编辑器：queue / candidates / override。
     * 未覆盖时队列展示当前生效顺序（系列队列或代码默认），保存后才写入本届。
     */
    Map<String, Object> editorStateForTournament(Long tournamentId);

    /** 仅写入本届队列，不改全局队列与其他赛事。 */
    void saveTournamentQueue(Long tournamentId, List<String> orderedCriterionCodes);

    /** 取消本届覆盖，恢复跟随系列/全局默认。 */
    void clearTournamentOverride(Long tournamentId);

    /** 启用自定义优先级时，应显示的数据列代码集合；未启用返回 null（全部显示） */
    Set<String> visibleColumnCodesForTournament(Long tournamentId);
}
