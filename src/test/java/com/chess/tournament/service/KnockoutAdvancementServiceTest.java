package com.chess.tournament.service;

import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.dao.TransactionCallback;
import com.chess.tournament.dao.UnitOfWork;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnockoutAdvancementServiceTest {

    @Mock
    private TournamentPlayerDao tournamentPlayerDao;
    @Mock
    private UnitOfWork unitOfWork;

    private KnockoutAdvancementService service;

    @BeforeEach
    void setUp() {
        service = new KnockoutAdvancementService(tournamentPlayerDao, unitOfWork);
    }

    @Test
    void markLosers_eliminatesBlackOnWhiteWin() throws Exception {
        when(unitOfWork.executeInTransaction(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });
        Game g = game(1, 10L, 20L, GameResult.WHITE_WIN);

        List<Long> losers = service.markLosers(List.of(g));

        assertEquals(List.of(20L), losers);
        verify(tournamentPlayerDao).updateQualification(
                isNull(), eq(20L), eq(QualificationStatus.ELIMINATED));
    }

    @Test
    void markLosers_skipsBye() {
        Game bye = game(1, 10L, null, GameResult.BYE);

        List<Long> losers = service.markLosers(List.of(bye));

        assertEquals(List.of(), losers);
        verifyNoInteractions(tournamentPlayerDao);
        verifyNoInteractions(unitOfWork);
    }

    @Test
    void markLosers_rejectsDraw() {
        Game draw = game(1, 10L, 20L, GameResult.DRAW);
        assertThrows(ValidationException.class, () -> service.markLosers(List.of(draw)));
    }

    @Test
    void markLosers_eliminatesWhiteOnBlackWin() throws Exception {
        ArgumentCaptor<TransactionCallback<Void>> captor = ArgumentCaptor.forClass(TransactionCallback.class);
        when(unitOfWork.executeInTransaction(captor.capture())).thenReturn(null);

        Game g = game(1, 10L, 20L, GameResult.BLACK_WIN);
        List<Long> losers = service.markLosers(List.of(g));
        assertEquals(List.of(10L), losers);

        captor.getValue().doInTransaction(null);
        verify(tournamentPlayerDao).updateQualification(
                isNull(), eq(10L), eq(QualificationStatus.ELIMINATED));
    }

    private static Game game(int board, Long white, Long black, GameResult result) {
        Game g = new Game();
        g.setBoardNumber(board);
        g.setWhiteTournamentPlayerId(white);
        g.setBlackTournamentPlayerId(black);
        g.setResult(result);
        return g;
    }
}
