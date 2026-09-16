package com.chess.tournament.service;

import com.chess.tournament.dao.GameDao;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.dao.UnitOfWork;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.LongPair;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.NotFoundException;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.pairing.PairingContext;
import com.chess.tournament.service.pairing.PairingProposal;
import com.chess.tournament.service.pairing.PairingStrategy;
import com.chess.tournament.service.pairing.PairingStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates and publishes round pairings via format-specific strategies (SDD §7.4).
 */
public final class PairingService {

    private static final Logger log = LoggerFactory.getLogger(PairingService.class);

    private final TournamentDao tournamentDao;
    private final TournamentPlayerDao tournamentPlayerDao;
    private final RoundDao roundDao;
    private final GameDao gameDao;
    private final UnitOfWork unitOfWork;
    private final PairingStrategyFactory strategyFactory;

    public PairingService(TournamentDao tournamentDao,
                          TournamentPlayerDao tournamentPlayerDao,
                          RoundDao roundDao,
                          GameDao gameDao,
                          UnitOfWork unitOfWork,
                          PairingStrategyFactory strategyFactory) {
        this.tournamentDao = Objects.requireNonNull(tournamentDao);
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.roundDao = Objects.requireNonNull(roundDao);
        this.gameDao = Objects.requireNonNull(gameDao);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.strategyFactory = Objects.requireNonNull(strategyFactory);
    }

    public List<PairingProposal> generatePairings(long tournamentId, int roundNumber) {
        LoadedContext loaded = loadAndValidate(tournamentId, roundNumber);
        PairingStrategy strategy = strategyFactory.forType(loaded.tournament().getType());
        List<PairingProposal> proposals = strategy.pair(loaded.context());
        validateProposals(proposals, loaded.players(), loaded.tournament().getType(), roundNumber);
        return proposals;
    }

    /**
     * Generates pairings and publishes them in one transaction.
     *
     * @return published games for the round
     */
    public List<Game> generateAndPublish(long tournamentId, int roundNumber) {
        List<PairingProposal> proposals = generatePairings(tournamentId, roundNumber);
        publishPairings(tournamentId, roundNumber, proposals);
        Round round = roundDao.findByTournamentAndNumber(tournamentId, roundNumber)
                .orElseThrow(() -> new NotFoundException("Round not found after publish"));
        return gameDao.findByRound(round.getId());
    }

    public void publishPairings(long tournamentId, int roundNumber, List<PairingProposal> proposals) {
        LoadedContext loaded = loadAndValidate(tournamentId, roundNumber);
        validateProposals(proposals, loaded.players(), loaded.tournament().getType(), roundNumber);

        Instant pairedAt = Instant.now();
        unitOfWork.executeInTransaction(connection -> {
            List<Game> games = toGames(loaded.round().getId(), proposals);
            gameDao.insertBatch(connection, games);
            roundDao.updateStatus(connection, loaded.round().getId(),
                    RoundStatus.PAIRINGS_PUBLISHED, pairedAt, null);
            return null;
        });
        log.info("Published {} pairings for tournament {} round {}",
                proposals.size(), tournamentId, roundNumber);
    }

    public List<Game> listGamesForRound(long tournamentId, int roundNumber) {
        Round round = roundDao.findByTournamentAndNumber(tournamentId, roundNumber)
                .orElseThrow(() -> new NotFoundException(
                        "Round " + roundNumber + " not found for tournament " + tournamentId));
        return gameDao.findByRound(round.getId());
    }

    public List<Game> listAllGames(long tournamentId) {
        List<Round> rounds = roundDao.findByTournament(tournamentId);
        List<Game> all = new ArrayList<>();
        for (Round round : rounds) {
            all.addAll(gameDao.findByRound(round.getId()));
        }
        return all;
    }

