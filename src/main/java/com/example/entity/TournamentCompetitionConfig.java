package com.example.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@TableName("tournament_competition_config")
public class TournamentCompetitionConfig {

    @TableId(value = "tournament_id", type = IdType.INPUT)
    private Long tournamentId;

    private Integer participantCount;
    /** 0默认模式 1正赛+资格赛 */
    private Integer entryMode;
    /** 1单败 2双败 3小组赛 */
    private Integer matchMode;
    /** 淘汰赛首轮：16/8/4/2(半决赛) */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer knockoutStartRound;

    /** 0 总排名经典种子（与组数奇偶无关）1 上下半区组间交叉（须偶数组）2 上下半区世界杯式交错（须偶数组，两两结对后按名次线交错） */
    @TableField(value = "knockout_bracket_mode", updateStrategy = FieldStrategy.ALWAYS)
    private Integer knockoutBracketMode;

    /** 小组赛全部验收后是否尝试自动生成淘汰赛首轮 */
    @TableField(value = "knockout_auto_from_group", updateStrategy = FieldStrategy.ALWAYS)
    private Boolean knockoutAutoFromGroup;

    /** 是否启用淘汰赛首轮抽签 */
    @TableField(value = "knockout_first_draw_enabled", updateStrategy = FieldStrategy.ALWAYS)
    private Boolean knockoutFirstDrawEnabled;

    /** 挂载资格赛是否参与首轮抽签；false 表示资格赛保持原对阵 */
    @TableField(value = "knockout_qualifier_draw", updateStrategy = FieldStrategy.ALWAYS)
    private Boolean knockoutQualifierDraw;

    /**
     * 上一阶段淘汰赛全部结束后才可进入下一阶段（默认 false）。
     * 开启后：须本轮 MAIN/FINAL 全部验收，才自动/手动生成下一轮对阵。
     */
    @TableField(value = "knockout_require_prev_stage_done", updateStrategy = FieldStrategy.ALWAYS)
    private Boolean knockoutRequirePrevStageDone;

    /** 资格赛挂载淘汰赛轮次（同赛事仅允许一轮） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer qualifierRound;

    /** 正赛+资格赛模式：资格赛名额数（≤参赛人数），对应库字段 knockout_qualify_count */
    @TableField(value = "knockout_qualify_count", updateStrategy = FieldStrategy.ALWAYS)
    private Integer knockoutQualifyCount;
    /** 1单循环无主客 2单循环主客(奇数组) 3双循环主客 */
    private Integer groupMode;
    private Integer groupSize;
    /** 小组赛是否允许平局（true=允许） */
    private Boolean groupAllowDraw;
    private LocalDateTime groupStageDeadline;
    /**
     * 1/16、1/8、1/4、半决赛、决赛（round=1 金/铜）截止时间；null 表示不限制。
     * 须显式列名：默认驼峰会把 Round16 变成 round16（无下划线），与迁移 knockout_round_16_deadline 不一致。
     * updateStrategy=ALWAYS：赛事配置表单可清空未排程轮次的截止。
     */
    @TableField(value = "knockout_round_16_deadline", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime knockoutRound16Deadline;
    @TableField(value = "knockout_round_8_deadline", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime knockoutRound8Deadline;
    @TableField(value = "knockout_round_4_deadline", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime knockoutRound4Deadline;
    @TableField(value = "knockout_round_2_deadline", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime knockoutRound2Deadline;
    @TableField(value = "knockout_round_1_deadline", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime knockoutRound1Deadline;
    /** 首轮淘汰赛附加资格赛截止；与正赛首轮截止独立。null=不限制。 */
    @TableField(value = "knockout_qualifier_deadline", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime knockoutQualifierDeadline;
    private Integer groupStageSets;
    private Integer knockoutStageSets;
    private Integer finalStageSets;
    private Boolean manualLocked;

    /**
     * 非表字段：为 true 时 saveConfig 按表单完整同步各轮截止（含清空）。
     * 仅赛事详情配置表单提交时置位，避免测试/局部 patch 误清截止。
     */
    @TableField(exist = false)
    private Boolean syncKnockoutDeadlines;

    /** 按淘汰赛 round 字段取对应截止时间；未知轮次返回 null。不含附加资格赛。 */
    public LocalDateTime getKnockoutDeadlineForRound(Integer round) {
        if (round == null) {
            return null;
        }
        return switch (round) {
            case 16 -> knockoutRound16Deadline;
            case 8 -> knockoutRound8Deadline;
            case 4 -> knockoutRound4Deadline;
            case 2 -> knockoutRound2Deadline;
            case 1 -> knockoutRound1Deadline;
            default -> null;
        };
    }

    /**
     * 从淘汰赛首轮到决赛的实际轮次（含首轮与决赛），如 8 → [8,4,2,1]。
     * start 非法时返回空列表。
     */
    public static List<Integer> knockoutRoundsFromStart(Integer startRound) {
        List<Integer> rounds = new ArrayList<>();
        if (startRound == null || startRound < 1) {
            return rounds;
        }
        int r = startRound;
        // 仅支持标准 2 的幂：16/8/4/2/1
        while (r >= 1) {
            if (r == 16 || r == 8 || r == 4 || r == 2 || r == 1) {
                rounds.add(r);
            } else {
                rounds.clear();
                return rounds;
            }
            if (r == 1) {
                break;
            }
            r = r / 2;
        }
        return rounds;
    }

    public boolean hasScheduledKnockoutRound(Integer round) {
        return knockoutRoundsFromStart(knockoutStartRound).contains(round);
    }

    /** 资格赛局数，默认 8 */
    @TableField(value = "qualifier_sets", updateStrategy = FieldStrategy.ALWAYS)
    private Integer qualifierSets;

    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
