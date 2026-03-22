package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
 * @since 2026-03-16
 */
@Getter
@Setter
@TableName("set_score")
public class SetScore implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long matchId;

    /**
     * 第几局
     */
    private Integer setNumber;

    private Integer player1Score;

    private Boolean player1IsX;

    private Integer player2Score;

    private Boolean player2IsX;

    private LocalDateTime createdAt;

    /**
     * ID
     */
    private Long hammerPlayerId;

    private Byte isBlankEnd;
}
