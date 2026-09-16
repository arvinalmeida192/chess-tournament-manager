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
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.exception.NotFoundException;
import com.chess.tournament.exception.ValidationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enrolls global players into tournaments per BR-ENR-001.
 */
public final class EnrollmentService {

    private final TournamentDao tournamentDao;
    private final TournamentPlayerDao tournamentPlayerDao;
    private final PlayerDao playerDao;
    private final RoundDao roundDao;
    private final GameDao gameDao;

    public EnrollmentService(TournamentDao tournamentDao,
                             TournamentPlayerDao tournamentPlayerDao,
                             PlayerDao playerDao,
                             RoundDao roundDao,
                             GameDao gameDao) {
        this.tournamentDao = Objects.requireNonNull(tournamentDao);
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.playerDao = Objects.requireNonNull(playerDao);
        this.roundDao = Objects.requireNonNull(roundDao);
        this.gameDao = Objects.requireNonNull(gameDao);
    }

    public TournamentPlayer enroll(long tournamentId, long playerId) {
        Tournament tournament = requireTournament(tournamentId);
        assertEnrollmentAllowed(tournament);

        Player player = playerDao.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found: " + playerId));
        if (!player.isActive()) {
            throw new ValidationException("Cannot enroll inactive player: " + player.getName());
        }

        boolean alreadyEnrolled = tournamentPlayerDao.findByTournament(tournamentId).stream()
                .anyMatch(tp -> tp.getPlayerId() == playerId);
        if (alreadyEnrolled) {
            throw new ValidationException("Player is already enrolled in this tournament");
        }

        TournamentPlayer tp = new TournamentPlayer();
        tp.setTournamentId(tournamentId);
        tp.setPlayerId(playerId);
        tp.setStartRating(player.getGlobalRating());
        tp.setCurrentRating(player.getGlobalRating());
        tp.setPoints(BigDecimal.ZERO);
        tp.setWins(0);
        tp.setDraws(0);
        tp.setLosses(0);
        tp.setGamesPlayed(0);
        tp.setQualificationStatus(QualificationStatus.PENDING);
        tp.setColorBalance(0);

        long id = tournamentPlayerDao.insert(tp);
        tp.setId(id);
        return tp;
    }

    public void unenroll(long tournamentId, long playerId) {
        Tournament tournament = requireTournament(tournamentId);
        assertEnrollmentAllowed(tournament);

        if (gameDao.existsForTournament(tournamentId)) {
            throw new ValidationException("Cannot remove enrollment after games exist for this tournament");
        }

        boolean enrolled = tournamentPlayerDao.findByTournament(tournamentId).stream()
                .anyMatch(tp -> tp.getPlayerId() == playerId);
        if (!enrolled) {
            throw new NotFoundException("Player is not enrolled in tournament " + tournamentId);
        }

        tournamentPlayerDao.deleteByTournamentAndPlayer(tournamentId, playerId);
    }

    public List<TournamentPlayer> listEnrolled(long tournamentId) {
        requireTournament(tournamentId);
        return tournamentPlayerDao.findByTournament(tournamentId);
    }

    /**
     * Active global players not yet enrolled in the tournament.
     */
    public List<Player> listAvailablePlayers(long tournamentId) {
        requireTournament(tournamentId);
        Set<Long> enrolledIds = tournamentPlayerDao.findByTournament(tournamentId).stream()
                .map(TournamentPlayer::getPlayerId)
                .collect(Collectors.toSet());
        return playerDao.findAll(true).stream()
                .filter(p -> !enrolledIds.contains(p.getId()))
                .toList();
    }

    public boolean isEnrollmentLocked(long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        return !isEnrollmentAllowed(tournament);
    }

    private Tournament requireTournament(long tournamentId) {
        return tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));
    }

    private void assertEnrollmentAllowed(Tournament tournament) {
        if (!isEnrollmentAllowed(tournament)) {
            throw new ValidationException(
                    "Enrollment is locked (allowed in DRAFT anytime, or ACTIVE only while round 1 is PENDING_PAIRINGS)");
        }
    }

    private boolean isEnrollmentAllowed(Tournament tournament) {
        TournamentStatus status = tournament.getStatus();
        if (status == TournamentStatus.DRAFT) {
            return true;
        }
        if (status == TournamentStatus.ACTIVE) {
            Optional<Round> round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1);
            return round1.isPresent() && round1.get().getStatus() == RoundStatus.PENDING_PAIRINGS;
        }
        return false;
    }
}