    /**
     * Finds the next round that can receive pairings, if any.
     */
    public Optional<Integer> findPairableRoundNumber(long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            return Optional.empty();
        }
        List<Round> rounds = roundDao.findByTournament(tournamentId);
        return rounds.stream()
                .filter(r -> r.getStatus() == RoundStatus.PENDING_PAIRINGS)
                .map(Round::getRoundNumber)
                .min(Integer::compareTo);
    }

    private LoadedContext loadAndValidate(long tournamentId, int roundNumber) {
        Tournament tournament = requireTournament(tournamentId);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new ValidationException("Pairings require an ACTIVE tournament");
        }

        strategyFactory.forType(tournament.getType()); // fail fast if unsupported

        Round round = ensureRound(tournament, roundNumber);
        if (round.getStatus() != RoundStatus.PENDING_PAIRINGS) {
            throw new ValidationException(
                    "Round " + roundNumber + " is not PENDING_PAIRINGS (status="
                            + round.getStatus() + ")");
        }

        List<Game> previousRoundGames = List.of();
        if (roundNumber > 1) {
            Round previous = roundDao.findByTournamentAndNumber(tournamentId, roundNumber - 1)
                    .orElseThrow(() -> new ValidationException(
                            "Previous round " + (roundNumber - 1) + " does not exist"));
            if (previous.getStatus() != RoundStatus.COMPLETED) {
                throw new ValidationException(
                        "Cannot pair round " + roundNumber
                                + " until round " + (roundNumber - 1) + " is COMPLETED");
            }
            previousRoundGames = gameDao.findByRound(previous.getId());
        }

        List<TournamentPlayer> players = tournamentPlayerDao.findByTournament(tournamentId);
        if (players.isEmpty()) {
            throw new ValidationException("No players enrolled");
        }

        Set<LongPair> previousPairings = gameDao.findPreviousPairings(tournamentId);
        PairingContext context = new PairingContext(
                tournament, roundNumber, players, previousPairings, List.of(), previousRoundGames);
        return new LoadedContext(tournament, round, players, context);
    }

    private Round ensureRound(Tournament tournament, int roundNumber) {
        Optional<Round> existing = roundDao.findByTournamentAndNumber(
                tournament.getId(), roundNumber);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (roundNumber < 1 || roundNumber > tournament.getRoundsPlanned()) {
            throw new ValidationException(
                    "Round " + roundNumber + " is outside planned rounds 1.."
                            + tournament.getRoundsPlanned());
        }
        if (roundNumber == 1) {
            throw new NotFoundException("Round 1 missing — start the tournament first");
        }
        Round previous = roundDao.findByTournamentAndNumber(tournament.getId(), roundNumber - 1)
                .orElseThrow(() -> new ValidationException(
                        "Cannot create round " + roundNumber + " without previous round"));
        if (previous.getStatus() != RoundStatus.COMPLETED) {
            throw new ValidationException(
                    "Cannot create round " + roundNumber
                            + " until round " + (roundNumber - 1) + " is COMPLETED");
        }

        Round created = new Round();
        created.setTournamentId(tournament.getId());
        created.setRoundNumber(roundNumber);
        created.setStatus(RoundStatus.PENDING_PAIRINGS);
        long id = roundDao.insert(created);
        created.setId(id);
        return created;
    }

    private Tournament requireTournament(long tournamentId) {
        return tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));
    }

    static void validateProposals(List<PairingProposal> proposals,
                                  List<TournamentPlayer> players,
                                  TournamentType type,
                                  int roundNumber) {
        if (proposals == null || proposals.isEmpty()) {
            throw new ValidationException("No pairings to publish");
        }
        Set<Long> enrolled = players.stream()
                .map(TournamentPlayer::getId)
                .collect(Collectors.toSet());
        Set<Long> active = players.stream()
                .filter(tp -> tp.getQualificationStatus() != QualificationStatus.ELIMINATED)
                .map(TournamentPlayer::getId)
                .collect(Collectors.toSet());

        Set<Long> seen = new HashSet<>();
        Set<Integer> boards = new HashSet<>();
        int byeCount = 0;

        for (PairingProposal p : proposals) {
            if (!boards.add(p.boardNumber())) {
                throw new ValidationException("Duplicate board number: " + p.boardNumber());
            }
            if (p.bye()) {
                byeCount++;
                assertPlayerOnce(p.whiteTpId(), seen, enrolled);
            } else {
                assertPlayerOnce(p.whiteTpId(), seen, enrolled);
                assertPlayerOnce(p.blackTpId(), seen, enrolled);
            }
        }

        if (type == TournamentType.KNOCKOUT) {
            if (roundNumber == 1) {
                if (seen.size() != enrolled.size()) {
                    throw new ValidationException(
                            "Knockout round 1 must include every enrolled player exactly once");
                }
            } else {
                // Subsequent KO rounds: only remaining winners; no eliminated players
                for (Long id : seen) {
                    if (!active.contains(id)) {
                        throw new ValidationException(
                                "Eliminated player cannot be paired: " + id);
                    }
                }
                if (byeCount > 0) {
                    throw new ValidationException(
                            "Knockout rounds after round 1 should not have byes");
                }
            }
        } else {
            if (byeCount > 1) {
                throw new ValidationException("At most one bye per round");
            }
            if (seen.size() != enrolled.size()) {
                throw new ValidationException(
                        "Pairings must include every enrolled player exactly once");
            }
        }
    }

    private static void assertPlayerOnce(Long tpId, Set<Long> seen, Set<Long> enrolled) {
        if (tpId == null || !enrolled.contains(tpId)) {
            throw new ValidationException("Unknown tournament player in pairing: " + tpId);
        }
        if (!seen.add(tpId)) {
            throw new ValidationException("Player paired more than once in round: " + tpId);
        }
    }

    private static List<Game> toGames(long roundId, List<PairingProposal> proposals) {
        List<Game> games = new ArrayList<>(proposals.size());
        for (PairingProposal p : proposals) {
            Game game = new Game();
            game.setRoundId(roundId);
            game.setBoardNumber(p.boardNumber());
            game.setWhiteTournamentPlayerId(p.whiteTpId());
            game.setBlackTournamentPlayerId(p.blackTpId());
            game.setRematch(false);
            if (p.bye()) {
                game.setResult(GameResult.BYE);
                game.setWhiteScore(BigDecimal.ONE);
                game.setBlackScore(null);
            } else {
                game.setResult(GameResult.PENDING);
            }
            games.add(game);
        }
        return games;
    }

    private record LoadedContext(
            Tournament tournament,
            Round round,
            List<TournamentPlayer> players,
            PairingContext context
    ) {
    }
}
