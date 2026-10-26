package com.example.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;

public class Series {
    private Long id;
    private Long seasonId;
    private Integer sequence; // 该赛季内的序号
    private String name; // 系列名称
    /** 是否启用排名参考优先级（小组赛总排名 / 小组 / 伪小组） */
    private Boolean rankingPriorityEnabled;
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(Long seasonId) {
        this.seasonId = seasonId;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getRankingPriorityEnabled() {
        return rankingPriorityEnabled;
    }

    public void setRankingPriorityEnabled(Boolean rankingPriorityEnabled) {
        this.rankingPriorityEnabled = rankingPriorityEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // 为了兼容性，提供createTime方法
    public LocalDateTime getCreateTime() {
        return createdAt;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createdAt = createTime;
    }
}
