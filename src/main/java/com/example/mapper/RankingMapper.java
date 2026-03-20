package com.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RankingMapper {

    @Select("""
        <script>
        SELECT utp.user_id AS userId,
               t.series_id AS seriesId,
               MAX(utp.points) AS seriesPoints
        FROM user_tournament_points utp
        JOIN tournament t ON t.id = utp.tournament_id
        WHERE t.series_id IN
        <foreach collection="seriesIds" item="sid" open="(" separator="," close=")">
            #{sid}
        </foreach>
        GROUP BY utp.user_id, t.series_id
        </script>
        """)
    List<Map<String, Object>> selectUserSeriesMaxPoints(@Param("seriesIds") List<Long> seriesIds);

    @Select("""
        <script>
        SELECT utp.user_id AS userId,
               t.series_id AS seriesId,
               MAX(utp.points) AS seriesPoints
        FROM user_tournament_points utp
        JOIN tournament t ON t.id = utp.tournament_id
        WHERE t.series_id IN
        <foreach collection="seriesIds" item="sid" open="(" separator="," close=")">
            #{sid}
        </foreach>
        <if test="excludedLevelCodes != null and excludedLevelCodes.size() > 0">
          AND t.level_code NOT IN
          <foreach collection="excludedLevelCodes" item="lc" open="(" separator="," close=")">
              #{lc}
          </foreach>
        </if>
        GROUP BY utp.user_id, t.series_id
        </script>
        """)
    List<Map<String, Object>> selectUserSeriesMaxPointsExcludingLevels(
            @Param("seriesIds") List<Long> seriesIds,
            @Param("excludedLevelCodes") List<String> excludedLevelCodes
    );

    @Select("""
        <script>
        SELECT utp.user_id AS userId,
               SUM(utp.points) AS totalPoints
        FROM user_tournament_points utp
        JOIN tournament t ON t.id = utp.tournament_id
        WHERE t.series_id IN
        <foreach collection="seriesIds" item="sid" open="(" separator="," close=")">
            #{sid}
        </foreach>
        AND t.level_code IN
        <foreach collection="levelCodes" item="lc" open="(" separator="," close=")">
            #{lc}
        </foreach>
        GROUP BY utp.user_id
        </script>
        """)
    List<Map<String, Object>> selectUserTotalPointsForLevels(
            @Param("seriesIds") List<Long> seriesIds,
            @Param("levelCodes") List<String> levelCodes
    );

    @Select("""
        <script>
        SELECT DISTINCT t.series_id AS seriesId
        FROM tournament t
        WHERE t.series_id IN
        <foreach collection="seriesIds" item="sid" open="(" separator="," close=")">
            #{sid}
        </foreach>
        AND t.level_code IN
        <foreach collection="levelCodes" item="lc" open="(" separator="," close=")">
            #{lc}
        </foreach>
        </script>
        """)
    List<Long> selectSeriesIdsHavingLevels(
            @Param("seriesIds") List<Long> seriesIds,
            @Param("levelCodes") List<String> levelCodes
    );

    @Select("""
        <script>
        SELECT utp.user_id AS userId,
               t.series_id AS seriesId,
               MAX(utp.points) AS seriesPoints
        FROM user_tournament_points utp
        JOIN tournament t ON t.id = utp.tournament_id
        WHERE t.series_id IN
        <foreach collection="seriesIds" item="sid" open="(" separator="," close=")">
            #{sid}
        </foreach>
        <if test="excludedSeriesIds != null and excludedSeriesIds.size() > 0">
          AND t.series_id NOT IN
          <foreach collection="excludedSeriesIds" item="esid" open="(" separator="," close=")">
              #{esid}
          </foreach>
        </if>
        GROUP BY utp.user_id, t.series_id
        </script>
        """)
    List<Map<String, Object>> selectUserSeriesMaxPointsExcludingSeriesIds(
            @Param("seriesIds") List<Long> seriesIds,
            @Param("excludedSeriesIds") List<Long> excludedSeriesIds
    );

    @Select("""
        <script>
        SELECT t.series_id AS seriesId,
               MAX(utp.points) AS seriesPoints
        FROM user_tournament_points utp
        JOIN tournament t ON t.id = utp.tournament_id
        WHERE utp.user_id = #{userId}
        AND t.series_id IN
        <foreach collection="seriesIds" item="sid" open="(" separator="," close=")">
            #{sid}
        </foreach>
        <if test="excludedSeriesIds != null and excludedSeriesIds.size() > 0">
          AND t.series_id NOT IN
          <foreach collection="excludedSeriesIds" item="esid" open="(" separator="," close=")">
              #{esid}
          </foreach>
        </if>
        GROUP BY t.series_id
        </script>
        """)
    List<Map<String, Object>> selectUserSeriesMaxPointsForUserExcludingSeriesIds(
            @Param("userId") Long userId,
            @Param("seriesIds") List<Long> seriesIds,
            @Param("excludedSeriesIds") List<Long> excludedSeriesIds
    );

    @Select("""
        <script>
        SELECT t.series_id AS seriesId,
               t.level_code AS levelCode,
               SUM(utp.points) AS totalPoints
        FROM user_tournament_points utp
        JOIN tournament t ON t.id = utp.tournament_id
        WHERE utp.user_id = #{userId}
        AND t.series_id IN
        <foreach collection="seriesIds" item="sid" open="(" separator="," close=")">
            #{sid}
        </foreach>
        AND t.level_code IN
        <foreach collection="levelCodes" item="lc" open="(" separator="," close=")">
            #{lc}
        </foreach>
        GROUP BY t.series_id, t.level_code
        </script>
        """)
    List<Map<String, Object>> selectUserFinalsPointsBySeriesAndLevel(
            @Param("userId") Long userId,
            @Param("seriesIds") List<Long> seriesIds,
            @Param("levelCodes") List<String> levelCodes
    );
}

