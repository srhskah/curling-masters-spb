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
@TableName("tournament_registration")
public class TournamentRegistration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tournamentId;
    private Long userId;

    /**
     * 0 待审 1 通过 2 拒绝<br>
     * 截止后未处理视为同意（仅用于排序/入选计算，不改变库内 status）
     */
    private Integer status;

    private LocalDateTime registeredAt;
    private LocalDateTime reviewedAt;

    @TableField("reviewed_by_user_id")
    private Long reviewedByUserId;
}
