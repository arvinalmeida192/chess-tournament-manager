package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.TournamentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundRobinPairingStrategyTest {

    private final RoundRobinPairingStrategy strategy = new RoundRobinPairingStrategy();

    @Test
    void supports_onlyRoundRobin() {
        assertTrue(strategy.supports(TournamentType.ROUND_ROBIN));
        assertFalse(strategy.supports(TournamentType.SWISS));
        assertFalse(strategy.supports(TournamentType.KNOCKOUT));
    }

    @Test
    void pair_n4_round1_twoGamesNoBye() {
        List<PairingProposal> proposals = strategy.pair(context(4, 1));
        assertEquals(2, proposals.size());
        assertTrue(proposals.stream().noneMatch(PairingProposal::bye));
        assertAllPlayersOnce(proposals, 4);
    }

    @Test
    void pair_n5_round1_includesOneBye() {
        List<PairingProposal> proposals = strategy.pair(context(5, 1));
        assertEquals(3, proposals.size()); // 2 games + 1 bye
        assertEquals(1, proposals.stream().filter(PairingProposal::bye).count());
        assertAllPlayersOnce(proposals, 5);
    }

    @Test
    void factory_resolvesRoundRobin() {
        PairingStrategyFactory factory = new PairingStrategyFactory();
        assertTrue(factory.forType(TournamentType.ROUND_ROBIN) instanceof RoundRobinPairingStrategy);
    }

    private static void assertAllPlayersOnce(List<PairingProposal> proposals, int n) {
        Set<Long> seen = new HashSet<>();
        for (PairingProposal p : proposals) {
            assertTrue(seen.add(p.whiteTpId()));
            if (!p.bye()) {
                assertTrue(seen.add(p.blackTpId()));
            }
        }
        assertEquals(n, seen.size());
    }

    private static PairingContext context(int n, int round) {
        Tournament tournament = new Tournament();
        tournament.setId(1L);
        tournament.setType(TournamentType.ROUND_ROBIN);
        tournament.setRoundsPlanned(RoundRobinScheduleGenerator.expectedRoundCount(n));

        List<TournamentPlayer> players = new ArrayList<>();
        for (long i = 1; i <= n; i++) {
            TournamentPlayer tp = new TournamentPlayer();
            tp.setId(i);
            tp.setTournamentId(1L);
            tp.setPlayerId(100 + i);
            players.add(tp);
        }
        return new PairingContext(tournament, round, players, Set.of(), List.of());
    }
}
