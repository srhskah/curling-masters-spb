package com.example.service.impl;

import com.example.entity.Season;
import com.example.mapper.SeasonMapper;
import com.example.service.SeasonService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-04
 */
@Service
public class SeasonServiceImpl extends ServiceImpl<SeasonMapper, Season> implements SeasonService {

    @Autowired
    private SeasonMapper seasonMapper;
    
    @Override
    public boolean hasRelatedSeries(Long seasonId) {
        if (seasonId == null) {
            return false;
        }
        
        // 检查是否存在关联的系列赛事
        LambdaQueryWrapper<com.example.entity.Series> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(com.example.entity.Series::getSeasonId, seasonId);
        
        return new com.example.service.impl.SeriesServiceImpl().count(queryWrapper) > 0;
    }
}