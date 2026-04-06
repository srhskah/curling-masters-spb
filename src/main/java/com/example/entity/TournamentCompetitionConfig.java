package com.example.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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
    private Integer groupStageSets;
    private Integer knockoutStageSets;
    private Integer finalStageSets;
    private Boolean manualLocked;

    /** 资格赛局数，默认 8 */
    @TableField(value = "qualifier_sets", updateStrategy = FieldStrategy.ALWAYS)
    private Integer qualifierSets;

    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
