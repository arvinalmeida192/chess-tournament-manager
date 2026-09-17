package com.chess.tournament.service;

import com.chess.tournament.dao.GameDao;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.dao.UnitOfWork;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.NotFoundException;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.RatingService.RatingOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Saves game results and completes rounds with scoring + Elo (SDD §7.5, SRS FR-RES-* / FR-RAT-*).
 */
public final class ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultService.class);
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final TournamentDao tournamentDao;
    private final TournamentPlayerDao tournamentPlayerDao;
    private final RoundDao roundDao;
    private final GameDao gameDao;
    private final PlayerDao playerDao;
    private final UnitOfWork unitOfWork;
    private final RatingService ratingService;
    private final KnockoutAdvancementService knockoutAdvancementService;

    public ResultService(TournamentDao tournamentDao,
                         TournamentPlayerDao tournamentPlayerDao,
                         RoundDao roundDao,
                         GameDao gameDao,
                         PlayerDao playerDao,
                         UnitOfWork unitOfWork,
                         RatingService ratingService,
                         KnockoutAdvancementService knockoutAdvancementService) {
        this.tournamentDao = Objects.requireNonNull(tournamentDao);
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.roundDao = Objects.requireNonNull(roundDao);
        this.gameDao = Objects.requireNonNull(gameDao);
        this.playerDao = Objects.requireNonNull(playerDao);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.ratingService = Objects.requireNonNull(ratingService);
        this.knockoutAdvancementService = Objects.requireNonNull(knockoutAdvancementService);
    }

    public List<Game> listGamesForRound(long tournamentId, int roundNumber) {
        Round round = requireRound(tournamentId, roundNumber);
        return gameDao.findByRound(round.getId());
    }

    /**
     * Incremental save of a single game result. Does not update points or ratings
     * (those apply on {@link #completeRound}).
     */
    public Game saveGameResult(long gameId, GameResult result) {
        Objects.requireNonNull(result, "result");
        if (result == GameResult.PENDING) {
            throw new ValidationException("Cannot save PENDING as a result");
        }

        Game game = gameDao.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Game not found: " + gameId));
        Round round = roundDao.findById(game.getRoundId())
                .orElseThrow(() -> new NotFoundException("Round not found for game " + gameId));
        Tournament tournament = requireTournament(round.getTournamentId());

        assertRoundEditable(tournament, round);
        validateResultForGame(tournament, game, result);
        applyResultFields(game, result);

        unitOfWork.executeInTransaction(connection -> {
            gameDao.updateResult(connection, game);
            if (round.getStatus() == RoundStatus.PAIRINGS_PUBLISHED) {
                roundDao.updateStatus(connection, round.getId(), RoundStatus.IN_PROGRESS,
                        round.getPairedAt(), null);
            }
            return null;
        });
        log.info("Saved result {} for game {} (tournament {} round {})",
                result, gameId, tournament.getId(), round.getRoundNumber());
        return gameDao.findById(gameId).orElseThrow();
    }

    /**
     * Completes a round: applies points/W-D-L, Elo (non-bye), marks KO losers, sets COMPLETED,
     * and creates the next planned round as {@link RoundStatus#PENDING_PAIRINGS} when applicable.
     *
     * @return next round number ready for pairings, if one was prepared
     */
    public Optional<Integer> completeRound(long tournamentId, int roundNumber) {
        Tournament tournament = requireTournament(tournamentId);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new ValidationException("Results require an ACTIVE tournament");
        }
        Round round = requireRound(tournamentId, roundNumber);
        if (round.getStatus() == RoundStatus.COMPLETED) {
            throw new ValidationException("Round " + roundNumber + " is already COMPLETED");
        }
        if (round.getStatus() != RoundStatus.PAIRINGS_PUBLISHED
                && round.getStatus() != RoundStatus.IN_PROGRESS) {
            throw new ValidationException(
                    "Round " + roundNumber + " cannot be completed (status=" + round.getStatus() + ")");
        }

        List<Game> games = gameDao.findByRound(round.getId());
        if (games.isEmpty()) {
            throw new ValidationException("Round " + roundNumber + " has no games");
        }
        for (Game game : games) {
            if (game.getResult() == null || game.getResult() == GameResult.PENDING) {
                throw new ValidationException(
                        "Cannot complete round: board " + game.getBoardNumber() + " has no result");
            }
            if (tournament.getType() == TournamentType.KNOCKOUT && game.getResult() == GameResult.DRAW) {
                throw new ValidationException(
                        "Knockout does not allow draws (board " + game.getBoardNumber() + ")");
            }
        }

        List<TournamentPlayer> players = tournamentPlayerDao.findByTournament(tournamentId);
        Map<Long, TournamentPlayer> byTpId = new HashMap<>();
        Map<Long, Integer> ratingSnapshot = new HashMap<>();
        for (TournamentPlayer tp : players) {
            byTpId.put(tp.getId(), tp);
            ratingSnapshot.put(tp.getId(), tp.getCurrentRating());
        }

        Instant completedAt = Instant.now();
        Optional<Integer> nextRound = unitOfWork.executeInTransaction(connection -> {
            for (Game game : games) {
                processCompletedGame(connection, game, byTpId, ratingSnapshot);
            }
            for (TournamentPlayer tp : byTpId.values()) {
                tournamentPlayerDao.updateStats(connection, tp);
            }
            roundDao.updateStatus(connection, round.getId(), RoundStatus.COMPLETED,
                    round.getPairedAt(), completedAt);
            if (tournament.getType() == TournamentType.KNOCKOUT) {
                knockoutAdvancementService.markLosers(connection, games);
            }
            return createNextRoundIfNeeded(connection, tournament, roundNumber);
        });
        log.info("Completed tournament {} round {}", tournamentId, roundNumber);
        return nextRound;
    }

    private Optional<Integer> createNextRoundIfNeeded(Connection connection,
                                                      Tournament tournament,
                                                      int completedRoundNumber) {
        int nextNumber = completedRoundNumber + 1;
        if (nextNumber > tournament.getRoundsPlanned()) {
            return Optional.empty();
        }
        Optional<Round> existing = roundDao.findByTournamentAndNumber(
                connection, tournament.getId(), nextNumber);
        if (existing.isPresent()) {
            return existing.get().getStatus() == RoundStatus.PENDING_PAIRINGS
                    ? Optional.of(nextNumber)
                    : Optional.empty();
        }
        Round created = new Round();
        created.setTournamentId(tournament.getId());
        created.setRoundNumber(nextNumber);
        created.setStatus(RoundStatus.PENDING_PAIRINGS);
        roundDao.insert(connection, created);
        log.info("Prepared tournament {} round {} for pairings", tournament.getId(), nextNumber);
        return Optional.of(nextNumber);
    }

    public Optional<Integer> findResultsRoundNumber(long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            return Optional.empty();
        }
        return roundDao.findByTournament(tournamentId).stream()
                .filter(r -> r.getStatus() == RoundStatus.PAIRINGS_PUBLISHED
                        || r.getStatus() == RoundStatus.IN_PROGRESS)
                .map(Round::getRoundNumber)
                .min(Integer::compareTo);
    }

    private void processCompletedGame(Connection connection,
                                      Game game,
                                      Map<Long, TournamentPlayer> byTpId,
                                      Map<Long, Integer> ratingSnapshot) {
        GameResult result = game.getResult();
        if (result == GameResult.BYE) {
            TournamentPlayer white = requireTp(byTpId, game.getWhiteTournamentPlayerId());
            white.setPoints(white.getPoints().add(BigDecimal.ONE));
            white.setWins(white.getWins() + 1);
            white.setGamesPlayed(white.getGamesPlayed() + 1);
            game.setWhiteScore(BigDecimal.ONE);
            game.setBlackScore(null);
            game.setWhiteRatingDelta(null);
            game.setBlackRatingDelta(null);
            gameDao.updateResult(connection, game);
            return;
        }

        TournamentPlayer white = requireTp(byTpId, game.getWhiteTournamentPlayerId());
        TournamentPlayer black = requireTp(byTpId, game.getBlackTournamentPlayerId());
        BigDecimal whiteScore = scoreForWhite(result);
        BigDecimal blackScore = scoreForBlack(result);
        applyScoreRecord(white, whiteScore);
        applyScoreRecord(black, blackScore);
        game.setWhiteScore(whiteScore);
        game.setBlackScore(blackScore);

        int whiteStart = ratingSnapshot.get(white.getId());
        int blackStart = ratingSnapshot.get(black.getId());
        RatingOutcome whiteOut = ratingService.rate(whiteStart, blackStart, whiteScore.doubleValue());
        RatingOutcome blackOut = ratingService.rate(blackStart, whiteStart, blackScore.doubleValue());

        white.setCurrentRating(whiteOut.newRating());
        black.setCurrentRating(blackOut.newRating());
        game.setWhiteRatingDelta(whiteOut.delta());
        game.setBlackRatingDelta(blackOut.delta());
        gameDao.updateResult(connection, game);

        updateGlobalRating(connection, white.getPlayerId(), whiteOut.newRating());
        updateGlobalRating(connection, black.getPlayerId(), blackOut.newRating());
    }

    private void updateGlobalRating(Connection connection, long playerId, int newRating) {
        Player player = playerDao.findById(connection, playerId)
                .orElseThrow(() -> new NotFoundException("Player not found: " + playerId));
        player.setGlobalRating(newRating);
        playerDao.update(connection, player);
    }

    private static void applyScoreRecord(TournamentPlayer tp, BigDecimal score) {
        tp.setPoints(tp.getPoints().add(score));
        tp.setGamesPlayed(tp.getGamesPlayed() + 1);
        int cmp = score.compareTo(BigDecimal.ONE);
        if (cmp == 0) {
            tp.setWins(tp.getWins() + 1);
        } else if (score.compareTo(HALF) == 0) {
            tp.setDraws(tp.getDraws() + 1);
        } else if (score.compareTo(BigDecimal.ZERO) == 0) {
            tp.setLosses(tp.getLosses() + 1);
        } else {
            throw new ValidationException("Unexpected game score: " + score);
        }
    }

    private void assertRoundEditable(Tournament tournament, Round round) {
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new ValidationException("Results require an ACTIVE tournament");
        }
        if (round.getStatus() == RoundStatus.COMPLETED) {
            throw new ValidationException("Cannot edit results of a COMPLETED round");
        }
        if (round.getStatus() != RoundStatus.PAIRINGS_PUBLISHED
                && round.getStatus() != RoundStatus.IN_PROGRESS) {
            throw new ValidationException(
                    "Round is not open for results (status=" + round.getStatus() + ")");
        }
        Optional<Round> laterPublished = roundDao.findByTournament(tournament.getId()).stream()
                .filter(r -> r.getRoundNumber() > round.getRoundNumber())
                .filter(r -> r.getStatus() != RoundStatus.PENDING_PAIRINGS)
                .findFirst();
        if (laterPublished.isPresent()) {
            throw new ValidationException(
                    "Cannot edit results: a later round already has pairings or is completed");
        }
    }

    private static void validateResultForGame(Tournament tournament, Game game, GameResult result) {
        boolean byeGame = game.getBlackTournamentPlayerId() == null;
        if (byeGame) {
            if (result != GameResult.BYE) {
                throw new ValidationException("Bye boards must remain BYE");
            }
            return;
        }
        if (result == GameResult.BYE) {
            throw new ValidationException("Cannot set BYE on a two-player game");
        }
        if (tournament.getType() == TournamentType.KNOCKOUT && result == GameResult.DRAW) {
            throw new ValidationException("Knockout does not allow draws");
        }
    }

    private static void applyResultFields(Game game, GameResult result) {
        game.setResult(result);
        if (result == GameResult.BYE) {
            game.setWhiteScore(BigDecimal.ONE);
            game.setBlackScore(null);
        } else {
            game.setWhiteScore(scoreForWhite(result));
            game.setBlackScore(scoreForBlack(result));
        }
    }

    static BigDecimal scoreForWhite(GameResult result) {
        return switch (result) {
            case WHITE_WIN -> BigDecimal.ONE;
            case BLACK_WIN -> BigDecimal.ZERO;
            case DRAW -> HALF;
            default -> throw new ValidationException("No white score for " + result);
        };
    }

    static BigDecimal scoreForBlack(GameResult result) {
        return switch (result) {
            case WHITE_WIN -> BigDecimal.ZERO;
            case BLACK_WIN -> BigDecimal.ONE;
            case DRAW -> HALF;
            default -> throw new ValidationException("No black score for " + result);
        };
    }

    private static TournamentPlayer requireTp(Map<Long, TournamentPlayer> byTpId, Long id) {
        if (id == null) {
            throw new ValidationException("Missing tournament player on game");
        }
        TournamentPlayer tp = byTpId.get(id);
        if (tp == null) {
            throw new NotFoundException("Tournament player not found: " + id);
        }
        return tp;
    }

    private Tournament requireTournament(long tournamentId) {
        return tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));
    }

    private Round requireRound(long tournamentId, int roundNumber) {
        return roundDao.findByTournamentAndNumber(tournamentId, roundNumber)
                .orElseThrow(() -> new NotFoundException(
                        "Round " + roundNumber + " not found for tournament " + tournamentId));
    }
}
