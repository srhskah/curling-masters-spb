package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("`match`")
public class Match implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tournament_id")
    private Long tournamentId;

    /** 类别：1000赛资格赛、1/8决赛等 */
    private String category;

    /** 阶段：GROUP/QUALIFIER/MAIN/FINAL */
    @TableField("phase_code")
    private String phaseCode;

    /** 小组赛对应分组 */
    @TableField("group_id")
    private Long groupId;

    /** 轮次，用于排序 */
    private Integer round;

    @TableField("player1_id")
    private Long player1Id;

    @TableField("player2_id")
    private Long player2Id;

    @TableField("home_user_id")
    private Long homeUserId;

    @TableField("away_user_id")
    private Long awayUserId;

    /** 冗余胜者，可通过比分计算 */
    @TableField("winner_id")
    private Long winnerId;

    /** 0-未开始,1-进行中,2-已结束,3-退赛 */
    private Byte status;

    /** 管理员/主办验收后锁定比分 */
    @TableField("result_locked")
    private Boolean resultLocked;

    @TableField("accepted_by_user_id")
    private Long acceptedByUserId;

    @TableField("accepted_at")
    private LocalDateTime acceptedAt;

    /** 场次创建者（手动排签可追踪到操作者） */
    @TableField("created_by_user_id")
    private Long createdByUserId;

    /** 场次来源：AUTO_FROM_GROUP / MANUAL_KO_EDITOR / AUTO_BRACKET_ADVANCE */
    @TableField("create_source")
    private String createSource;

    @TableField("scheduled_time")
    private LocalDateTime scheduledTime;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 资格赛轮次（NULL=非资格赛） */
    @TableField("qualifier_round")
    private Integer qualifierRound;

    @TableField("knockout_bracket_slot")
    private Integer knockoutBracketSlot;

    @TableField("feeder_match1_id")
    private Long feederMatch1Id;

    @TableField("feeder_match2_id")
    private Long feederMatch2Id;

    @TableField("knockout_half")
    private Integer knockoutHalf;

    /** 1-player1, 2-player2 */
    @TableField("first_end_hammer")
    private Byte firstEndHammer;

    public LocalDateTime getCreateTime() {
        return createdAt;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createdAt = createTime;
    }
}
