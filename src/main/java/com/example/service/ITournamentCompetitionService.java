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
    /**
     * @param allowPersistWithXWithoutAutoAccept 为 true 时，允许在存在 X 的情况下先落库比分（不触发 save 内自带的 autoAccept），
     *               供 {@link #saveMatchScoreThenAccept} 等“保存后立即验收”的原子流程使用。
     */
    void saveMatchScore(User operator, Long matchId, Integer firstEndHammer, List<String> player1Scores, List<String> player2Scores,
                        Boolean autoAccept, String signature, boolean allowPersistWithXWithoutAutoAccept);
    /**
     * 原子提交：先按当前表单保存局分/先后手，再执行验收（避免“只点了验收、未先保存”导致数据未落库）。
     */
    void saveMatchScoreThenAccept(User operator, Long matchId, Integer firstEndHammer, List<String> player1Scores, List<String> player2Scores,
                                  String signature);
    void acceptMatchScore(User operator, Long matchId, String signature);
    /** 小组赛截止后自动验收全部未锁定的小组赛场次。 */
    void autoAcceptOverdueGroupMatches(Long tournamentId);

    /**
     * 一键刷新排名：清空本赛事既有积分记录后，按当前赛况重算分步积分（与验收后触发的规则一致：
     * 未晋级者、各轮落败者、奖牌赛结果等）。
     */
    void recomputeTournamentRankingPoints(Long tournamentId);

    /**
     * 按当前验收规则批量回补淘汰赛（MAIN/FINAL/KO_QUALIFIER）“已验收”状态。
     * @return 本次由未验收 -> 已验收 的场次数
     */
    int recomputeKnockoutAcceptanceStates(User operator, Long tournamentId);
}
