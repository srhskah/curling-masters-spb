package com.example.service;

import com.example.entity.TournamentLevel;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Curling Masters
 * @since 2026-03-17
 */
public interface ITournamentLevelService extends IService<TournamentLevel> {
    
    /**
     * 检查赛事等级是否有关联的赛事
     * @param levelId 赛事等级ID
     * @return true如果有关联数据，否则false
     */
    boolean hasRelatedTournaments(Integer levelId);
}
