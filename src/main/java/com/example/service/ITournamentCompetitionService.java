package com.example.service;

import com.example.entity.*;

import java.util.List;
import java.util.Map;

public interface ITournamentCompetitionService {
    TournamentCompetitionConfig getConfig(Long tournamentId);
    TournamentCompetitionConfig saveConfig(User operator, TournamentCompetitionConfig form);
    List<Integer> calcGroupSizeOptions(Integer participantCount);
    void autoFillGroupMembersFromRegistration(User operator, Long tournamentId);
    void saveGroupMembers(User operator, Long tournamentId, Map<Long, List<Long>> groupUserMap);
    /** 仅保存一个小组的成员名单（不影响其他组），并校验与他人小组不重复 */
    void saveGroupMembersForGroup(User operator, Long tournamentId, Long groupId, List<Long> memberUserIds);
    /**
     * 按当前页面提交的各组原始名单全量覆盖数据库中的小组名单（赛事下每个分组均会写入；请求中未出现的分组 id 视为该组清空），
     * 随后删除全部小组赛场次并按各组名单重新生成对阵。
     */
    void saveAllGroupsRosterAndGenerateMatches(User operator, Long tournamentId, Map<Long, String> groupIdToRawText);
    void generateGroupMatches(User operator, Long tournamentId);
    /**
     * 按当前名单增量同步该组小组赛：删除已不再需要的对局（多余场次优先删未录入比分的），
     * 仅新增缺失的对局；未受影响的场次保留（含已录比分）。
     */
    void syncGroupMatchesForGroup(User operator, Long tournamentId, Long groupId);
    boolean canEditMatchScore(User operator, Match match);
    /** 办赛人员（管理员/主办）或本场比赛双方选手是否可执行验收 */
    boolean canAcceptMatchScore(User operator, Match match);
    void saveMatchScore(User operator, Long matchId, Integer firstEndHammer, List<String> player1Scores, List<String> player2Scores,
                        Boolean autoAccept, String signature);
    void acceptMatchScore(User operator, Long matchId, String signature);
    /** 小组赛截止后自动验收全部未锁定的小组赛场次。 */
    void autoAcceptOverdueGroupMatches(Long tournamentId);
}
