package com.example.service;

import com.example.dto.TournamentRegistrationPreviewDto;
import com.example.dto.TournamentRegistrationRowDto;
import com.example.entity.Tournament;
import com.example.entity.TournamentRegistrationSetting;
import com.example.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public interface ITournamentRegistrationService {

    TournamentRegistrationSetting getSetting(Long tournamentId);

    /**
     * 创建或更新报名配置（仅筹备中赛事；管理员或主办）
     */
    void saveSetting(User editor, TournamentRegistrationSetting form);

    void register(Long tournamentId, Long userId, LocalDateTime now);

    void withdraw(Long tournamentId, Long userId, LocalDateTime now);

    void approve(Long tournamentId, Long targetUserId, User reviewer, LocalDateTime now);

    /** 拒绝 = 删除报名记录，允许日后再次报名（若仍开放） */
    void reject(Long tournamentId, Long targetUserId, User reviewer, LocalDateTime now);

    List<TournamentRegistrationRowDto> listRows(Long tournamentId, LocalDateTime now);

    TournamentRegistrationPreviewDto preview(Long tournamentId, LocalDateTime now);

    /** null 表示可报名 */
    String validateRegister(Long tournamentId, Long userId, LocalDateTime now);

    boolean canManage(User user, Long tournamentId);

    /** 筹备中 + 已开启 + 当前早于截止时间 */
    boolean isRegistrationOpen(Tournament tournament, LocalDateTime now);

    /**
     * 是否显示报名接龙入口：仅看赛事是否为「筹备中」。<br>
     * 新建赛事尚未保存报名配置时也应显示入口，便于管理员进入页面开启报名。
     */
    boolean registrationModuleActive(Tournament tournament, LocalDateTime now);

    /** 已在后台开启报名（存在配置且 enabled=true） */
    boolean isRegistrationEnabled(Tournament tournament);

    /** 将「正赛直通车」用户写入 tournament_entry（entry_type=1），幂等跳过已存在 */
    int materializeMainDirectEntries(User operator, Long tournamentId, LocalDateTime now);

    List<Tournament> listOpenRegistrationTournaments(Integer limit, LocalDateTime now);

    List<Tournament> listOpenRegistrationForSeason(Long seasonId, LocalDateTime now);

    boolean hasRegistration(Long tournamentId, Long userId);
}
