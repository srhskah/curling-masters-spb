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
@TableName("tournament_group_member")
public class TournamentGroupMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tournamentId;
    private Long groupId;
    private Long userId;
    private Integer seedNo;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
