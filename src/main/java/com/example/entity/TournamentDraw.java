package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tournament_draw")
public class TournamentDraw {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tournamentId;
    /** MAIN=直通车 QUALIFIER=资格赛晋级 */
    private String drawPool;
    private String drawType; // TIERED, RANDOM
    private Integer groupCount;
    private Integer tierCount;
    /** 种子抽签：种子人数（须为 groupCount 的倍数且小于参赛人数） */
    private Integer seedCount;
    private String status; // PENDING, IN_PROGRESS, COMPLETED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 抽签开放时刻（报名截止后自动开启或手动开启后写入） */
    private LocalDateTime drawOpenedAt;
}
