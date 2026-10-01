package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 赛事报名禁报参照配置（每个参照赛事独立K值）
 */
@Getter
@Setter
@TableName("tournament_registration_ban_ref")
public class TournamentRegistrationBanRef {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tournament_id")
    private Long tournamentId;

    @TableField("ref_tournament_id")
    private Long refTournamentId;

    @TableField("ban_top")
    private Integer banTop;

    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}