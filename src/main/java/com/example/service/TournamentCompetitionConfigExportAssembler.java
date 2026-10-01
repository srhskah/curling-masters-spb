package com.example.service;

import com.example.entity.Season;
import com.example.entity.Series;
import com.example.entity.Tournament;
import com.example.entity.TournamentCompetitionConfig;
import com.example.entity.TournamentLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 赛事配置导出数据组装器（文本和PDF共用）
 */
@Component
public class TournamentCompetitionConfigExportAssembler {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private SeriesService seriesService;
    @Autowired
    private SeasonService seasonService;
    @Autowired
    private ITournamentLevelService tournamentLevelService;
    @Autowired
    private ITournamentCompetitionService competitionService;

    /**
     * @return 赛事不存在时返回 null
     */
    public Map<String, Object> assemble(Long tournamentId) {
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return null;
        }
        TournamentCompetitionConfig config = competitionService.getConfig(tournamentId);
        
        String seasonLabel = "";
        Integer edition = null;
        if (t.getSeriesId() != null && t.getLevelCode() != null) {
            Series series = seriesService.getById(t.getSeriesId());
            if (series != null && series.getSeasonId() != null) {
                Season season = seasonService.getById(series.getSeasonId());
                if (season != null) {
                    seasonLabel = season.getYear() + "年" + (season.getHalf() != null && season.getHalf() == 1 ? "上半年" : "下半年");
                    List<Long> seasonSeriesIds = seriesService.lambdaQuery()
                            .eq(Series::getSeasonId, season.getId())
                            .list()
                            .stream()
                            .filter(s -> !isTestSeries(s))
                            .map(Series::getId)
                            .filter(Objects::nonNull)
                            .toList();
                    if (!seasonSeriesIds.isEmpty()) {
                        List<Tournament> sameLevel = tournamentService.lambdaQuery()
                                .in(Tournament::getSeriesId, seasonSeriesIds)
                                .eq(Tournament::getLevelCode, t.getLevelCode())
                                .list();
                        sameLevel.sort(Comparator
                                .comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(Tournament::getId, Comparator.nullsLast(Comparator.naturalOrder())));
                        for (int i = 0; i < sameLevel.size(); i++) {
                            if (Objects.equals(sameLevel.get(i).getId(), t.getId())) {
                                edition = i + 1;
                                break;
                            }
                        }
                    }
                }
            }
        }
        TournamentLevel level = t.getLevelCode() == null ? null
                : tournamentLevelService.lambdaQuery().eq(TournamentLevel::getCode, t.getLevelCode()).one();
        String levelText = t.getLevelCode() != null ? t.getLevelCode() : (level != null ? level.getName() : "-");
        String tournamentEditionTitle = (seasonLabel.isEmpty() ? "赛季" : seasonLabel) + "-" + levelText + "-" + (edition == null ? "?" : edition);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("documentTitle", "赛事配置");
        root.put("tournamentEditionTitle", tournamentEditionTitle);
        root.put("tournamentId", t.getId());
        root.put("levelCode", t.getLevelCode() != null ? t.getLevelCode() : "-");
        root.put("seasonLabel", seasonLabel);
        root.put("edition", edition);
        root.put("tournamentStatus", t.getStatus());
        root.put("startDate", t.getStartDate() != null ? DT.format(t.getStartDate()) : "-");
        root.put("endDate", t.getEndDate() != null ? DT.format(t.getEndDate()) : "-");
        
        // 赛制规则
        root.put("competitionRuleType", t.getCompetitionRuleType() != null ? t.getCompetitionRuleType() : "NORMAL");
        root.put("customCenterLineLimit", t.getCustomCenterLineLimit());
        root.put("customDeadStoneLimit", t.getCustomDeadStoneLimit());
        
