package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-17
 */
@Getter
@Setter
@TableName("tournament_level")
public class TournamentLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 等级代码，如年终总决赛、2000赛等
     */
    private String code;

    private String name;

    /**
     * 默认冠军积分比率(%)
     */
    private BigDecimal defaultChampionRatio;

    /**
     * 垫底积分
     */
    private Integer defaultBottomPoints;

    private String description;
}
