package com.chess.tournament.integration;

import com.chess.tournament.dao.JdbcUnitOfWork;
import com.chess.tournament.dao.impl.JdbcGameDao;
import com.chess.tournament.dao.impl.JdbcPlayerDao;
import com.chess.tournament.dao.impl.JdbcRoundDao;
import com.chess.tournament.dao.impl.JdbcTournamentDao;
import com.chess.tournament.dao.impl.JdbcTournamentPlayerDao;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentEnrollmentIntegrationTest extends AbstractPostgresIntegrationTest {

    private TournamentService tournamentService;
    private EnrollmentService enrollmentService;
    private JdbcRoundDao roundDao;
    private JdbcPlayerDao playerDao;

    @BeforeEach
    void setUpServices() {
        JdbcTournamentDao tournamentDao = new JdbcTournamentDao(getDataSource());
        JdbcTournamentPlayerDao tournamentPlayerDao = new JdbcTournamentPlayerDao(getDataSource());
        roundDao = new JdbcRoundDao(getDataSource());
        playerDao = new JdbcPlayerDao(getDataSource());
        JdbcGameDao gameDao = new JdbcGameDao(getDataSource());
        JdbcUnitOfWork unitOfWork = new JdbcUnitOfWork(getDataSource());

        tournamentService = new TournamentService(tournamentDao, tournamentPlayerDao, roundDao, unitOfWork);
        enrollmentService = new EnrollmentService(
                tournamentDao, tournamentPlayerDao, playerDao, roundDao, gameDao);
    }

    @Test
    void createEnrollStart_createsRound1() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Swiss Cup", TournamentType.SWISS, 5, 2, SwissFirstRoundMethod.RANDOM));

        for (int i = 1; i <= 4; i++) {
            long playerId = insertPlayer("P" + i, 1500 + i * 10);
            enrollmentService.enroll(tournament.getId(), playerId);
        }

        tournamentService.start(tournament.getId());

        Tournament loaded = tournamentService.findById(tournament.getId()).orElseThrow();
        assertEquals(TournamentStatus.ACTIVE, loaded.getStatus());

        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        assertEquals(1, round1.getRoundNumber());
        assertEquals(RoundStatus.PENDING_PAIRINGS, round1.getStatus());
        assertEquals(4, enrollmentService.listEnrolled(tournament.getId()).size());
    }

    @Test
    void enroll_samePlayerTwiceFails() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "RR Event", TournamentType.ROUND_ROBIN, 3, 0, null));
        long playerId = insertPlayer("Dup", 1600);
        enrollmentService.enroll(tournament.getId(), playerId);

        assertThrows(ValidationException.class,
                () -> enrollmentService.enroll(tournament.getId(), playerId));
    }

    @Test
    void enrollmentLocked_whenRound1NotPendingPairings() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "KO Bracket", TournamentType.KNOCKOUT, 2, 1, null));
        enrollmentService.enroll(tournament.getId(), insertPlayer("A", 1500));
        enrollmentService.enroll(tournament.getId(), insertPlayer("B", 1600));
        enrollmentService.enroll(tournament.getId(), insertPlayer("C", 1550));
        enrollmentService.enroll(tournament.getId(), insertPlayer("D", 1650));

        tournamentService.start(tournament.getId());

        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        roundDao.updateStatus(round1.getId(), RoundStatus.PAIRINGS_PUBLISHED, Instant.now(), null);

        long extra = insertPlayer("E", 1400);
        assertThrows(ValidationException.class,
                () -> enrollmentService.enroll(tournament.getId(), extra));
        assertTrue(enrollmentService.isEnrollmentLocked(tournament.getId()));
    }

    private long insertPlayer(String name, int rating) {
        Player player = new Player();
        player.setName(name);
        player.setGlobalRating(rating);
        return playerDao.insert(player);
    }
}
