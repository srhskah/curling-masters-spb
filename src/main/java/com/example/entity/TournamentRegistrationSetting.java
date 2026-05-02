package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 赛事报名接龙配置（1:1 赛事）
 */
@Getter
@Setter
@TableName("tournament_registration_setting")
public class TournamentRegistrationSetting {

    @TableId(value = "tournament_id", type = IdType.INPUT)
    private Long tournamentId;

    private Boolean enabled;

    private LocalDateTime deadline;

    @TableField("quota_n")
    private Integer quotaN;

    /**
     * 0 默认：截止后按时间先后取前 n 进入正赛（待审视同同意）<br>
     * 1 正赛-资格赛：前 m 直通车正赛，其余 (n-m) 正赛名额由资格赛决定；资格赛种子数默认可为 n-m
     */
    private Integer mode;

    @TableField("main_direct_m")
    private Integer mainDirectM;

    @TableField("qualifier_seed_count")
    private Integer qualifierSeedCount;

    @TableField("ban_total_rank_top")
    private Integer banTotalRankTop;

    @TableField("ban_other_tournament_id")
    private Long banOtherTournamentId;

    /**
     * 禁报参照的多个其他赛事 ID，逗号分隔；非空时优先于 {@link #banOtherTournamentId}。
     */
    @TableField("ban_other_tournament_ids")
    private String banOtherTournamentIds;

    @TableField("ban_other_tournament_top")
    private Integer banOtherTournamentTop;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
