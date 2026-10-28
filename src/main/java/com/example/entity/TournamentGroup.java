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
@TableName("tournament_group")
public class TournamentGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tournamentId;
    private String groupName;
    private Integer groupOrder;
    /**
     * 唯一 ≥3 人同分且胜平负相同时的排名依据：
     * {@code PSEUDO}=拆伪小组（默认）；{@code GROUP}=用完整小组数据。
     */
    @TableField("rank_tie_mode")
    private String rankTieMode;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
