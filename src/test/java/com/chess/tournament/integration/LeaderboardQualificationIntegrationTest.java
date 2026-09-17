package com.chess.tournament.integration;

import com.chess.tournament.dao.JdbcUnitOfWork;
import com.chess.tournament.dao.impl.JdbcGameDao;
import com.chess.tournament.dao.impl.JdbcPlayerDao;
import com.chess.tournament.dao.impl.JdbcRoundDao;
import com.chess.tournament.dao.impl.JdbcTournamentDao;
import com.chess.tournament.dao.impl.JdbcTournamentPlayerDao;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.KnockoutAdvancementService;
import com.chess.tournament.service.LeaderboardService;
import com.chess.tournament.service.PairingService;
import com.chess.tournament.service.QualificationService;
import com.chess.tournament.service.RatingService;
import com.chess.tournament.service.ResultService;
import com.chess.tournament.service.TournamentService;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import com.chess.tournament.service.leaderboard.StandingRow;
import com.chess.tournament.service.pairing.PairingStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardQualificationIntegrationTest extends AbstractPostgresIntegrationTest {

    private TournamentService tournamentService;
    private EnrollmentService enrollmentService;
    private PairingService pairingService;
    private ResultService resultService;
    private LeaderboardService leaderboardService;
    private QualificationService qualificationService;
    private JdbcTournamentPlayerDao tournamentPlayerDao;
    private JdbcPlayerDao playerDao;

    @BeforeEach
    void setUpServices() {
        JdbcTournamentDao tournamentDao = new JdbcTournamentDao(getDataSource());
        tournamentPlayerDao = new JdbcTournamentPlayerDao(getDataSource());
        JdbcRoundDao roundDao = new JdbcRoundDao(getDataSource());
        playerDao = new JdbcPlayerDao(getDataSource());
        JdbcGameDao gameDao = new JdbcGameDao(getDataSource());
        JdbcUnitOfWork unitOfWork = new JdbcUnitOfWork(getDataSource());

        leaderboardService = new LeaderboardService(
                tournamentDao, tournamentPlayerDao, roundDao, gameDao, playerDao);
        qualificationService = new QualificationService(
                tournamentDao, tournamentPlayerDao, leaderboardService, unitOfWork, roundDao);
        tournamentService = new TournamentService(
                tournamentDao, tournamentPlayerDao, roundDao, unitOfWork, qualificationService);
        enrollmentService = new EnrollmentService(
                tournamentDao, tournamentPlayerDao, playerDao, roundDao, gameDao);
        pairingService = new PairingService(
                tournamentDao, tournamentPlayerDao, roundDao, gameDao, unitOfWork,
                new PairingStrategyFactory());
        KnockoutAdvancementService knockoutAdvancementService =
                new KnockoutAdvancementService(tournamentPlayerDao, unitOfWork);
        resultService = new ResultService(
                tournamentDao, tournamentPlayerDao, roundDao, gameDao, playerDao,
                unitOfWork, new RatingService(), knockoutAdvancementService);
    }

    @Test
    void swissThreeRounds_leaderboardMatchesTpPoints_qualificationTop4() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Swiss Qualifiers", TournamentType.SWISS, 3, 4, SwissFirstRoundMethod.RATING_SPLIT));

        int[] ratings = {2000, 1900, 1800, 1700, 1600, 1500, 1400, 1300};
        for (int i = 0; i < 8; i++) {
            enrollmentService.enroll(tournament.getId(), insertPlayer("P" + (i + 1), ratings[i]));
        }
        tournamentService.start(tournament.getId());

        for (int round = 1; round <= 3; round++) {
            List<Game> games = pairingService.generateAndPublish(tournament.getId(), round);
            for (Game game : games) {
                if (game.getResult() == GameResult.BYE) {
                    continue;
                }
                // Prefer higher start-rating as white win when possible — deterministic enough
                resultService.saveGameResult(game.getId(), GameResult.WHITE_WIN);
            }
            resultService.completeRound(tournament.getId(), round);
        }

        List<StandingRow> board = leaderboardService.getLeaderboard(tournament.getId(), null);
        assertEquals(8, board.size());

        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournament.getId());
        for (StandingRow row : board) {
            TournamentPlayer tp = tps.stream()
                    .filter(p -> p.getId().equals(row.getTournamentPlayerId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(0, tp.getPoints().compareTo(row.getPoints()),
                    "TP.points must match leaderboard for " + row.getPlayerName());
            assertEquals(tp.getWins(), row.getWins());
            assertEquals(tp.getDraws(), row.getDraws());
            assertEquals(tp.getLosses(), row.getLosses());
        }

        // Points should be non-increasing down the board
        for (int i = 1; i < board.size(); i++) {
            assertTrue(board.get(i - 1).getPoints().compareTo(board.get(i).getPoints()) >= 0);
        }

        qualificationService.applyQualification(tournament.getId());
        List<TournamentPlayer> after = tournamentPlayerDao.findByTournament(tournament.getId());
        long qualified = after.stream()
                .filter(tp -> tp.getQualificationStatus() == QualificationStatus.QUALIFIED)
                .count();
        long eliminated = after.stream()
                .filter(tp -> tp.getQualificationStatus() == QualificationStatus.ELIMINATED)
                .count();
        assertEquals(4, qualified);
        assertEquals(4, eliminated);

        // Top 4 ranks are the qualified set
        List<StandingRow> afterBoard = leaderboardService.getLeaderboard(tournament.getId(), null);
        for (int i = 0; i < 4; i++) {
            assertEquals(QualificationStatus.QUALIFIED, afterBoard.get(i).getQualificationStatus());
        }
        for (int i = 4; i < 8; i++) {
            assertEquals(QualificationStatus.ELIMINATED, afterBoard.get(i).getQualificationStatus());
        }

        tournamentService.finalizeTournament(tournament.getId());
        Tournament done = tournamentService.findById(tournament.getId()).orElseThrow();
        assertEquals(TournamentStatus.COMPLETED, done.getStatus());
        assertTrue(done.getCompletedAt() != null);
    }

    @Test
    void finalize_blockedWhenRoundsIncomplete() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "Incomplete", TournamentType.SWISS, 3, 2, SwissFirstRoundMethod.RANDOM));
        enrollmentService.enroll(tournament.getId(), insertPlayer("A", 1500));
        enrollmentService.enroll(tournament.getId(), insertPlayer("B", 1500));
        tournamentService.start(tournament.getId());

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);
        for (Game g : games) {
            if (g.getResult() != GameResult.BYE) {
                resultService.saveGameResult(g.getId(), GameResult.DRAW);
            }
        }
        resultService.completeRound(tournament.getId(), 1);

        assertThrows(ValidationException.class,
                () -> tournamentService.finalizeTournament(tournament.getId()));
        assertThrows(ValidationException.class,
                () -> qualificationService.applyQualification(tournament.getId()));
    }

    @Test
    void qualification_qZero_marksAllNotApplicable() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "No Qualifiers", TournamentType.SWISS, 1, 0, SwissFirstRoundMethod.RANDOM));
        enrollmentService.enroll(tournament.getId(), insertPlayer("A", 1600));
        enrollmentService.enroll(tournament.getId(), insertPlayer("B", 1400));
        tournamentService.start(tournament.getId());

        List<Game> games = pairingService.generateAndPublish(tournament.getId(), 1);
        for (Game g : games) {
            if (g.getResult() != GameResult.BYE) {
                resultService.saveGameResult(g.getId(), GameResult.WHITE_WIN);
            }
        }
        resultService.completeRound(tournament.getId(), 1);

        qualificationService.applyQualification(tournament.getId());
        List<TournamentPlayer> tps = tournamentPlayerDao.findByTournament(tournament.getId());
        assertTrue(tps.stream().allMatch(tp ->
                tp.getQualificationStatus() == QualificationStatus.NOT_APPLICABLE));
    }

    @Test
    void historicalLeaderboard_recomputesPointsUpToRound() {
        Tournament tournament = tournamentService.create(new CreateTournamentCommand(
                "History", TournamentType.SWISS, 2, 1, SwissFirstRoundMethod.RANDOM));
        enrollmentService.enroll(tournament.getId(), insertPlayer("A", 1500));
        enrollmentService.enroll(tournament.getId(), insertPlayer("B", 1500));
        tournamentService.start(tournament.getId());

        for (int round = 1; round <= 2; round++) {
            List<Game> games = pairingService.generateAndPublish(tournament.getId(), round);
            for (Game g : games) {
                if (g.getResult() != GameResult.BYE) {
                    resultService.saveGameResult(g.getId(), GameResult.WHITE_WIN);
                }
            }
            resultService.completeRound(tournament.getId(), round);
        }

        List<StandingRow> afterR1 = leaderboardService.getLeaderboard(tournament.getId(), 1);
        BigDecimal totalAfterR1 = afterR1.stream()
                .map(StandingRow::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // One decisive game → 1 + 0 = 1 point total after round 1
        assertEquals(0, BigDecimal.ONE.compareTo(totalAfterR1));

        List<StandingRow> afterR2 = leaderboardService.getLeaderboard(tournament.getId(), 2);
        BigDecimal totalAfterR2 = afterR2.stream()
                .map(StandingRow::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("2").compareTo(totalAfterR2));
    }

    private long insertPlayer(String name, int rating) {
        var player = new com.chess.tournament.domain.Player();
        player.setName(name);
        player.setGlobalRating(rating);
        player.setActive(true);
        return playerDao.insert(player);
    }
}
