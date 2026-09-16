package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.TournamentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockoutPairingStrategyTest {

    private final KnockoutPairingStrategy strategy = new KnockoutPairingStrategy();

    @Test
    void supports_onlyKnockout() {
        assertTrue(strategy.supports(TournamentType.KNOCKOUT));
        assertFalse(strategy.supports(TournamentType.ROUND_ROBIN));
        assertFalse(strategy.supports(TournamentType.SWISS));
    }

    @Test
    void pair_round1_eightPlayers_fourGames() {
        List<PairingProposal> proposals = strategy.pair(context(players(8), 1, List.of()));
        assertEquals(4, proposals.size());
        assertTrue(proposals.stream().noneMatch(PairingProposal::bye));
    }

    @Test
    void pair_round1_sixPlayers_twoByes() {
        List<PairingProposal> proposals = strategy.pair(context(players(6), 1, List.of()));
        assertEquals(4, proposals.size());
        assertEquals(2, proposals.stream().filter(PairingProposal::bye).count());
    }

    @Test
    void pair_round2_pairsAdjacentWinners() {
        List<TournamentPlayer> all = players(4);
        List<Game> prev = List.of(
                game(1, 1L, 4L, GameResult.WHITE_WIN),
                game(2, 2L, 3L, GameResult.WHITE_WIN));
        List<PairingProposal> proposals = strategy.pair(context(all, 2, prev));
        assertEquals(1, proposals.size());
        PairingProposal finalMatch = proposals.get(0);
        assertFalse(finalMatch.bye());
        Set<Long> ids = Set.of(finalMatch.whiteTpId(), finalMatch.blackTpId());
        assertEquals(Set.of(1L, 2L), ids);
    }

    @Test
    void factory_resolvesKnockout() {
        PairingStrategyFactory factory = new PairingStrategyFactory();
        assertTrue(factory.forType(TournamentType.KNOCKOUT) instanceof KnockoutPairingStrategy);
    }

    private static PairingContext context(List<TournamentPlayer> players, int round,
                                          List<Game> previous) {
        Tournament tournament = new Tournament();
        tournament.setId(1L);
        tournament.setType(TournamentType.KNOCKOUT);
        tournament.setRoundsPlanned(KnockoutSeeding.expectedRounds(players.size()));
        return new PairingContext(tournament, round, players, Set.of(), List.of(), previous);
    }

    private static Game game(int board, long white, long black, GameResult result) {
        Game g = new Game();
        g.setBoardNumber(board);
        g.setWhiteTournamentPlayerId(white);
        g.setBlackTournamentPlayerId(black);
        g.setResult(result);
        return g;
    }

    private static List<TournamentPlayer> players(int n) {
        List<TournamentPlayer> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            TournamentPlayer tp = new TournamentPlayer();
            tp.setId((long) i);
            tp.setTournamentId(1L);
            tp.setPlayerId(100L + i);
            tp.setCurrentRating(1700 - 100 * i);
            tp.setStartRating(tp.getCurrentRating());
            list.add(tp);
        }
        return list;
    }
}
