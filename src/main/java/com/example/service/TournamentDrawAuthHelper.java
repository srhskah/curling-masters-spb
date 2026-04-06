package com.example.service;

import com.example.entity.Tournament;
import com.example.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 抽签/办赛权限：超级管理员、普通管理员、本届主办。
 */
@Component
public class TournamentDrawAuthHelper {

    private static final int ROLE_ADMIN_MAX = 1;

    @Autowired
    private TournamentService tournamentService;

    public boolean canManageDraw(User user, Long tournamentId) {
        if (user == null || tournamentId == null) {
            return false;
        }
        Integer role = user.getRole();
        if (role != null && role <= ROLE_ADMIN_MAX) {
            return true;
        }
        Tournament t = tournamentService.getById(tournamentId);
        return t != null && t.getHostUserId() != null && t.getHostUserId().equals(user.getId());
    }
}
