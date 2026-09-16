package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.LongPair;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwissPairingStrategyTest {

    private final SwissPairingStrategy strategy = new SwissPairingStrategy();

    @Test
    void supports_onlySwiss() {
        assertTrue(strategy.supports(TournamentType.SWISS));
        assertFalse(strategy.supports(TournamentType.ROUND_ROBIN));
        assertFalse(strategy.supports(TournamentType.KNOCKOUT));
    }

    @Test
    void factory_resolvesSwiss() {
        PairingStrategyFactory factory = new PairingStrategyFactory();
        assertTrue(factory.forType(TournamentType.SWISS) instanceof SwissPairingStrategy);
    }

    @Test
    void round1_random_isDeterministicForTournamentId() {
        List<TournamentPlayer> players = players(8, 0);
        List<PairingProposal> a = strategy.pair(context(players, 1, SwissFirstRoundMethod.RANDOM, Set.of(), Set.of()));
        List<PairingProposal> b = strategy.pair(context(players, 1, SwissFirstRoundMethod.RANDOM, Set.of(), Set.of()));
        assertEquals(a, b);
        assertEquals(4, a.size());
        assertTrue(a.stream().noneMatch(PairingProposal::bye));
        assertAllPlayersOnce(a, players);
    }

    @Test
    void round1_ratingSplit_pairsTopHalfVsBottomHalf() {
        List<TournamentPlayer> players = players(8, 0);
        // ids 1..8 ratings 1700,1600,...,1000
        List<PairingProposal> proposals = strategy.pair(
                context(players, 1, SwissFirstRoundMethod.RATING_SPLIT, Set.of(), Set.of()));

        assertEquals(4, proposals.size());
        Set<LongPair> pairs = proposals.stream()
                .map(p -> LongPair.of(p.whiteTpId(), p.blackTpId()))
                .collect(Collectors.toSet());
        assertTrue(pairs.contains(LongPair.of(1L, 5L)));
        assertTrue(pairs.contains(LongPair.of(2L, 6L)));
        assertTrue(pairs.contains(LongPair.of(3L, 7L)));
        assertTrue(pairs.contains(LongPair.of(4L, 8L)));
    }

    @Test
    void round1_odd_assignsSingleByeToLowestAfterOrder() {
        List<TournamentPlayer> players = players(5, 0);
        List<PairingProposal> proposals = strategy.pair(
                context(players, 1, SwissFirstRoundMethod.RATING_SPLIT, Set.of(), Set.of()));

        assertEquals(3, proposals.size());
        List<PairingProposal> byes = proposals.stream().filter(PairingProposal::bye).toList();
        assertEquals(1, byes.size());
        assertEquals(5L, byes.get(0).whiteTpId()); // lowest rated after sort
        assertAllPlayersOnce(proposals, players);
    }

    @Test
    void round2_pairsWithinScoreGroups() {
        List<TournamentPlayer> players = players(8, 0);
        // Top 4 have 1 point, bottom 4 have 0
        for (int i = 0; i < 4; i++) {
            players.get(i).setPoints(BigDecimal.ONE);
        }

        List<PairingProposal> proposals = strategy.pair(
                context(players, 2, SwissFirstRoundMethod.RANDOM, Set.of(), Set.of()));

        assertEquals(4, proposals.size());
        assertAllPlayersOnce(proposals, players);

        for (PairingProposal p : proposals) {
            if (p.bye()) {
                continue;
            }
            TournamentPlayer w = byId(players, p.whiteTpId());
            TournamentPlayer b = byId(players, p.blackTpId());
            assertEquals(0, w.getPoints().compareTo(b.getPoints()),
                    "Same-score group preferred when even counts allow");
        }
    }

    @Test
    void round2_avoidsRematchWhenAlternativeExists() {
        List<TournamentPlayer> players = players(4, 0);
        for (TournamentPlayer tp : players) {
            tp.setPoints(BigDecimal.ONE);
        }
        Set<LongPair> previous = Set.of(LongPair.of(1L, 2L), LongPair.of(3L, 4L));

        List<PairingProposal> proposals = strategy.pair(
                context(players, 2, SwissFirstRoundMethod.RANDOM, previous, Set.of()));

        assertEquals(2, proposals.size());
        assertTrue(proposals.stream().noneMatch(PairingProposal::rematch));
        Set<LongPair> now = proposals.stream()
                .map(p -> LongPair.of(p.whiteTpId(), p.blackTpId()))
                .collect(Collectors.toSet());
        assertFalse(now.contains(LongPair.of(1L, 2L)));
        assertFalse(now.contains(LongPair.of(3L, 4L)));
    }

    @Test
    void round2_setsRematchWhenForced() {
        List<TournamentPlayer> players = players(2, 0);
        players.get(0).setPoints(BigDecimal.ONE);
        players.get(1).setPoints(BigDecimal.ONE);
        Set<LongPair> previous = Set.of(LongPair.of(1L, 2L));

        List<PairingProposal> proposals = strategy.pair(
                context(players, 2, SwissFirstRoundMethod.RANDOM, previous, Set.of()));

        assertEquals(1, proposals.size());
        assertTrue(proposals.get(0).rematch());
        assertEquals(LongPair.of(1L, 2L),
                LongPair.of(proposals.get(0).whiteTpId(), proposals.get(0).blackTpId()));
    }

    @Test
    void round2_odd_byePrefersPlayerWithoutPriorBye() {
        List<TournamentPlayer> players = players(5, 0);
        // All equal points; id 5 had a bye already; lowest-rated without bye should be id 4
        for (TournamentPlayer tp : players) {
            tp.setPoints(BigDecimal.ZERO);
        }
        Set<Long> priorByes = Set.of(5L);

        List<PairingProposal> proposals = strategy.pair(
                context(players, 2, SwissFirstRoundMethod.RANDOM, Set.of(), priorByes));

        List<PairingProposal> byes = proposals.stream().filter(PairingProposal::bye).toList();
        assertEquals(1, byes.size());
        assertEquals(4L, byes.get(0).whiteTpId());
    }

    @Test
    void colorAssigner_givesWhiteToLowerColorBalance() {
        TournamentPlayer a = player(1, 1500);
        TournamentPlayer b = player(2, 1600);
        a.setColorBalance(1);
        b.setColorBalance(-1);
        TournamentPlayer[] colors = SwissColorAssigner.assign(a, b);
        assertEquals(2L, colors[0].getId());
        assertEquals(1L, colors[1].getId());
    }

    @Test
    void colorAssigner_tieBreaksByHigherRating() {
        TournamentPlayer a = player(1, 1400);
        TournamentPlayer b = player(2, 1600);
        a.setColorBalance(0);
        b.setColorBalance(0);
        TournamentPlayer[] colors = SwissColorAssigner.assign(a, b);
        assertEquals(2L, colors[0].getId());
        assertEquals(1L, colors[1].getId());
    }

    @Test
    void scoreGroupBuilder_partitionsByPoints() {
        List<TournamentPlayer> players = players(4, 0);
        players.get(0).setPoints(new BigDecimal("2"));
        players.get(1).setPoints(new BigDecimal("2"));
        players.get(2).setPoints(BigDecimal.ONE);
        players.get(3).setPoints(BigDecimal.ZERO);

        List<List<TournamentPlayer>> groups = SwissScoreGroupBuilder.build(players);
        assertEquals(3, groups.size());
        assertEquals(2, groups.get(0).size());
        assertEquals(1, groups.get(1).size());
        assertEquals(1, groups.get(2).size());
    }

    @Test
    void threeRounds_eightPlayers_noRematchWhenAlternativesExist() {
        List<TournamentPlayer> players = players(8, 0);
        Set<LongPair> previous = new HashSet<>();

        for (int round = 1; round <= 3; round++) {
            List<PairingProposal> proposals = strategy.pair(
                    context(players, round,
                            round == 1 ? SwissFirstRoundMethod.RATING_SPLIT : SwissFirstRoundMethod.RANDOM,
                            previous, Set.of()));
            assertAllPlayersOnce(proposals, players);
            for (PairingProposal p : proposals) {
                if (p.bye()) {
                    continue;
                }
                LongPair pair = LongPair.of(p.whiteTpId(), p.blackTpId());
                assertFalse(previous.contains(pair) && !p.rematch(),
                        "Rematch without rematch flag");
                assertFalse(p.rematch(), "Unexpected forced rematch in open 8-player field round " + round);
                previous.add(pair);
            }
            // Simulate all draws so scores stay clustered but still pairable
            for (TournamentPlayer tp : players) {
                tp.setPoints(tp.getPoints().add(BigDecimal.valueOf(0.5)));
            }
        }
    }

    @Test
    void performance_100Players_round2UnderFiveSeconds() {
        List<TournamentPlayer> players = players(100, 0);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setPoints(BigDecimal.valueOf(i % 5));
        }
        Set<LongPair> previous = new HashSet<>();
        // Seed some previous pairings
        for (int i = 1; i <= 50; i++) {
            previous.add(LongPair.of(i, i + 50L));
        }

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            List<PairingProposal> proposals = strategy.pair(
                    context(players, 2, SwissFirstRoundMethod.RANDOM, previous, Set.of()));
            assertEquals(50, proposals.size());
            assertAllPlayersOnce(proposals, players);
        });
    }

    private static void assertAllPlayersOnce(List<PairingProposal> proposals,
                                             List<TournamentPlayer> players) {
        Set<Long> seen = new HashSet<>();
        for (PairingProposal p : proposals) {
            assertTrue(seen.add(p.whiteTpId()));
            if (!p.bye()) {
                assertTrue(seen.add(p.blackTpId()));
            }
        }
        assertEquals(players.size(), seen.size());
    }

    private static TournamentPlayer byId(List<TournamentPlayer> players, long id) {
        return players.stream().filter(p -> p.getId() == id).findFirst().orElseThrow();
    }

    private static PairingContext context(List<TournamentPlayer> players, int round,
                                          SwissFirstRoundMethod method,
                                          Set<LongPair> previous,
                                          Set<Long> byeHistory) {
        Tournament tournament = new Tournament();
        tournament.setId(42L);
        tournament.setType(TournamentType.SWISS);
        tournament.setRoundsPlanned(5);
        tournament.setSwissFirstRoundMethod(method);
        return new PairingContext(tournament, round, players, previous, List.of(), List.of(), byeHistory);
    }

    private static List<TournamentPlayer> players(int n, int points) {
        List<TournamentPlayer> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            TournamentPlayer tp = player(i, 1700 - 100 * (i - 1));
            tp.setPoints(BigDecimal.valueOf(points));
            list.add(tp);
        }
        return list;
    }

    private static TournamentPlayer player(long id, int rating) {
        TournamentPlayer tp = new TournamentPlayer();
        tp.setId(id);
        tp.setTournamentId(1L);
        tp.setPlayerId(1000L + id);
        tp.setCurrentRating(rating);
        tp.setStartRating(rating);
        tp.setPoints(BigDecimal.ZERO);
        tp.setColorBalance(0);
        return tp;
    }
}
