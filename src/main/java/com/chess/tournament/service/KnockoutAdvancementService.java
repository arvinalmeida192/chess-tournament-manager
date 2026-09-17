package com.chess.tournament.service;

import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.dao.UnitOfWork;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Marks knockout losers as {@link QualificationStatus#ELIMINATED}.
 * Invoked from {@code ResultService.completeRound}; callable standalone for tests.
 */
public final class KnockoutAdvancementService {

    private static final Logger log = LoggerFactory.getLogger(KnockoutAdvancementService.class);

    private final TournamentPlayerDao tournamentPlayerDao;
    private final UnitOfWork unitOfWork;

    public KnockoutAdvancementService(TournamentPlayerDao tournamentPlayerDao, UnitOfWork unitOfWork) {
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    /**
     * For each completed non-bye game, sets the loser's qualification to ELIMINATED.
     * Bye games have no loser. Draws are rejected.
     *
     * @return tournament-player ids marked eliminated
     */
    public List<Long> markLosers(List<Game> completedRoundGames) {
        Objects.requireNonNull(completedRoundGames, "completedRoundGames");
        List<Long> losers = collectLoserIds(completedRoundGames);
        if (losers.isEmpty()) {
            return List.of();
        }
        unitOfWork.executeInTransaction(connection -> {
            markLosers(connection, completedRoundGames);
            return null;
        });
        return List.copyOf(losers);
    }

    /**
     * Marks losers using an existing transaction connection (used by {@link ResultService#completeRound}).
     */
    public List<Long> markLosers(Connection connection, List<Game> completedRoundGames) {
        Objects.requireNonNull(completedRoundGames, "completedRoundGames");
        List<Long> losers = collectLoserIds(completedRoundGames);
        for (Long loserId : losers) {
            tournamentPlayerDao.updateQualification(
                    connection, loserId, QualificationStatus.ELIMINATED);
        }
        if (!losers.isEmpty()) {
            log.info("Marked {} knockout losers as ELIMINATED", losers.size());
        }
        return List.copyOf(losers);
    }

    private static List<Long> collectLoserIds(List<Game> completedRoundGames) {
        List<Long> losers = new ArrayList<>();
        for (Game game : completedRoundGames) {
            Long loserId = loserTpId(game);
            if (loserId != null) {
                losers.add(loserId);
            }
        }
        return losers;
    }

    static Long loserTpId(Game game) {
        GameResult result = game.getResult();
        if (result == null || result == GameResult.PENDING) {
            throw new ValidationException(
                    "Cannot mark losers: game board " + game.getBoardNumber() + " is not completed");
        }
        return switch (result) {
            case BYE -> null;
            case WHITE_WIN -> {
                if (game.getBlackTournamentPlayerId() == null) {
                    throw new ValidationException("WHITE_WIN missing black player");
                }
                yield game.getBlackTournamentPlayerId();
            }
            case BLACK_WIN -> {
                if (game.getWhiteTournamentPlayerId() == null) {
                    throw new ValidationException("BLACK_WIN missing white player");
                }
                yield game.getWhiteTournamentPlayerId();
            }
            case DRAW -> throw new ValidationException(
                    "Knockout does not allow draws (board " + game.getBoardNumber() + ")");
            case PENDING -> throw new ValidationException("Unreachable");
        };
    }
}
