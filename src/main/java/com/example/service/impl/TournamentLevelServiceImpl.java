package com.example.service.impl;

import com.example.entity.TournamentLevel;
import com.example.mapper.TournamentLevelMapper;
import com.example.service.ITournamentLevelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-17
 */
@Service
public class TournamentLevelServiceImpl extends ServiceImpl<TournamentLevelMapper, TournamentLevel> implements ITournamentLevelService {

    @Override
    public boolean hasRelatedTournaments(Integer levelId) {
        // 通过SQL查询检查是否有赛事使用该等级
        String sql = "SELECT COUNT(1) FROM tournament WHERE level_code = (SELECT code FROM tournament_level WHERE id = " + levelId + ")";
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<TournamentLevel>()
                .apply("EXISTS (SELECT 1 FROM tournament WHERE level_code = (SELECT code FROM tournament_level WHERE id = {0}))", levelId));
        return count > 0;
    }
}
