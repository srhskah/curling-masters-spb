package com.example.entity;

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
    private Integer knockoutStartRound;
    /** 资格赛挂载淘汰赛轮次（同赛事仅允许一轮） */
    private Integer qualifierRound;
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

    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