        // 比赛配置
        if (config != null) {
            root.put("participantCount", config.getParticipantCount() != null ? config.getParticipantCount() : 8);
            root.put("entryMode", config.getEntryMode() != null ? config.getEntryMode() : 0);
            root.put("entryModeText", entryModeText(config.getEntryMode()));
            root.put("matchMode", config.getMatchMode() != null ? config.getMatchMode() : 3);
            root.put("matchModeText", matchModeText(config.getMatchMode()));
            root.put("knockoutStartRound", config.getKnockoutStartRound());
            root.put("knockoutStartRoundText", knockoutStartRoundText(config.getKnockoutStartRound()));
            root.put("knockoutBracketMode", config.getKnockoutBracketMode());
            root.put("knockoutBracketModeText", knockoutBracketModeText(config.getKnockoutBracketMode()));
            root.put("knockoutAutoFromGroup", config.getKnockoutAutoFromGroup());
            root.put("qualifierRound", config.getQualifierRound());
            root.put("qualifierRoundText", qualifierRoundText(config.getQualifierRound()));
            root.put("knockoutQualifyCount", config.getKnockoutQualifyCount());
            root.put("groupMode", config.getGroupMode() != null ? config.getGroupMode() : 1);
            root.put("groupModeText", groupModeText(config.getGroupMode()));
            root.put("groupSize", config.getGroupSize());
            root.put("groupAllowDraw", config.getGroupAllowDraw());
            root.put("groupStageDeadline", config.getGroupStageDeadline() != null ? DT.format(config.getGroupStageDeadline()) : "-");
            root.put("groupStageSets", config.getGroupStageSets() != null ? config.getGroupStageSets() : 8);
            root.put("knockoutStageSets", config.getKnockoutStageSets());
            root.put("finalStageSets", config.getFinalStageSets());
            root.put("qualifierSets", config.getQualifierSets() != null ? config.getQualifierSets() : 8);
            root.put("manualLocked", config.getManualLocked());
        } else {
            // 默认值
            root.put("participantCount", 8);
            root.put("entryMode", 0);
            root.put("entryModeText", "默认模式");
            root.put("matchMode", 3);
            root.put("matchModeText", "小组赛");
            root.put("knockoutStartRound", null);
            root.put("knockoutStartRoundText", "-");
            root.put("knockoutBracketMode", null);
            root.put("knockoutBracketModeText", "-");
            root.put("knockoutAutoFromGroup", null);
            root.put("qualifierRound", null);
            root.put("qualifierRoundText", "-");
            root.put("knockoutQualifyCount", null);
            root.put("groupMode", 1);
            root.put("groupModeText", "单循环（不分主客）");
            root.put("groupSize", null);
            root.put("groupAllowDraw", true);
            root.put("groupStageDeadline", "-");
            root.put("groupStageSets", 8);
            root.put("knockoutStageSets", null);
            root.put("finalStageSets", null);
            root.put("qualifierSets", 8);
            root.put("manualLocked", null);
        }
        
        return root;
    }

    private static boolean isTestSeries(Series series) {
        if (series == null) return false;
        String name = series.getName();
        return name != null && name.contains("测试");
    }

    private static String entryModeText(Integer mode) {
        if (mode == null) return "默认模式";
        return mode == 1 ? "正赛+资格赛" : "默认模式";
    }

    private static String matchModeText(Integer mode) {
        if (mode == null) return "小组赛";
        return switch (mode) {
            case 1 -> "单败淘汰赛";
            case 2 -> "双败淘汰赛";
            case 3 -> "小组赛";
            default -> "-";
        };
    }

    private static String knockoutStartRoundText(Integer round) {
        if (round == null) return "-";
        return switch (round) {
            case 16 -> "1/16决赛";
            case 8 -> "1/8决赛";
            case 4 -> "1/4决赛";
            case 2 -> "半决赛";
            default -> "-";
        };
    }

    private static String knockoutBracketModeText(Integer mode) {
        if (mode == null) return "-";
        return switch (mode) {
            case 0 -> "总排名经典种子";
            case 1 -> "上下半区组间交叉";
            case 2 -> "上下半区世界杯式交错";
            default -> "-";
        };
    }

    private static String qualifierRoundText(Integer round) {
        if (round == null) return "-";
        return switch (round) {
            case 16 -> "1/16决赛附加资格赛";
            case 8 -> "1/8决赛附加资格赛";
            case 4 -> "1/4决赛附加资格赛";
            case 2 -> "半决赛附加资格赛";
            default -> "-";
        };
    }

    private static String groupModeText(Integer mode) {
        if (mode == null) return "单循环（不分主客）";
        return switch (mode) {
            case 1 -> "单循环（不分主客）";
            case 2 -> "单循环（主客场，组内人数需奇数）";
            case 3 -> "双循环（主客场）";
            default -> "-";
        };
    }
}