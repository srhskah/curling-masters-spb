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
@TableName("match_score_edit_log")
public class MatchScoreEditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Integer setNumber;
    private Long editorUserId;
    private Integer oldPlayer1Score;
    private Integer oldPlayer2Score;
    private Integer newPlayer1Score;
    private Integer newPlayer2Score;
    private Boolean oldPlayer1IsX;
    private Boolean oldPlayer2IsX;
    private Boolean newPlayer1IsX;
    private Boolean newPlayer2IsX;
    @TableField("edited_at")
    private LocalDateTime editedAt;
}
