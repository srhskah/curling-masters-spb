package com.example.service;

import com.example.dto.h2h.H2hPayload;
import com.example.dto.h2h.H2hUserOption;
import com.example.entity.Match;
import com.example.entity.Tournament;
import com.example.entity.User;
import com.example.mapper.MatchAcceptanceMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeadToHeadQueryServiceTest {

    @Mock private UserService userService;
    @Mock private IMatchService matchService;
    @Mock private TournamentService tournamentService;
    @Mock private SeriesService seriesService;
    @Mock private SeasonService seasonService;
    @Mock private ITournamentLevelService tournamentLevelService;
    @Mock private ISetScoreService setScoreService;
    @Mock private MatchAcceptanceMapper matchAcceptanceMapper;

    @InjectMocks private HeadToHeadQueryService service;

    @Test
    void buildPayload_onlyUser1_returnsMatchesFromSlotsOnly() {
        Long userId = 1001L;
        Match m = new Match();
        m.setId(1L);
        m.setTournamentId(99L);
        m.setStatus((byte) 0);
        m.setPlayer1Id(userId);
        m.setPlayer2Id(2001L);

        when(matchService.list(any(Wrapper.class))).thenReturn(List.of(m));
        when(setScoreService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        Tournament t = new Tournament();
        t.setId(99L);
        when(tournamentService.listByIds(eq(Set.of(99L)))).thenReturn(List.of(t));
        User p1 = new User();
        p1.setId(userId);
        p1.setUsername("u1001");
        User p2 = new User();
        p2.setId(2001L);
        p2.setUsername("u2001");
        when(userService.listByIds(eq(Set.of(userId, 2001L)))).thenReturn(new java.util.ArrayList<>(List.of(p1, p2)));

        H2hPayload payload = service.buildPayload(userId, null);

        Assertions.assertNotNull(payload);
        Assertions.assertFalse(payload.seasons().isEmpty(), "选手一应在四槽位命中时返回场次");
    }

    @Test
    void listOpponentsOf_usesOnlySlotOpponents() {
        Long userId = 1001L;
        Match m = new Match();
        m.setId(11L);
        m.setPlayer1Id(userId);
        m.setPlayer2Id(2002L);

        when(matchService.list(any(Wrapper.class))).thenReturn(List.of(m));

        User u2 = new User();
        u2.setId(2002L);
        u2.setUsername("bravo");
        when(userService.listByIds(eq(Set.of(2002L)))).thenReturn(new java.util.ArrayList<>(List.of(u2)));

        List<H2hUserOption> opponents = service.listOpponentsOf(userId);

        Assertions.assertEquals(1, opponents.size());
        Assertions.assertEquals(2002L, opponents.get(0).id());
        Assertions.assertEquals("bravo", opponents.get(0).username());
    }
}
