package com.chess.tournament.service.leaderboard;

import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.enums.GameResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Sums tournament points earned in games between two tournament players (SDD §10.2).
 */
public final class HeadToHeadCalculator {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    /**
     * @return points scored by {@code tpId} against {@code opponentTpId} across the given games
     */
    public BigDecimal pointsScoredAgainst(long tpId, long opponentTpId, List<Game> games) {
        Objects.requireNonNull(games, "games");
        BigDecimal total = BigDecimal.ZERO;
        for (Game game : games) {
            if (game.getResult() == null || game.getResult() == GameResult.PENDING
                    || game.getResult() == GameResult.BYE) {
                continue;
            }
            Long white = game.getWhiteTournamentPlayerId();
            Long black = game.getBlackTournamentPlayerId();
            if (white == null || black == null) {
                continue;
            }
            boolean involvesPair =
                    (white == tpId && black == opponentTpId)
                            || (white == opponentTpId && black == tpId);
            if (!involvesPair) {
                continue;
            }
            if (white == tpId) {
                total = total.add(scoreForSide(game, true));
            } else {
                total = total.add(scoreForSide(game, false));
            }
        }
        return total;
    }

    /**
     * Comparator-style: positive if a beat b on H2H points, negative if b beat a, 0 if equal.
     */
    public int compare(long tpA, long tpB, List<Game> games) {
        BigDecimal aPoints = pointsScoredAgainst(tpA, tpB, games);
        BigDecimal bPoints = pointsScoredAgainst(tpB, tpA, games);
        return aPoints.compareTo(bPoints);
    }

    private static BigDecimal scoreForSide(Game game, boolean white) {
        BigDecimal stored = white ? game.getWhiteScore() : game.getBlackScore();
        if (stored != null) {
            return stored;
        }
        return switch (game.getResult()) {
            case WHITE_WIN -> white ? BigDecimal.ONE : BigDecimal.ZERO;
            case BLACK_WIN -> white ? BigDecimal.ZERO : BigDecimal.ONE;
            case DRAW -> HALF;
            default -> BigDecimal.ZERO;
        };
    }
}
