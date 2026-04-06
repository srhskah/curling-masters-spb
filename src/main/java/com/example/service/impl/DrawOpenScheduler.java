package com.example.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 报名截止后，将已保存但未设置开放时刻的抽签配置自动开放（与 initializeDraw 中「截止前保存」配合）。
 */
@Service
public class DrawOpenScheduler {

    @Autowired
    private DrawManagementService drawManagementService;

    @Scheduled(cron = "0 * * * * ?")
    public void openDrawsAfterRegistrationDeadline() {
        drawManagementService.openPendingDrawsAfterDeadline(LocalDateTime.now());
    }
}
