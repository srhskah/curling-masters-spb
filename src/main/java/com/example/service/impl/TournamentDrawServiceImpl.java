package com.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.TournamentDraw;
import com.example.mapper.TournamentDrawMapper;
import com.example.service.TournamentDrawService;
import org.springframework.stereotype.Service;

@Service
public class TournamentDrawServiceImpl extends ServiceImpl<TournamentDrawMapper, TournamentDraw> implements TournamentDrawService {
}
