package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRegistrationRowDto {
    private Long registrationId;
    private Long userId;
    private String username;
    /** 0 待审 1 通过 2 拒绝 */
    private Integer status;
    private LocalDateTime registeredAt;
    /** 截止后未处理视为同意 */
    private boolean effectiveApproved;
    /** 纯文本说明：本系列其他赛事参赛/报名标注 */
    private String seriesCrossNote;
}
