package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.exception.ValidationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Knockout bracket seeding helpers (SDD §8.3, BR-KO-001).
 * <p>
 * <b>Seeding order:</b> players sorted by {@code current_rating} descending,
 * then {@code id} ascending for stability. Seed 1 is the highest rated.
 * <p>
 * <b>Bracket pairs:</b> standard power-of-two expansion starting from {@code [1,2]},
 * doubling each step by placing {@code seed} opposite {@code size*2+1-seed}.
 * For 8 players the first-round seed pairs are
 * {@code 1v8, 4v5, 2v7, 3v6} (board order).
 * <p>
 * <b>Byes (BR-KO-001):</b> when {@code N} is not a power of two, phantom seeds
 * {@code N+1..B} create bye slots. Those slots are filled with the
 * <em>lowest-rated</em> players; remaining players fill remaining seeds by rating
 * (best → lowest remaining seed number).
 */
public final class KnockoutSeeding {

    private KnockoutSeeding() {
    }

    public static int nextPowerOfTwo(int n) {
        if (n < 1) {
            throw new ValidationException("Player count must be >= 1");
        }
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    public static int byeCount(int playerCount) {
        return nextPowerOfTwo(playerCount) - playerCount;
    }

    public static int expectedRounds(int playerCount) {
        if (playerCount < 2) {
            throw new ValidationException("Knockout requires at least 2 players");
        }
        return Integer.numberOfTrailingZeros(nextPowerOfTwo(playerCount));
    }

    /**
     * Seed numbers paired for first round boards: each int[2] is (seedA, seedB).
     */
    public static List<int[]> firstRoundSeedPairs(int bracketSize) {
        if (bracketSize < 2 || (bracketSize & (bracketSize - 1)) != 0) {
            throw new ValidationException("Bracket size must be a power of two >= 2");
        }
        int[] order = bracketSeedOrder(bracketSize);
        List<int[]> pairs = new ArrayList<>(bracketSize / 2);
        for (int i = 0; i < order.length; i += 2) {
            pairs.add(new int[]{order[i], order[i + 1]});
        }
        return pairs;
    }

    /**
     * Builds first-round slot assignments: each entry is a real match (two players)
     * or a bye (one player, other null). List order = board order.
     */
    public static List<Slot> buildRound1Slots(List<TournamentPlayer> players) {
        Objects.requireNonNull(players, "players");
        int n = players.size();
        if (n < 2) {
            throw new ValidationException("Knockout requires at least 2 players");
        }

        List<TournamentPlayer> ranked = sortByRatingDesc(players);
        int bracketSize = nextPowerOfTwo(n);
        List<int[]> seedPairs = firstRoundSeedPairs(bracketSize);

        Map<Integer, TournamentPlayer> seedToPlayer = assignSeedsWithLowestRatedByes(ranked, n, bracketSize);

        List<Slot> slots = new ArrayList<>(seedPairs.size());
        for (int[] pair : seedPairs) {
            TournamentPlayer a = seedToPlayer.get(pair[0]);
            TournamentPlayer b = seedToPlayer.get(pair[1]);
            if (a == null && b == null) {
                throw new IllegalStateException("Empty bracket slot for seeds " + pair[0] + "," + pair[1]);
            }
            if (a == null) {
                slots.add(Slot.bye(b));
            } else if (b == null) {
                slots.add(Slot.bye(a));
            } else {
                // Higher rating (lower seed number in traditional sense) gets white:
                // prefer the better-rated of the two as white.
                if (compareRatingDesc(a, b) <= 0) {
                    slots.add(Slot.match(a, b));
                } else {
                    slots.add(Slot.match(b, a));
                }
            }
        }
        return slots;
    }

    /**
     * Assigns players to seed numbers 1..bracketSize (null = phantom).
     * Bye-facing seeds receive lowest-rated players; other seeds receive
     * remaining players best-first into ascending seed numbers.
     */
    static Map<Integer, TournamentPlayer> assignSeedsWithLowestRatedByes(
            List<TournamentPlayer> rankedBestFirst, int n, int bracketSize) {
        List<int[]> seedPairs = firstRoundSeedPairs(bracketSize);
        List<Integer> byeSeeds = new ArrayList<>();
        List<Integer> playSeeds = new ArrayList<>();
        for (int[] pair : seedPairs) {
            boolean aPhantom = pair[0] > n;
            boolean bPhantom = pair[1] > n;
            if (aPhantom && bPhantom) {
                throw new IllegalStateException("Both seeds phantom");
            }
            if (aPhantom) {
                byeSeeds.add(pair[1]);
            } else if (bPhantom) {
                byeSeeds.add(pair[0]);
            } else {
                playSeeds.add(pair[0]);
                playSeeds.add(pair[1]);
            }
        }
        byeSeeds.sort(Integer::compareTo);
        playSeeds.sort(Integer::compareTo);

        // rankedBestFirst: index 0 = best. Lowest rated are at the end.
        int byeCount = bracketSize - n;
        List<TournamentPlayer> byePlayers = new ArrayList<>(
                rankedBestFirst.subList(n - byeCount, n));
        // Assign lowest-rated to bye seeds; among bye seeds, lower seed# gets
        // the higher of the low-rated group for slightly fairer bracket placement.
        byePlayers.sort(KnockoutSeeding::compareRatingDesc);

        List<TournamentPlayer> playPlayers = new ArrayList<>(
                rankedBestFirst.subList(0, n - byeCount));
        // best → lowest play seed number
        playPlayers.sort(KnockoutSeeding::compareRatingDesc);

        Map<Integer, TournamentPlayer> map = new HashMap<>();
        for (int i = 0; i < byeSeeds.size(); i++) {
            map.put(byeSeeds.get(i), byePlayers.get(i));
        }
        for (int i = 0; i < playSeeds.size(); i++) {
            map.put(playSeeds.get(i), playPlayers.get(i));
        }
        return map;
    }

    public static List<TournamentPlayer> sortByRatingDesc(List<TournamentPlayer> players) {
        List<TournamentPlayer> copy = new ArrayList<>(players);
        copy.sort(KnockoutSeeding::compareRatingDesc);
        return copy;
    }

    static int compareRatingDesc(TournamentPlayer a, TournamentPlayer b) {
        int cmp = Integer.compare(b.getCurrentRating(), a.getCurrentRating());
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare(a.getId(), b.getId());
    }

    /**
     * Standard bracket seed order (adjacent pairs play).
     */
    static int[] bracketSeedOrder(int bracketSize) {
        int[] positions = {1, 2};
        for (int size = 2; size < bracketSize; size *= 2) {
            int[] next = new int[size * 2];
            for (int i = 0; i < size; i++) {
                next[i * 2] = positions[i];
                next[i * 2 + 1] = size * 2 + 1 - positions[i];
            }
            positions = next;
        }
        return positions;
    }

    /**
     * One first-round bracket slot.
     */
    public record Slot(TournamentPlayer white, TournamentPlayer black, boolean bye) {
        public static Slot match(TournamentPlayer white, TournamentPlayer black) {
            return new Slot(white, black, false);
        }

        public static Slot bye(TournamentPlayer player) {
            return new Slot(player, null, true);
        }
    }
}
