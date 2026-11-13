package com.example.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class RankingListEntryDto {
    private int rank;
    private String username;
    /** 雪花 ID 超出 JS Number 安全整数，JSON 必须按字符串输出，否则排名页重绘后链接会指向错误用户并 302 回首页 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private int points;

    public RankingListEntryDto() {
    }

    public RankingListEntryDto(int rank, Long userId, String username, int points) {
        this.rank = rank;
        this.userId = userId;
        this.username = username;
        this.points = points;
    }

    public int getRank() {
        return rank;
    }

    public String getUsername() {
        return username;
    }

    public Long getUserId() {
        return userId;
    }

    public int getPoints() {
        return points;
    }
}

