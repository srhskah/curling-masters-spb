package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-04
 */
@Getter
@Setter
@TableName("season")
public class Season implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本ID，用于Java序列化机制

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer year;

    /**
     * 1-上半年,2-下半年
     */
    private Integer half;

    @TableField("created_at")
    private LocalDateTime createdAt;
    
    // 为了兼容性，提供createTime方法
    public LocalDateTime getCreateTime() {
        return createdAt;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createdAt = createTime;
    }
}