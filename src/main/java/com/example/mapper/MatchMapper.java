package com.example.mapper;

import com.example.entity.Match;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-16
 */
public interface MatchMapper extends BaseMapper<Match> {

    /**
     * H2H：选手出现在 player1/player2 或主客字段中的场次，或与该用户存在 match_acceptance 的场次
     * （历史上存在 player 槽位/主客未与 user 对齐时，仍可通过验收记录关联到该场）。
     */
    @Select("SELECT * FROM ("
            + "SELECT * FROM `match` WHERE player1_id = #{uid} OR player2_id = #{uid} "
            + "OR home_user_id = #{uid} OR away_user_id = #{uid} "
            + "UNION "
            + "SELECT m.* FROM `match` m "
            + "INNER JOIN match_acceptance ma ON m.id = ma.match_id AND ma.user_id = #{uid}"
            + ") t")
    List<Match> selectMatchesInvolvingUser(@Param("uid") long uid);

    /**
     * H2H：两名选手互为对阵（含主客），或同一场次双方均存在验收记录（补数据不全时的双方 H2H）。
     */
    @Select("SELECT * FROM ("
            + "SELECT * FROM `match` WHERE "
            + "(player1_id = #{u1} AND player2_id = #{u2}) OR (player1_id = #{u2} AND player2_id = #{u1}) OR "
            + "(home_user_id = #{u1} AND away_user_id = #{u2}) OR (home_user_id = #{u2} AND away_user_id = #{u1}) "
            + "UNION "
            + "SELECT m.* FROM `match` m WHERE m.id IN ("
            + "SELECT match_id FROM match_acceptance WHERE user_id = #{u1}) AND m.id IN ("
            + "SELECT match_id FROM match_acceptance WHERE user_id = #{u2})"
            + ") t")
    List<Match> selectMatchesHeadToHead(@Param("u1") long u1, @Param("u2") long u2);

    /**
     * 与用户在「同一场比赛验收记录」中出现的其他用户（用于选手槽位为空时仍可列出交手过的人）。
     */
    @Select("SELECT DISTINCT ma2.user_id FROM match_acceptance ma1 "
            + "INNER JOIN match_acceptance ma2 ON ma1.match_id = ma2.match_id AND ma2.user_id <> ma1.user_id "
            + "WHERE ma1.user_id = #{uid} AND ma2.user_id IS NOT NULL AND ma2.user_id > 0")
    List<Long> selectOpponentUserIdsFromCoAcceptance(@Param("uid") long uid);
}
