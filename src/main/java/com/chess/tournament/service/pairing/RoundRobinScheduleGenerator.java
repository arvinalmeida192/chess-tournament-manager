package com.chess.tournament.service.pairing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Circle-method Round Robin schedule (SDD §8.2).
 * <p>
 * Players are ordered by enrollment id. Odd N appends a phantom bye slot so the
 * working size is even. Round count is {@code N-1} (even N) or {@code N} (odd N).
 * Each unordered pair appears exactly once across the full schedule.
 */
public final class RoundRobinScheduleGenerator {

    private RoundRobinScheduleGenerator() {
    }

    /**
     * @param playerIds ordered tournament-player ids (enrollment order)
     * @return list of rounds; each round is a list of unordered pairs where
     *         {@code null} opponent means bye for the non-null id
     */
    public static List<List<ScheduledPair>> generate(List<Long> playerIds) {
        Objects.requireNonNull(playerIds, "playerIds");
        int n = playerIds.size();
        if (n < 2) {
            throw new IllegalArgumentException("Round Robin schedule requires at least 2 players");
        }

        List<Long> circle = new ArrayList<>(playerIds);
        boolean odd = n % 2 != 0;
        if (odd) {
            circle.add(null); // phantom bye
        }
        int size = circle.size(); // always even
        int roundCount = size - 1;

        List<List<ScheduledPair>> schedule = new ArrayList<>(roundCount);
        List<Long> arrangement = new ArrayList<>(circle);

        for (int round = 1; round <= roundCount; round++) {
            List<ScheduledPair> pairs = new ArrayList<>(size / 2);
            for (int i = 0; i < size / 2; i++) {
                Long a = arrangement.get(i);
                Long b = arrangement.get(size - 1 - i);
                pairs.add(new ScheduledPair(a, b));
            }
            schedule.add(List.copyOf(pairs));
            rotateClockwiseFixedFirst(arrangement);
        }
        return List.copyOf(schedule);
    }

    /**
     * Schedule for a specific 1-based round number.
     */
    public static List<ScheduledPair> round(List<Long> playerIds, int roundNumber) {
        List<List<ScheduledPair>> full = generate(playerIds);
        if (roundNumber < 1 || roundNumber > full.size()) {
            throw new IllegalArgumentException(
                    "Round " + roundNumber + " out of range 1.." + full.size());
        }
        return full.get(roundNumber - 1);
    }

    public static int expectedRoundCount(int playerCount) {
        if (playerCount < 1) {
            throw new IllegalArgumentException("playerCount must be >= 1");
        }
        return (playerCount % 2 == 0) ? playerCount - 1 : playerCount;
    }

    /**
     * Unique real (non-bye) unordered pairs across the full schedule.
     */
    public static Set<UnorderedPair> uniqueRealPairs(List<List<ScheduledPair>> schedule) {
        Set<UnorderedPair> pairs = new HashSet<>();
        for (List<ScheduledPair> round : schedule) {
            for (ScheduledPair pair : round) {
                if (!pair.isBye()) {
                    pairs.add(UnorderedPair.of(pair.a(), pair.b()));
                }
            }
        }
        return pairs;
    }

    /** Fix index 0; rotate indices 1..n-1 clockwise by one. */
    private static void rotateClockwiseFixedFirst(List<Long> arrangement) {
        int n = arrangement.size();
        if (n < 2) {
            return;
        }
        Long last = arrangement.get(n - 1);
        for (int i = n - 1; i >= 2; i--) {
            arrangement.set(i, arrangement.get(i - 1));
        }
        arrangement.set(1, last);
    }

    /**
     * One scheduled pairing; either side may be null (bye).
     */
    public record ScheduledPair(Long a, Long b) {
        public boolean isBye() {
            return a == null || b == null;
        }

        public Long byePlayerId() {
            if (!isBye()) {
                return null;
            }
            return a != null ? a : b;
        }
    }

    public record UnorderedPair(long first, long second) {
        public UnorderedPair {
            if (first > second) {
                long tmp = first;
                first = second;
                second = tmp;
            }
        }

        public static UnorderedPair of(long x, long y) {
            return new UnorderedPair(x, y);
        }
    }
}
