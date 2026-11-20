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

    /**
     * 新建赛事默认：小组赛是否允许平局（null/true=允许）。
     * 仅影响之后新建并首次保存运行配置的赛事，不回写已有赛事。
     */
    private Boolean groupAllowDraw;

    /**
     * 新建赛事默认：禁平局时加局胜负积分模式。
     * {@code 2_1}=胜+2/败+1（默认）；{@code 3_0}=胜+3/败+0。
     */
    private String groupOtPointsMode;
}
