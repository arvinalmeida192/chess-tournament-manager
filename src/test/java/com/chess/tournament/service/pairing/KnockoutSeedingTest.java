package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.TournamentPlayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockoutSeedingTest {

    @Test
    void nextPowerOfTwo() {
        assertEquals(8, KnockoutSeeding.nextPowerOfTwo(5));
        assertEquals(8, KnockoutSeeding.nextPowerOfTwo(8));
        assertEquals(16, KnockoutSeeding.nextPowerOfTwo(9));
        assertEquals(4, KnockoutSeeding.nextPowerOfTwo(3));
    }

    @Test
    void byeCount_forSixIsTwo() {
        assertEquals(2, KnockoutSeeding.byeCount(6));
        assertEquals(0, KnockoutSeeding.byeCount(8));
        assertEquals(3, KnockoutSeeding.byeCount(5));
    }

    @Test
    void expectedRounds_matchesCeilLog2() {
        assertEquals(3, KnockoutSeeding.expectedRounds(8));
        assertEquals(3, KnockoutSeeding.expectedRounds(6));
        assertEquals(2, KnockoutSeeding.expectedRounds(4));
        assertEquals(1, KnockoutSeeding.expectedRounds(2));
    }

    @Test
    void firstRoundSeedPairs_eightPlayers() {
        List<int[]> pairs = KnockoutSeeding.firstRoundSeedPairs(8);
        assertEquals(4, pairs.size());
        assertEquals(1, pairs.get(0)[0]);
        assertEquals(8, pairs.get(0)[1]);
        assertEquals(4, pairs.get(1)[0]);
        assertEquals(5, pairs.get(1)[1]);
        assertEquals(2, pairs.get(2)[0]);
        assertEquals(7, pairs.get(2)[1]);
        assertEquals(3, pairs.get(3)[0]);
        assertEquals(6, pairs.get(3)[1]);
    }

    @Test
    void buildRound1_eightPlayers_fourRealGamesNoBye() {
        List<KnockoutSeeding.Slot> slots = KnockoutSeeding.buildRound1Slots(players(8));
        assertEquals(4, slots.size());
        assertTrue(slots.stream().noneMatch(KnockoutSeeding.Slot::bye));
        assertAllPlayersOnce(slots, 8);
    }

    @Test
    void buildRound1_sixPlayers_twoByesToLowestRated() {
        List<TournamentPlayer> players = players(6);
        List<KnockoutSeeding.Slot> slots = KnockoutSeeding.buildRound1Slots(players);
        assertEquals(4, slots.size());
        assertEquals(2, slots.stream().filter(KnockoutSeeding.Slot::bye).count());
        assertEquals(2, slots.stream().filter(s -> !s.bye()).count());

        Set<Long> byeIds = new HashSet<>();
        for (KnockoutSeeding.Slot slot : slots) {
            if (slot.bye()) {
                byeIds.add(slot.white().getId());
            }
        }
        assertTrue(byeIds.contains(5L));
        assertTrue(byeIds.contains(6L));
        assertAllPlayersOnce(slots, 6);
    }

    @Test
    void buildRound1_fivePlayers_threeByes() {
        List<KnockoutSeeding.Slot> slots = KnockoutSeeding.buildRound1Slots(players(5));
        assertEquals(4, slots.size());
        assertEquals(3, slots.stream().filter(KnockoutSeeding.Slot::bye).count());
        assertEquals(1, slots.stream().filter(s -> !s.bye()).count());
        assertAllPlayersOnce(slots, 5);
    }

    private static void assertAllPlayersOnce(List<KnockoutSeeding.Slot> slots, int n) {
        Set<Long> seen = new HashSet<>();
        for (KnockoutSeeding.Slot slot : slots) {
            assertTrue(seen.add(slot.white().getId()));
            if (!slot.bye()) {
                assertTrue(seen.add(slot.black().getId()));
            }
        }
        assertEquals(n, seen.size());
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
