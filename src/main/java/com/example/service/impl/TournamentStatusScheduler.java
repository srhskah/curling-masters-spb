package com.example.service.impl;

import org.springframework.stereotype.Service;

/**
 * 赛事状态相关定时任务占位。
 * <p>报名截止后不再自动改赛事状态；超时自动验收见 {@link GroupDeadlineAutoAcceptScheduler}。
 */
@Service
public class TournamentStatusScheduler {
}
