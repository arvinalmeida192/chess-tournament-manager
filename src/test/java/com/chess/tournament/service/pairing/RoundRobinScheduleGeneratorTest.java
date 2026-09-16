package com.chess.tournament.service.pairing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundRobinScheduleGeneratorTest {

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8})
    void schedule_hasExpectedRoundCount(int n) {
        List<Long> ids = ids(n);
        List<List<RoundRobinScheduleGenerator.ScheduledPair>> schedule =
                RoundRobinScheduleGenerator.generate(ids);
        assertEquals(RoundRobinScheduleGenerator.expectedRoundCount(n), schedule.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6})
    void schedule_uniquePairsEqualNChoose2(int n) {
        List<List<RoundRobinScheduleGenerator.ScheduledPair>> schedule =
                RoundRobinScheduleGenerator.generate(ids(n));
        Set<RoundRobinScheduleGenerator.UnorderedPair> pairs =
                RoundRobinScheduleGenerator.uniqueRealPairs(schedule);
        assertEquals(n * (n - 1) / 2, pairs.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6})
    void eachRound_everyPlayerAtMostOnce(int n) {
        List<Long> playerIds = ids(n);
        List<List<RoundRobinScheduleGenerator.ScheduledPair>> schedule =
                RoundRobinScheduleGenerator.generate(playerIds);

        for (int r = 0; r < schedule.size(); r++) {
            Set<Long> seen = new HashSet<>();
            int byeCount = 0;
            for (RoundRobinScheduleGenerator.ScheduledPair pair : schedule.get(r)) {
                if (pair.isBye()) {
                    byeCount++;
                    Long byeId = pair.byePlayerId();
                    assertTrue(seen.add(byeId), "duplicate in round " + (r + 1));
                } else {
                    assertTrue(seen.add(pair.a()), "duplicate in round " + (r + 1));
                    assertTrue(seen.add(pair.b()), "duplicate in round " + (r + 1));
                }
            }
            assertEquals(n, seen.size(), "round " + (r + 1) + " must include all players");
            assertEquals(n % 2, byeCount, "bye count for round " + (r + 1));
        }
    }

    @Test
    void eachPlayer_meetsEveryOtherExactlyOnce_n4() {
        assertEachMeetsEachOnce(4);
    }

    @Test
    void eachPlayer_meetsEveryOtherExactlyOnce_n5() {
        assertEachMeetsEachOnce(5);
    }

    @Test
    void eachPlayer_meetsEveryOtherExactlyOnce_n6() {
        assertEachMeetsEachOnce(6);
    }

    @Test
    void round_outOfRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> RoundRobinScheduleGenerator.round(ids(4), 0));
        assertThrows(IllegalArgumentException.class,
                () -> RoundRobinScheduleGenerator.round(ids(4), 4));
    }

    @Test
    void tooFewPlayers_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> RoundRobinScheduleGenerator.generate(List.of(1L)));
    }

    @Test
    void n4_round1_pairsAsExpected() {
        // Circle: [1,2,3,4] → (1,4) and (2,3)
        List<RoundRobinScheduleGenerator.ScheduledPair> r1 =
                RoundRobinScheduleGenerator.round(ids(4), 1);
        assertEquals(2, r1.size());
        assertFalse(r1.get(0).isBye());
        assertEquals(1L, r1.get(0).a());
        assertEquals(4L, r1.get(0).b());
        assertEquals(2L, r1.get(1).a());
        assertEquals(3L, r1.get(1).b());
    }

    private static void assertEachMeetsEachOnce(int n) {
        List<Long> playerIds = ids(n);
        List<List<RoundRobinScheduleGenerator.ScheduledPair>> schedule =
                RoundRobinScheduleGenerator.generate(playerIds);

        Map<RoundRobinScheduleGenerator.UnorderedPair, Integer> counts = new HashMap<>();
        for (List<RoundRobinScheduleGenerator.ScheduledPair> round : schedule) {
            for (RoundRobinScheduleGenerator.ScheduledPair pair : round) {
                if (!pair.isBye()) {
                    RoundRobinScheduleGenerator.UnorderedPair key =
                            RoundRobinScheduleGenerator.UnorderedPair.of(pair.a(), pair.b());
                    counts.merge(key, 1, Integer::sum);
                }
            }
        }

        for (long i = 1; i <= n; i++) {
            for (long j = i + 1; j <= n; j++) {
                assertEquals(1, counts.getOrDefault(
                                RoundRobinScheduleGenerator.UnorderedPair.of(i, j), 0),
                        "pair " + i + "-" + j + " should appear exactly once");
            }
        }
    }

    private static List<Long> ids(int n) {
        List<Long> list = new ArrayList<>(n);
        for (long i = 1; i <= n; i++) {
            list.add(i);
        }
        return list;
    }
}
