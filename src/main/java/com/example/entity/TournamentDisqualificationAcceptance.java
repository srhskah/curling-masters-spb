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
@TableName("tournament_disqualification_acceptance")
public class TournamentDisqualificationAcceptance {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dqId;

    private Long userId;

    private String signature;

    @TableField("accepted_at")
    private LocalDateTime acceptedAt;
}

