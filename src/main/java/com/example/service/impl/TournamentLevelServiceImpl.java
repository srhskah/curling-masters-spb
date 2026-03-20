package com.example.service.impl;

import com.example.entity.Tournament;
import com.example.entity.TournamentLevel;
import com.example.mapper.TournamentLevelMapper;
import com.example.service.TournamentService;
import com.example.service.ITournamentLevelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


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

    private final TournamentService tournamentService;

    public TournamentLevelServiceImpl(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @Override
    public boolean hasRelatedTournaments(Integer levelId) {
        if (levelId == null) return false;

        TournamentLevel level = getById(levelId);
        if (level == null || level.getCode() == null) return false;

        // 两步查询，避免使用 apply 拼接 SQL 片段。
        long count = tournamentService.lambdaQuery()
                .eq(Tournament::getLevelCode, level.getCode())
                .count();
        return count > 0;
    }
}
