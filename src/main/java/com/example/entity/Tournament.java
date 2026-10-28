package com.example.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Tournament {
    private Long id;
    private Long seriesId;
    private String levelCode; // 关联tournament_level.code
    private Long hostUserId; // 主办用户
    private BigDecimal championPointsRatio; // 实际使用的冠军积分比率（可修改）
    private Integer status; // 0-筹备中,1-进行中,2-已结束
    private LocalDate startDate;
    private LocalDate endDate;
    private String competitionRuleType; // 赛制规则类型：NORMAL-正常5壶保护制，CUSTOM-自定义
    private Integer customCenterLineLimit; // 自定义不允许磕中线壶的数量（0-16）
    private Integer customDeadStoneLimit; // 自定义不允许打成死壶的数量（0-16）
    /** 为 true 时使用本届独立决胜项队列，不再跟随系列/全局默认 */
    private Boolean rankingPriorityOverride;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    
    // 为了兼容性，提供createTime方法
    public LocalDateTime getCreateTime() {
        return createdAt;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createdAt = createTime;
    }
}
