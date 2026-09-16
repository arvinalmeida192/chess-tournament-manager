package com.chess.tournament.integration;

import com.chess.tournament.dao.impl.JdbcGameDao;
import com.chess.tournament.dao.impl.JdbcPlayerDao;
import com.chess.tournament.dao.impl.JdbcRoundDao;
import com.chess.tournament.dao.impl.JdbcTournamentDao;
import com.chess.tournament.dao.impl.JdbcTournamentPlayerDao;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.LongPair;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.TournamentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDaoIntegrationTest extends AbstractPostgresIntegrationTest {

    private final JdbcPlayerDao playerDao = new JdbcPlayerDao(getDataSource());
    private final JdbcTournamentDao tournamentDao = new JdbcTournamentDao(getDataSource());
    private final JdbcTournamentPlayerDao tournamentPlayerDao = new JdbcTournamentPlayerDao(getDataSource());
    private final JdbcRoundDao roundDao = new JdbcRoundDao(getDataSource());
    private final JdbcGameDao gameDao = new JdbcGameDao(getDataSource());

    @Test
    void findPreviousPairings_returnsUnorderedPairsFromAllRounds() {
        long tournamentId = createTournament();
        long tp1 = enrollPlayer(tournamentId, "P1", 1500);
        long tp2 = enrollPlayer(tournamentId, "P2", 1600);
        long tp3 = enrollPlayer(tournamentId, "P3", 1700);
        long tp4 = enrollPlayer(tournamentId, "P4", 1800);

        long round1Id = createRound(tournamentId, 1);
        insertGame(round1Id, 1, tp1, tp2);
        insertGame(round1Id, 2, tp3, tp4);

        long round2Id = createRound(tournamentId, 2);
        insertGame(round2Id, 1, tp1, tp3);

        Set<LongPair> pairings = gameDao.findPreviousPairings(tournamentId);

        assertEquals(3, pairings.size());
        assertTrue(pairings.contains(LongPair.of(tp1, tp2)));
        assertTrue(pairings.contains(LongPair.of(tp3, tp4)));
        assertTrue(pairings.contains(LongPair.of(tp1, tp3)));
        assertTrue(pairings.contains(LongPair.of(tp2, tp1)));
    }

    @Test
    void insertBatch_findByRound_andUpdateResult() {
        long tournamentId = createTournament();
        long tp1 = enrollPlayer(tournamentId, "White", 1500);
        long tp2 = enrollPlayer(tournamentId, "Black", 1600);
        long roundId = createRound(tournamentId, 1);

        Game game = new Game();
        game.setRoundId(roundId);
        game.setBoardNumber(1);
        game.setWhiteTournamentPlayerId(tp1);
        game.setBlackTournamentPlayerId(tp2);
        game.setResult(GameResult.PENDING);
        gameDao.insertBatch(List.of(game));

        List<Game> loaded = gameDao.findByRound(roundId);
        assertEquals(1, loaded.size());
        assertEquals(GameResult.PENDING, loaded.get(0).getResult());

        Game toUpdate = loaded.get(0);
        toUpdate.setResult(GameResult.WHITE_WIN);
        toUpdate.setWhiteScore(BigDecimal.ONE);
        toUpdate.setBlackScore(BigDecimal.ZERO);
        gameDao.updateResult(toUpdate);

        Game updated = gameDao.findByRound(roundId).get(0);
        assertEquals(GameResult.WHITE_WIN, updated.getResult());
        assertEquals(0, BigDecimal.ONE.compareTo(updated.getWhiteScore()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getBlackScore()));
    }

    private long createTournament() {
        Tournament tournament = new Tournament();
        tournament.setName("Pairing Test");
        tournament.setType(TournamentType.SWISS);
        tournament.setRoundsPlanned(3);
        tournament.setQualifiersCount(0);
        return tournamentDao.insert(tournament);
    }

    private long enrollPlayer(long tournamentId, String name, int rating) {
        Player player = new Player();
        player.setName(name);
        player.setGlobalRating(rating);
        long playerId = playerDao.insert(player);

        TournamentPlayer tp = new TournamentPlayer();
        tp.setTournamentId(tournamentId);
        tp.setPlayerId(playerId);
        tp.setStartRating(rating);
        tp.setCurrentRating(rating);
        return tournamentPlayerDao.insert(tp);
    }

    private long createRound(long tournamentId, int roundNumber) {
        Round round = new Round();
        round.setTournamentId(tournamentId);
        round.setRoundNumber(roundNumber);
        return roundDao.insert(round);
    }

    private void insertGame(long roundId, int board, long whiteTpId, long blackTpId) {
        Game game = new Game();
        game.setRoundId(roundId);
        game.setBoardNumber(board);
        game.setWhiteTournamentPlayerId(whiteTpId);
        game.setBlackTournamentPlayerId(blackTpId);
        game.setResult(GameResult.PENDING);
        gameDao.insertBatch(List.of(game));
    }
}
