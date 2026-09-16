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
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import com.chess.tournament.service.pairing.PairingStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwissPairingIntegrationTest extends AbstractPostgresIntegrationTest {

    private TournamentService tournamentService;
    private EnrollmentService enrollmentService;
    private PairingService pairingService;
    private JdbcRoundDao roundDao;
    private JdbcPlayerDao playerDao;
    private JdbcTournamentPlayerDao tournamentPlayerDao;
    private JdbcGameDao gameDao;

    @BeforeEach
    void setUpServices() {
        JdbcTournamentDao tournamentDao = new JdbcTournamentDao(getDataSource());
        tournamentPlayerDao = new JdbcTournamentPlayerDao(getDataSource());
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
    void publishRound1_random_eightPlayers() {
        Tournament tournament = startSwiss(8, SwissFirstRoundMethod.RANDOM, 3);

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);

        assertEquals(4, games.size());
        assertTrue(games.stream().allMatch(g -> g.getResult() == GameResult.PENDING));
        assertTrue(games.stream().noneMatch(Game::isRematch));

        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        assertEquals(RoundStatus.PAIRINGS_PUBLISHED, round1.getStatus());
    }

    @Test
    void publishRound1_updatesColorBalance() {
        Tournament tournament = startSwiss(4, SwissFirstRoundMethod.RATING_SPLIT, 3);

        pairingService.generateAndPublish(tournament.getId(), 1);

        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournament.getId());
        int sum = tps.stream().mapToInt(TournamentPlayer::getColorBalance).sum();
        assertEquals(0, sum, "White +1 and black -1 should cancel across the field");
        assertTrue(tps.stream().anyMatch(tp -> tp.getColorBalance() == 1));
        assertTrue(tps.stream().anyMatch(tp -> tp.getColorBalance() == -1));
    }

    @Test
    void publishRound1ThenRound2_afterSimulatedResults() {
        Tournament tournament = startSwiss(8, SwissFirstRoundMethod.RATING_SPLIT, 3);

        List<Game> r1 = pairingService.generateAndPublish(tournament.getId(), 1);
        assertEquals(4, r1.size());

        // Simulate white wins and award points (Phase 8 ResultService not yet available)
        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournament.getId());
        for (Game g : r1) {
            g.setResult(GameResult.WHITE_WIN);
            g.setWhiteScore(BigDecimal.ONE);
            g.setBlackScore(BigDecimal.ZERO);
            gameDao.updateResult(g);

            TournamentPlayer white = findTp(tps, g.getWhiteTournamentPlayerId());
            TournamentPlayer black = findTp(tps, g.getBlackTournamentPlayerId());
            white.setPoints(white.getPoints().add(BigDecimal.ONE));
            white.setWins(white.getWins() + 1);
            white.setGamesPlayed(white.getGamesPlayed() + 1);
            black.setLosses(black.getLosses() + 1);
            black.setGamesPlayed(black.getGamesPlayed() + 1);
            tournamentPlayerDao.updateStats(white);
            tournamentPlayerDao.updateStats(black);
        }

        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        roundDao.updateStatus(round1.getId(), RoundStatus.COMPLETED, round1.getPairedAt(), Instant.now());

        List<Game> r2 = pairingService.generateAndPublish(tournament.getId(), 2);
        assertEquals(4, r2.size());
        assertTrue(r2.stream().noneMatch(g -> g.getResult() == GameResult.BYE));

        Set<String> r1Pairs = pairKeys(r1);
        for (Game g : r2) {
            assertFalse(r1Pairs.contains(pairKey(g.getWhiteTournamentPlayerId(), g.getBlackTournamentPlayerId())),
                    "Round 2 should avoid rematches when alternatives exist");
            assertFalse(g.isRematch());
        }

        Round round2 = roundDao.findByTournamentAndNumber(tournament.getId(), 2).orElseThrow();
        assertEquals(RoundStatus.PAIRINGS_PUBLISHED, round2.getStatus());
    }

    @Test
    void publishRound1_oddField_singleBye() {
        Tournament tournament = startSwiss(5, SwissFirstRoundMethod.RATING_SPLIT, 3);

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);

        assertEquals(3, games.size());
        long byes = games.stream().filter(g -> g.getResult() == GameResult.BYE).count();
        assertEquals(1, byes);
    }

    private Tournament startSwiss(int n, SwissFirstRoundMethod method, int rounds) {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Swiss " + n, TournamentType.SWISS, rounds, 2, method));
        for (int i = 1; i <= n; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("SW" + i, 1800 - i * 20));
        }
        tournamentService.start(tournament.getId());
        return tournament;
    }

    private long insertPlayer(String name, int rating) {
        Player player = new Player();
        player.setName(name);
        player.setGlobalRating(rating);
        return playerDao.insert(player);
    }

    private static TournamentPlayer findTp(List<TournamentPlayer> tps, long id) {
        return tps.stream().filter(tp -> tp.getId() == id).findFirst().orElseThrow();
    }

    private static Set<String> pairKeys(List<Game> games) {
        Set<String> keys = new HashSet<>();
        for (Game g : games) {
            if (g.getBlackTournamentPlayerId() != null) {
                keys.add(pairKey(g.getWhiteTournamentPlayerId(), g.getBlackTournamentPlayerId()));
            }
        }
        return keys;
    }

    private static String pairKey(long a, long b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }
}
