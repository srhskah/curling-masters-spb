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
@TableName("tournament_disqualification")
public class TournamentDisqualification {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tournamentId;

    private Long userId;

    private String reason;

    private Boolean effective;

    private LocalDateTime effectiveAt;

    private Long createdByUserId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

