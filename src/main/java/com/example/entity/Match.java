package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-16
 */
@Getter
@Setter
@TableName("`match`")
public class Match implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tournament_id")
    private Long tournamentId;

    /**
     * 类别：1000赛资格赛、1/8决赛等
     */
    private String category;

    /**
     * 轮次，用于排序
     */
    private Integer round;

    @TableField("player1_id")
    private Long player1Id;

    @TableField("player2_id")
    private Long player2Id;

    /**
     * 冗余胜者，可通过比分计算
     */
    @TableField("winner_id")
    private Long winnerId;

    /**
     * 0-未开始,1-进行中,2-已结束,3-退赛
     */
    private Byte status;

    @TableField("scheduled_time")
    private LocalDateTime scheduledTime;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 1-player1, 2-player2
     */
    @TableField("first_end_hammer")
    private Byte firstEndHammer;
    
    // 为了兼容性，提供createTime方法
    public LocalDateTime getCreateTime() {
        return createdAt;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createdAt = createTime;
    }
}
