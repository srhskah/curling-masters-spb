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
