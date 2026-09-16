package com.chess.tournament.integration;

import com.chess.tournament.dao.JdbcUnitOfWork;
import com.chess.tournament.dao.impl.JdbcGameDao;
import com.chess.tournament.dao.impl.JdbcPlayerDao;
import com.chess.tournament.dao.impl.JdbcRoundDao;
import com.chess.tournament.dao.impl.JdbcTournamentDao;
import com.chess.tournament.dao.impl.JdbcTournamentPlayerDao;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import com.chess.tournament.service.pairing.PairingStrategyFactory;
import com.chess.tournament.service.pairing.RoundRobinScheduleGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundRobinPairingIntegrationTest extends AbstractPostgresIntegrationTest {

    private TournamentService tournamentService;
    private EnrollmentService enrollmentService;
    private PairingService pairingService;
    private JdbcRoundDao roundDao;
    private JdbcPlayerDao playerDao;
    private JdbcGameDao gameDao;

    @BeforeEach
    void setUpServices() {
        JdbcTournamentDao tournamentDao = new JdbcTournamentDao(getDataSource());
        JdbcTournamentPlayerDao tournamentPlayerDao = new JdbcTournamentPlayerDao(getDataSource());
        roundDao = new JdbcRoundDao(getDataSource());
        playerDao = new JdbcPlayerDao(getDataSource());
        gameDao = new JdbcGameDao(getDataSource());
        JdbcUnitOfWork unitOfWork = new JdbcUnitOfWork(getDataSource());

        tournamentService = new TournamentService(tournamentDao, tournamentPlayerDao, roundDao, unitOfWork);
        enrollmentService = new EnrollmentService(
                tournamentDao, tournamentPlayerDao, playerDao, roundDao, gameDao);
        pairingService = new PairingService(
                tournamentDao, tournamentPlayerDao, roundDao, gameDao, unitOfWork,
                new PairingStrategyFactory());
    }

    @Test
    void publishRound1_createsFloorNOver2Games() {
        int n = 4;
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "RR Pairings", TournamentType.ROUND_ROBIN,
                RoundRobinScheduleGenerator.expectedRoundCount(n), 0, null));

        for (int i = 1; i <= n; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("RR" + i, 1500 + i));
        }
        tournamentService.start(tournament.getId());

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);

        assertEquals(n / 2, games.size());
        assertTrue(games.stream().allMatch(g -> g.getResult() == GameResult.PENDING));
        assertTrue(games.stream().noneMatch(Game::isRematch));

        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        assertEquals(RoundStatus.PAIRINGS_PUBLISHED, round1.getStatus());
        assertNotNull(round1.getPairedAt());
    }

    @Test
    void publishAllRounds_sequentially_coversFullSchedule() {
        int n = 4;
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "RR Full", TournamentType.ROUND_ROBIN,
                RoundRobinScheduleGenerator.expectedRoundCount(n), 0, null));

        for (int i = 1; i <= n; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("Full" + i, 1600 + i));
        }
        tournamentService.start(tournament.getId());

        int rounds = RoundRobinScheduleGenerator.expectedRoundCount(n);
        Set<String> uniquePairs = new HashSet<>();

        for (int r = 1; r <= rounds; r++) {
            List<Game> games = pairingService.generateAndPublish(tournament.getId(), r);
            assertEquals(n / 2, games.size());
            for (Game g : games) {
                long w = g.getWhiteTournamentPlayerId();
                long b = g.getBlackTournamentPlayerId();
                uniquePairs.add(Math.min(w, b) + "-" + Math.max(w, b));
            }
            Round round = roundDao.findByTournamentAndNumber(tournament.getId(), r).orElseThrow();
            roundDao.updateStatus(round.getId(), RoundStatus.COMPLETED, round.getPairedAt(), Instant.now());
        }

        assertEquals(n * (n - 1) / 2, uniquePairs.size());
    }

    @Test
    void publishRound1_oddN_includesByeGame() {
        int n = 5;
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "RR Odd", TournamentType.ROUND_ROBIN,
                RoundRobinScheduleGenerator.expectedRoundCount(n), 0, null));

        for (int i = 1; i <= n; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("Odd" + i, 1400 + i));
        }
        tournamentService.start(tournament.getId());

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);

        assertEquals(n / 2 + 1, games.size()); // floor(n/2) games + 1 bye
        long byeCount = games.stream().filter(g -> g.getResult() == GameResult.BYE).count();
        assertEquals(1, byeCount);
        Game bye = games.stream().filter(g -> g.getResult() == GameResult.BYE).findFirst().orElseThrow();
        assertNotNull(bye.getWhiteTournamentPlayerId());
        assertNull(bye.getBlackTournamentPlayerId());
    }

    private long insertPlayer(String name, int rating) {
        Player player = new Player();
        player.setName(name);
        player.setGlobalRating(rating);
        return playerDao.insert(player);
    }
}
