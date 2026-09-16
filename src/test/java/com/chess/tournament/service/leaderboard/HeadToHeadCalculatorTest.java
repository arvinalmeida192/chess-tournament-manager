package com.chess.tournament.service.leaderboard;

import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.enums.GameResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadToHeadCalculatorTest {

    private final HeadToHeadCalculator calculator = new HeadToHeadCalculator();

    @Test
    void pointsScoredAgainst_sumsDirectGamesOnly() {
        Game win = game(1L, 2L, GameResult.WHITE_WIN, "1", "0");
        Game draw = game(2L, 1L, GameResult.DRAW, "0.5", "0.5");
        Game other = game(1L, 3L, GameResult.WHITE_WIN, "1", "0");

        BigDecimal aVsB = calculator.pointsScoredAgainst(1L, 2L, List.of(win, draw, other));
        BigDecimal bVsA = calculator.pointsScoredAgainst(2L, 1L, List.of(win, draw, other));

        assertEquals(0, new BigDecimal("1.5").compareTo(aVsB));
        assertEquals(0, new BigDecimal("0.5").compareTo(bVsA));
        assertEquals(1, calculator.compare(1L, 2L, List.of(win, draw, other)));
    }

    @Test
    void ignoresByesAndPending() {
        Game bye = new Game();
        bye.setWhiteTournamentPlayerId(1L);
        bye.setBlackTournamentPlayerId(null);
        bye.setResult(GameResult.BYE);
        bye.setWhiteScore(BigDecimal.ONE);

        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculator.pointsScoredAgainst(1L, 2L, List.of(bye))));
    }

    private static Game game(long white, long black, GameResult result, String ws, String bs) {
        Game g = new Game();
        g.setWhiteTournamentPlayerId(white);
        g.setBlackTournamentPlayerId(black);
        g.setResult(result);
        g.setWhiteScore(new BigDecimal(ws));
        g.setBlackScore(new BigDecimal(bs));
        return g;
    }
}
