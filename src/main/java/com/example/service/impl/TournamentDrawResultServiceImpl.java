package com.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.TournamentDrawResult;
import com.example.mapper.TournamentDrawResultMapper;
import com.example.service.TournamentDrawResultService;
import org.springframework.stereotype.Service;

@Service
public class TournamentDrawResultServiceImpl extends ServiceImpl<TournamentDrawResultMapper, TournamentDrawResult> implements TournamentDrawResultService {
}
