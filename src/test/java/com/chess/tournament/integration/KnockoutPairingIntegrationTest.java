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
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.KnockoutAdvancementService;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import com.chess.tournament.service.pairing.KnockoutSeeding;
import com.chess.tournament.service.pairing.PairingStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockoutPairingIntegrationTest extends AbstractPostgresIntegrationTest {

    private TournamentService tournamentService;
    private EnrollmentService enrollmentService;
    private PairingService pairingService;
    private KnockoutAdvancementService advancementService;
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
        advancementService = new KnockoutAdvancementService(tournamentPlayerDao, unitOfWork);
    }

    @Test
    void publishRound1_eightPlayers_fourGames() {
        int n = 8;
        Tournament tournament = startKo(n);

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);

        assertEquals(4, games.size());
        assertTrue(games.stream().allMatch(g -> g.getResult() == GameResult.PENDING));
        assertTrue(games.stream().noneMatch(g -> g.getBlackTournamentPlayerId() == null));

        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        assertEquals(RoundStatus.PAIRINGS_PUBLISHED, round1.getStatus());
    }

    @Test
    void publishRound1_sixPlayers_includesTwoByes() {
        int n = 6;
        Tournament tournament = startKo(n);

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);

        assertEquals(4, games.size());
        long byes = games.stream().filter(g -> g.getResult() == GameResult.BYE).count();
        assertEquals(2, byes);
        for (Game bye : games.stream().filter(g -> g.getResult() == GameResult.BYE).toList()) {
            assertNull(bye.getBlackTournamentPlayerId());
            assertEquals(GameResult.BYE, bye.getResult());
        }
    }

    @Test
    void markLosers_thenPairRound2_fromWinners() {
        Tournament tournament = startKo(4);
        List<Game> r1 = pairingService.generateAndPublish(tournament.getId(), 1);
        assertEquals(2, r1.size());

        // Complete round 1 with white wins
        for (Game g : r1) {
            g.setResult(GameResult.WHITE_WIN);
            gameDao.updateResult(g);
        }
        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        roundDao.updateStatus(round1.getId(), RoundStatus.COMPLETED, round1.getPairedAt(), Instant.now());

        List<Long> losers = advancementService.markLosers(r1);
        assertEquals(2, losers.size());

        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournament.getId());
        long eliminated = tps.stream()
                .filter(tp -> tp.getQualificationStatus() == QualificationStatus.ELIMINATED)
                .count();
        assertEquals(2, eliminated);

        List<Game> r2 = pairingService.generateAndPublish(tournament.getId(), 2);
        assertEquals(1, r2.size());
        assertEquals(GameResult.PENDING, r2.get(0).getResult());
    }

    private Tournament startKo(int n) {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "KO " + n, TournamentType.KNOCKOUT,
                KnockoutSeeding.expectedRounds(n), 1, null));
        for (int i = 1; i <= n; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("KO" + i, 1800 - i * 20));
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
}
