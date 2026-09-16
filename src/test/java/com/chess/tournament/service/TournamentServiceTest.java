package com.chess.tournament.service;

import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.dao.TransactionCallback;
import com.chess.tournament.dao.UnitOfWork;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.dto.CreateTournamentCommand;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentDao tournamentDao;
    @Mock
    private TournamentPlayerDao tournamentPlayerDao;
    @Mock
    private RoundDao roundDao;
    @Mock
    private UnitOfWork unitOfWork;

    private TournamentService tournamentService;

    @BeforeEach
    void setUp() {
        tournamentService = new TournamentService(tournamentDao, tournamentPlayerDao, roundDao, unitOfWork);
    }

    @Test
    void create_persistsDraftTournament() {
        when(tournamentDao.insert(any(Tournament.class))).thenReturn(10L);
        Tournament persisted = new Tournament();
        persisted.setId(10L);
        persisted.setName("Spring Open");
        persisted.setStatus(TournamentStatus.DRAFT);
        when(tournamentDao.findById(10L)).thenReturn(Optional.of(persisted));

        CreateTournamentCommand cmd = new CreateTournamentCommand(
                " Spring Open ", TournamentType.SWISS, 5, 2, SwissFirstRoundMethod.RANDOM);

        Tournament created = tournamentService.create(cmd);

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentDao).insert(captor.capture());
        assertEquals("Spring Open", captor.getValue().getName());
        assertEquals(TournamentStatus.DRAFT, captor.getValue().getStatus());
        assertEquals(10L, created.getId());
    }

    @Test
    void start_rejectsWhenTooFewPlayers() {
        Tournament t = new Tournament();
        t.setId(1L);
        t.setType(TournamentType.ROUND_ROBIN);
        t.setRoundsPlanned(3);
        t.setStatus(TournamentStatus.DRAFT);
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(t));
        when(tournamentPlayerDao.findByTournament(1L)).thenReturn(List.of(new TournamentPlayer()));

        assertThrows(ValidationException.class, () -> tournamentService.start(1L));
    }

    @Test
    void start_runsTransactionWhenValid() {
        Tournament t = new Tournament();
        t.setId(1L);
        t.setType(TournamentType.SWISS);
        t.setRoundsPlanned(5);
        t.setStatus(TournamentStatus.DRAFT);
        when(tournamentDao.findById(1L)).thenReturn(Optional.of(t));
        when(tournamentPlayerDao.findByTournament(1L)).thenReturn(List.of(
                new TournamentPlayer(), new TournamentPlayer(), new TournamentPlayer(), new TournamentPlayer()));
        when(unitOfWork.executeInTransaction(any())).thenReturn(null);

        Optional<String> warning = tournamentService.start(1L);

        assertTrue(warning.isEmpty());
        ArgumentCaptor<TransactionCallback<?>> callbackCaptor = ArgumentCaptor.forClass(TransactionCallback.class);
        verify(unitOfWork).executeInTransaction(callbackCaptor.capture());
    }
}
