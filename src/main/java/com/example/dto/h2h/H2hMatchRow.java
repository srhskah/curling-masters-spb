package com.example.dto.h2h;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 单场比赛在 H2H 列表中的展示数据。
 * 各类 id 以 JSON 字符串输出，避免前端大整数精度丢失。
 */
public record H2hMatchRow(
        @JsonFormat(shape = JsonFormat.Shape.STRING) long matchId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long tournamentId,
        String tournamentTitle,
        String category,
        Integer round,
        String phaseCode,
        String scheduledTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long player1Id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long player2Id,
        String player1Name,
        String player2Name,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long winnerId,
        String winnerName,
        int status,
        int totalPlayer1,
        int totalPlayer2,
        boolean resultLocked
) {
}
