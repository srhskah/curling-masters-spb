package com.example.service;

import com.example.entity.Season;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-04
 */
public interface SeasonService extends IService<Season> {
    
    /**
     * 检查赛季是否有关联的系列赛事
     * @param seasonId 赛季ID
     * @return true如果有关联数据，否则false
     */
    boolean hasRelatedSeries(Long seasonId);
}