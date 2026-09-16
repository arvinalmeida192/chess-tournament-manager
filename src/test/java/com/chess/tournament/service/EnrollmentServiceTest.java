package com.chess.tournament.service;

import com.chess.tournament.dao.GameDao;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private TournamentDao tournamentDao;
    @Mock
    private TournamentPlayerDao tournamentPlayerDao;
    @Mock
    private PlayerDao playerDao;
    @Mock
    private RoundDao roundDao;
    @Mock
    private GameDao gameDao;

    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(
                tournamentDao, tournamentPlayerDao, playerDao, roundDao, gameDao);
    }

    @Test
    void enroll_copiesGlobalRating() {
        Tournament t = draftTournament(1L);
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(t));
        when(tournamentPlayerDao.findByTournament(1L)).thenReturn(List.of());
        Player player = new Player();
        player.setId(5L);
        player.setName("Alice");
        player.setGlobalRating(1725);
        player.setActive(true);
        when(playerDao.findById(5L)).thenReturn(Optional.of(player));
        when(tournamentPlayerDao.insert(any(TournamentPlayer.class))).thenReturn(99L);

        TournamentPlayer tp = enrollmentService.enroll(1L, 5L);

        ArgumentCaptor<TournamentPlayer> captor = ArgumentCaptor.forClass(TournamentPlayer.class);
        verify(tournamentPlayerDao).insert(captor.capture());
        assertEquals(1725, captor.getValue().getStartRating());
        assertEquals(1725, captor.getValue().getCurrentRating());
        assertEquals(99L, tp.getId());
    }

    @Test
    void enroll_rejectsDuplicate() {
        Tournament t = draftTournament(1L);
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(t));
        TournamentPlayer existing = new TournamentPlayer();
        existing.setPlayerId(5L);
        when(tournamentPlayerDao.findByTournament(1L)).thenReturn(List.of(existing));
        Player player = new Player();
        player.setId(5L);
        player.setActive(true);
        player.setGlobalRating(1500);
        when(playerDao.findById(5L)).thenReturn(Optional.of(player));

        assertThrows(ValidationException.class, () -> enrollmentService.enroll(1L, 5L));
        verify(tournamentPlayerDao, never()).insert(any());
    }

    @Test
    void enroll_lockedWhenRound1Published() {
        Tournament t = new Tournament();
        t.setId(1L);
        t.setStatus(TournamentStatus.ACTIVE);
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(t));
        Round round1 = new Round();
        round1.setStatus(RoundStatus.PAIRINGS_PUBLISHED);
        when(roundDao.findByTournamentAndNumber(1L, 1)).thenReturn(Optional.of(round1));

        assertThrows(ValidationException.class, () -> enrollmentService.enroll(1L, 5L));
        assertTrue(enrollmentService.isEnrollmentLocked(1L));
    }

    @Test
    void unenroll_rejectsWhenGamesExist() {
        Tournament t = draftTournament(1L);
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(t));
        when(gameDao.existsForTournament(1L)).thenReturn(true);

        assertThrows(ValidationException.class, () -> enrollmentService.unenroll(1L, 5L));
    }

    private static Tournament draftTournament(long id) {
        Tournament t = new Tournament();
        t.setId(id);
        t.setStatus(TournamentStatus.DRAFT);
        return t;
    }
}
