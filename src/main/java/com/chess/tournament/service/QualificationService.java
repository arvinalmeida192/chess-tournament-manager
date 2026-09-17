package com.chess.tournament.service;

import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.dao.UnitOfWork;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.exception.NotFoundException;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.leaderboard.StandingRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Marks QUALIFIED / ELIMINATED / NOT_APPLICABLE from standings (SDD §7.8, SRS FR-QLF-*).
 * Must only run after every planned round is completed — mid-event application would
 * mark active knockout winners as ELIMINATED and block further pairings.
 */
public final class QualificationService {

    private static final Logger log = LoggerFactory.getLogger(QualificationService.class);

    private final TournamentDao tournamentDao;
    private final TournamentPlayerDao tournamentPlayerDao;
    private final LeaderboardService leaderboardService;
    private final UnitOfWork unitOfWork;
    private final RoundDao roundDao;

    public QualificationService(TournamentDao tournamentDao,
                                TournamentPlayerDao tournamentPlayerDao,
                                LeaderboardService leaderboardService,
                                UnitOfWork unitOfWork,
                                RoundDao roundDao) {
        this.tournamentDao = Objects.requireNonNull(tournamentDao);
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.leaderboardService = Objects.requireNonNull(leaderboardService);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.roundDao = roundDao;
    }

    /**
     * Backward-compatible constructor for tests that do not exercise the all-rounds guard
     * via RoundDao (guard still runs if rounds can be listed through tournamentDao path).
     */
    public QualificationService(TournamentDao tournamentDao,
                                TournamentPlayerDao tournamentPlayerDao,
                                LeaderboardService leaderboardService,
                                UnitOfWork unitOfWork) {
        this(tournamentDao, tournamentPlayerDao, leaderboardService, unitOfWork, null);
    }

    /**
     * Applies qualification using the latest completed-round standings.
     * Top {@code qualifiersCount} → QUALIFIED; others → ELIMINATED;
     * if Q = 0, all → NOT_APPLICABLE.
     */
    public void applyQualification(long tournamentId) {
        Tournament tournament = tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));

        if (tournament.getStatus() != TournamentStatus.ACTIVE
                && tournament.getStatus() != TournamentStatus.COMPLETED) {
            throw new ValidationException("Qualification requires an ACTIVE or COMPLETED tournament");
        }
        if (!areAllRoundsComplete(tournament)) {
            throw new ValidationException(
                    "Cannot apply qualification until all "
                            + tournament.getRoundsPlanned()
                            + " planned rounds are completed");
        }

        List<StandingRow> standings = leaderboardService.getLeaderboard(tournamentId, null);
        if (standings.isEmpty()) {
            throw new ValidationException("No enrolled players to qualify");
        }
        if (leaderboardService.listCompletedRoundNumbers(tournamentId).isEmpty()) {
            throw new ValidationException("Cannot apply qualification before any round is completed");
        }

        int q = tournament.getQualifiersCount();
        List<TournamentPlayer> players = tournamentPlayerDao.findByTournament(tournamentId);

        unitOfWork.executeInTransaction(connection -> {
            if (q <= 0) {
                for (TournamentPlayer tp : players) {
                    tournamentPlayerDao.updateQualification(
                            connection, tp.getId(), QualificationStatus.NOT_APPLICABLE);
                }
                log.info("Tournament {} qualification: all NOT_APPLICABLE (Q=0)", tournamentId);
                return null;
            }

            Set<Long> qualifiedIds = new HashSet<>();
            int awarded = 0;
            for (StandingRow row : standings) {
                if (awarded >= q) {
                    break;
                }
                qualifiedIds.add(row.getTournamentPlayerId());
                awarded++;
            }

            for (TournamentPlayer tp : players) {
                if (qualifiedIds.contains(tp.getId())) {
                    tournamentPlayerDao.updateQualification(
                            connection, tp.getId(), QualificationStatus.QUALIFIED);
                } else {
                    tournamentPlayerDao.updateQualification(
                            connection, tp.getId(), QualificationStatus.ELIMINATED);
                }
            }
            log.info("Tournament {} qualification: top {} QUALIFIED", tournamentId, qualifiedIds.size());
            return null;
        });
    }

    private boolean areAllRoundsComplete(Tournament tournament) {
        if (roundDao == null) {
            return true; // tests without RoundDao keep prior behaviour
        }
        List<Round> rounds = roundDao.findByTournament(tournament.getId());
        if (rounds.size() < tournament.getRoundsPlanned()) {
            return false;
        }
        for (int n = 1; n <= tournament.getRoundsPlanned(); n++) {
            final int roundNumber = n;
            boolean complete = rounds.stream()
                    .filter(r -> r.getRoundNumber() == roundNumber)
                    .anyMatch(r -> r.getStatus() == RoundStatus.COMPLETED);
            if (!complete) {
                return false;
            }
        }
        return true;
    }
}
