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
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.KnockoutAdvancementService;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.RatingService;
import com.chess.tournament.service.ResultService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import com.chess.tournament.service.pairing.KnockoutSeeding;
import com.chess.tournament.service.pairing.PairingStrategyFactory;
import com.chess.tournament.service.pairing.RoundRobinScheduleGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    private TournamentService tournamentService;
    private EnrollmentService enrollmentService;
    private PairingService pairingService;
    private ResultService resultService;
    private JdbcRoundDao roundDao;
    private JdbcPlayerDao playerDao;
    private JdbcTournamentPlayerDao tournamentPlayerDao;
    private JdbcGameDao gameDao;
    private JdbcUnitOfWork unitOfWork;
    private JdbcTournamentDao tournamentDao;
    private KnockoutAdvancementService knockoutAdvancementService;

    @BeforeEach
    void setUpServices() {
        tournamentDao = new JdbcTournamentDao(getDataSource());
        tournamentPlayerDao = new JdbcTournamentPlayerDao(getDataSource());
        roundDao = new JdbcRoundDao(getDataSource());
        playerDao = new JdbcPlayerDao(getDataSource());
        gameDao = new JdbcGameDao(getDataSource());
        unitOfWork = new JdbcUnitOfWork(getDataSource());
        knockoutAdvancementService = new KnockoutAdvancementService(tournamentPlayerDao, unitOfWork);

        tournamentService = new TournamentService(tournamentDao, tournamentPlayerDao, roundDao, unitOfWork);
        enrollmentService = new EnrollmentService(
                tournamentDao, tournamentPlayerDao, playerDao, roundDao, gameDao);
        pairingService = new PairingService(
                tournamentDao, tournamentPlayerDao, roundDao, gameDao, unitOfWork,
                new PairingStrategyFactory());
        resultService = new ResultService(
                tournamentDao, tournamentPlayerDao, roundDao, gameDao, playerDao,
                unitOfWork, new RatingService(), knockoutAdvancementService);
    }

    @Test
    void completeRound_twoPlayers_whiteWin_pointsAndElo() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Elo Duel", TournamentType.SWISS, 3, 1, SwissFirstRoundMethod.RANDOM));
        long whitePlayerId = insertPlayer("Alice", 1500);
        long blackPlayerId = insertPlayer("Bob", 1500);
        enrollmentService.enroll(tournament.getId(), whitePlayerId);
        enrollmentService.enroll(tournament.getId(), blackPlayerId);
        tournamentService.start(tournament.getId());

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);
        assertEquals(1, games.size());
        Game game = games.get(0);

        resultService.saveGameResult(game.getId(), GameResult.WHITE_WIN);
        resultService.completeRound(tournament.getId(), 1);

        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournament.getId());
        TournamentPlayer whiteTp = findTp(tps, game.getWhiteTournamentPlayerId());
        TournamentPlayer blackTp = findTp(tps, game.getBlackTournamentPlayerId());

        assertEquals(0, whiteTp.getPoints().compareTo(BigDecimal.ONE));
        assertEquals(0, blackTp.getPoints().compareTo(BigDecimal.ZERO));
        assertEquals(1, whiteTp.getWins());
        assertEquals(1, blackTp.getLosses());
        assertEquals(1516, whiteTp.getCurrentRating());
        assertEquals(1484, blackTp.getCurrentRating());

        assertEquals(1516, playerDao.findById(whiteTp.getPlayerId()).orElseThrow().getGlobalRating());
        assertEquals(1484, playerDao.findById(blackTp.getPlayerId()).orElseThrow().getGlobalRating());

        Game stored = gameDao.findById(game.getId()).orElseThrow();
        assertEquals(16, stored.getWhiteRatingDelta());
        assertEquals(-16, stored.getBlackRatingDelta());

        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        assertEquals(RoundStatus.COMPLETED, round1.getStatus());
    }

    @Test
    void completeRound_rollsBackWhenRatingFailsMidway() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Rollback Swiss", TournamentType.SWISS, 3, 1, SwissFirstRoundMethod.RANDOM));
        for (int i = 1; i <= 4; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("P" + i, 1500));
        }
        tournamentService.start(tournament.getId());
        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);
        assertEquals(2, games.size());
        for (Game g : games) {
            resultService.saveGameResult(g.getId(), GameResult.WHITE_WIN);
        }

        AtomicInteger calls = new AtomicInteger();
        RatingService failing = new RatingService() {
            @Override
            public RatingOutcome rate(int playerRating, int opponentRating, double actualScore) {
                if (calls.incrementAndGet() > 2) {
                    throw new IllegalStateException("forced rating failure");
                }
                return super.rate(playerRating, opponentRating, actualScore);
            }
        };
        ResultService fragile = new ResultService(
                tournamentDao, tournamentPlayerDao, roundDao, gameDao, playerDao,
                unitOfWork, failing, knockoutAdvancementService);

        List<TournamentPlayer> before = tournamentPlayerDao.findByTournament(tournament.getId());
        assertThrows(IllegalStateException.class,
                () -> fragile.completeRound(tournament.getId(), 1));

        List<TournamentPlayer> after = tournamentPlayerDao.findByTournament(tournament.getId());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(0, before.get(i).getPoints().compareTo(after.get(i).getPoints()));
            assertEquals(before.get(i).getCurrentRating(), after.get(i).getCurrentRating());
            assertEquals(before.get(i).getWins(), after.get(i).getWins());
        }
        Round round1 = roundDao.findByTournamentAndNumber(tournament.getId(), 1).orElseThrow();
        assertNotEquals(RoundStatus.COMPLETED, round1.getStatus());
    }

    @Test
    void completeRound_rejectsWhileResultsMissing() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Incomplete", TournamentType.SWISS, 3, 1, SwissFirstRoundMethod.RANDOM));
        enrollmentService.enroll(tournament.getId(), insertPlayer("A", 1500));
        enrollmentService.enroll(tournament.getId(), insertPlayer("B", 1500));
        tournamentService.start(tournament.getId());
        pairingService.generateAndPublish(tournament.getId(), 1);

        assertThrows(ValidationException.class,
                () -> resultService.completeRound(tournament.getId(), 1));
    }

    @Test
    void completeRound_knockout_marksLosersEliminated() {
        int n = 4;
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "KO Results", TournamentType.KNOCKOUT,
                KnockoutSeeding.expectedRounds(n), 1, null));
        for (int i = 1; i <= n; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("KO" + i, 1800 - i * 10));
        }
        tournamentService.start(tournament.getId());
        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);
        for (Game g : games) {
            resultService.saveGameResult(g.getId(), GameResult.WHITE_WIN);
        }
        resultService.completeRound(tournament.getId(), 1);

        long eliminated = tournamentPlayerDao.findByTournament(tournament.getId()).stream()
                .filter(tp -> tp.getQualificationStatus() == QualificationStatus.ELIMINATED)
                .count();
        assertEquals(2, eliminated);
    }

    @Test
    void completeRound_byeAwardsPointWithoutRatingChange() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Bye Swiss", TournamentType.SWISS, 3, 1, SwissFirstRoundMethod.RATING_SPLIT));
        for (int i = 1; i <= 3; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("B" + i, 1500 + i));
        }
        tournamentService.start(tournament.getId());
        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);
        Game bye = games.stream().filter(g -> g.getResult() == GameResult.BYE).findFirst().orElseThrow();
        Game real = games.stream().filter(g -> g.getResult() == GameResult.PENDING).findFirst().orElseThrow();
        resultService.saveGameResult(real.getId(), GameResult.DRAW);
        resultService.completeRound(tournament.getId(), 1);

        TournamentPlayer byeTp = findTp(
                tournamentPlayerDao.findByTournament(tournament.getId()),
                bye.getWhiteTournamentPlayerId());
        assertEquals(0, byeTp.getPoints().compareTo(BigDecimal.ONE));
        assertEquals(1, byeTp.getWins());
        assertEquals(byeTp.getStartRating(), byeTp.getCurrentRating());
        assertTrue(gameDao.findById(bye.getId()).orElseThrow().getWhiteRatingDelta() == null);
    }

    @Test
    void saveGameResult_blockedAfterLaterRoundPublished() {
        // Use RR 4 players so we can complete R1 and publish R2
        int n = 4;
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Edit Lock", TournamentType.ROUND_ROBIN,
                RoundRobinScheduleGenerator.expectedRoundCount(n), 0, null));
        for (int i = 1; i <= n; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("RR" + i, 1500 + i));
        }
        tournamentService.start(tournament.getId());
        List<Game> r1 = pairingService.generateAndPublish(tournament.getId(), 1);
        for (Game g : r1) {
            resultService.saveGameResult(g.getId(), GameResult.WHITE_WIN);
        }
        resultService.completeRound(tournament.getId(), 1);
        pairingService.generateAndPublish(tournament.getId(), 2);

        Game first = r1.get(0);
        assertThrows(ValidationException.class,
                () -> resultService.saveGameResult(first.getId(), GameResult.BLACK_WIN));
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
}
