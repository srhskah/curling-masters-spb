package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tournament_draw_result")
public class TournamentDrawResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tournamentId;
    /** MAIN / QUALIFIER */
    private String drawPool;
    private Long userId;
    private Long groupId;
    private Integer tierNumber;
    /** 种子抽签：是否种子选手 */
    private Boolean isSeed;
    /** 组内位次 1..每组人数 */
    private Integer groupSlotIndex;
    private Integer drawOrder;
    private Boolean isAutoAssigned;
    private LocalDateTime createdAt;
}
