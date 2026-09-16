package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.LongPair;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Swiss / simplified Dutch pairings (SDD §8.4–8.5, BR-SWI-*).
 */
public final class SwissPairingStrategy implements PairingStrategy {

    @Override
    public boolean supports(TournamentType type) {
        return type == TournamentType.SWISS;
    }

    @Override
    public List<PairingProposal> pair(PairingContext ctx) {
        if (!supports(ctx.tournament().getType())) {
            throw new ValidationException("SwissPairingStrategy only supports SWISS");
        }
        if (ctx.players().size() < 2) {
            throw new ValidationException("Swiss requires at least 2 players");
        }
        if (ctx.roundNumber() == 1) {
            return pairRound1(ctx);
        }
        return pairSubsequent(ctx);
    }

    private static List<PairingProposal> pairRound1(PairingContext ctx) {
        List<TournamentPlayer> players = new ArrayList<>(ctx.players());
        SwissFirstRoundMethod method = ctx.tournament().getSwissFirstRoundMethod();
        if (method == null) {
            method = SwissFirstRoundMethod.RANDOM;
        }

        if (method == SwissFirstRoundMethod.RATING_SPLIT) {
            players.sort(Comparator
                    .comparingInt(TournamentPlayer::getCurrentRating).reversed()
                    .thenComparing(TournamentPlayer::getId));
        } else {
            long seed = ctx.tournament().getId() == null ? 0L : ctx.tournament().getId();
            Collections.shuffle(players, new Random(seed));
        }

        TournamentPlayer byePlayer = null;
        if (players.size() % 2 == 1) {
            byePlayer = players.remove(players.size() - 1);
        }

        List<PairingProposal> proposals = new ArrayList<>();
        int board = 1;

        if (method == SwissFirstRoundMethod.RATING_SPLIT) {
            int half = players.size() / 2;
            for (int i = 0; i < half; i++) {
                proposals.add(toProposal(board++, players.get(i), players.get(i + half), false));
            }
        } else {
            for (int i = 0; i < players.size(); i += 2) {
                proposals.add(toProposal(board++, players.get(i), players.get(i + 1), false));
            }
        }

        if (byePlayer != null) {
            proposals.add(PairingProposal.bye(board, byePlayer.getId()));
        }
        return List.copyOf(proposals);
    }

    private static List<PairingProposal> pairSubsequent(PairingContext ctx) {
        List<TournamentPlayer> remaining = new ArrayList<>(ctx.players());
        remaining.sort(SwissScoreGroupBuilder.standingOrder());

        TournamentPlayer byePlayer = null;
        if (remaining.size() % 2 == 1) {
            byePlayer = selectByeRecipient(remaining, ctx.previousByeRecipients());
            remaining.remove(byePlayer);
        }

        List<List<TournamentPlayer>> groups = SwissScoreGroupBuilder.build(remaining);
        List<TournamentPlayer> floaters = new ArrayList<>();
        List<PairingProposal> proposals = new ArrayList<>();
        int board = 1;

        for (int g = 0; g < groups.size(); g++) {
            List<TournamentPlayer> working = new ArrayList<>(floaters);
            working.addAll(groups.get(g));
            floaters.clear();
            working.sort(SwissScoreGroupBuilder.standingOrder());

            if (working.size() % 2 == 1) {
                if (g == groups.size() - 1) {
                    throw new ValidationException(
                            "Swiss pairing left an unpaired player after score-group processing");
                }
                floaters.add(working.remove(working.size() - 1));
            }

            for (TentativePair pair : pairScoreGroup(working, ctx.previousPairings())) {
                proposals.add(toProposal(board++, pair.a(), pair.b(), pair.rematch()));
            }
        }

        if (!floaters.isEmpty()) {
            throw new ValidationException("Swiss pairing could not place floater(s)");
        }

        if (byePlayer != null) {
            proposals.add(PairingProposal.bye(board, byePlayer.getId()));
        }
        return List.copyOf(proposals);
    }

    /**
     * Bye goes to the lowest-rated player in the lowest score group who has not yet
     * received a bye when possible (SDD §8.5).
     */
    static TournamentPlayer selectByeRecipient(List<TournamentPlayer> players,
                                               Set<Long> previousByeRecipients) {
        Set<Long> hadBye = previousByeRecipients == null ? Set.of() : previousByeRecipients;
        Comparator<TournamentPlayer> byeOrder = Comparator
                .comparing((TournamentPlayer tp) -> hadBye.contains(tp.getId()))
                .thenComparing(tp -> tp.getPoints() == null ? BigDecimal.ZERO : tp.getPoints())
                .thenComparingInt(TournamentPlayer::getCurrentRating)
                .thenComparing(TournamentPlayer::getId);

        return players.stream()
                .min(byeOrder)
                .orElseThrow(() -> new ValidationException("No player available for bye"));
    }

    /**
     * Dutch-style pairing within one (even-sized) working set: top half vs bottom half,
     * greedily avoiding rematches, then adjacent swaps; forced rematches flagged.
     */
    static List<TentativePair> pairScoreGroup(List<TournamentPlayer> group,
                                              Set<LongPair> previousPairings) {
        if (group.isEmpty()) {
            return List.of();
        }
        if (group.size() % 2 != 0) {
            throw new IllegalArgumentException("Score group working set must be even");
        }
        Set<LongPair> previous = previousPairings == null ? Set.of() : previousPairings;

        List<TournamentPlayer> sorted = new ArrayList<>(group);
        sorted.sort(Comparator
                .comparingInt(TournamentPlayer::getCurrentRating).reversed()
                .thenComparing(TournamentPlayer::getId));

        int half = sorted.size() / 2;
        List<TournamentPlayer> upper = new ArrayList<>(sorted.subList(0, half));
        List<TournamentPlayer> lower = new ArrayList<>(sorted.subList(half, sorted.size()));

        int[] pairing = greedyPair(upper, lower, previous);
        resolveRematchesWithSwaps(upper, lower, pairing, previous);

        List<TentativePair> result = new ArrayList<>(half);
        for (int i = 0; i < half; i++) {
            TournamentPlayer a = upper.get(i);
            TournamentPlayer b = lower.get(pairing[i]);
            boolean rematch = isRematch(a, b, previous);
            result.add(new TentativePair(a, b, rematch));
        }
        return result;
    }

    private static int[] greedyPair(List<TournamentPlayer> upper,
                                    List<TournamentPlayer> lower,
                                    Set<LongPair> previous) {
        int half = upper.size();
        boolean[] used = new boolean[half];
        int[] pairing = new int[half];

        for (int i = 0; i < half; i++) {
            int chosen = -1;
            for (int j = 0; j < half; j++) {
                if (used[j]) {
                    continue;
                }
                if (!isRematch(upper.get(i), lower.get(j), previous)) {
                    chosen = j;
                    break;
                }
            }
            if (chosen < 0) {
                for (int j = 0; j < half; j++) {
                    if (!used[j]) {
                        chosen = j;
                        break;
                    }
                }
            }
            used[chosen] = true;
            pairing[i] = chosen;
        }
        return pairing;
    }

    /**
     * O(n²) adjacent/exhaustive swap attempts to eliminate rematches when alternatives exist.
     */
    private static void resolveRematchesWithSwaps(List<TournamentPlayer> upper,
                                                  List<TournamentPlayer> lower,
                                                  int[] pairing,
                                                  Set<LongPair> previous) {
        int half = upper.size();
        boolean improved;
        int guard = 0;
        int maxPasses = half * half + 1;
        do {
            improved = false;
            guard++;
            for (int i = 0; i < half; i++) {
                if (!isRematch(upper.get(i), lower.get(pairing[i]), previous)) {
                    continue;
                }
                for (int j = 0; j < half; j++) {
                    if (j == i) {
                        continue;
                    }
                    int li = pairing[i];
                    int lj = pairing[j];
                    boolean iOk = !isRematch(upper.get(i), lower.get(lj), previous);
                    boolean jOk = !isRematch(upper.get(j), lower.get(li), previous);
                    if (iOk && jOk) {
                        pairing[i] = lj;
                        pairing[j] = li;
                        improved = true;
                        break;
                    }
                    // Prefer fixing i when j was already a rematch
                    if (iOk && isRematch(upper.get(j), lower.get(lj), previous)) {
                        pairing[i] = lj;
                        pairing[j] = li;
                        improved = true;
                        break;
                    }
                }
            }
        } while (improved && guard < maxPasses);
    }

    private static boolean isRematch(TournamentPlayer a, TournamentPlayer b, Set<LongPair> previous) {
        return previous.contains(LongPair.of(a.getId(), b.getId()));
    }

    private static PairingProposal toProposal(int board, TournamentPlayer a, TournamentPlayer b,
                                              boolean rematch) {
        TournamentPlayer[] colors = SwissColorAssigner.assign(a, b);
        return PairingProposal.game(board, colors[0].getId(), colors[1].getId(), rematch);
    }

    record TentativePair(TournamentPlayer a, TournamentPlayer b, boolean rematch) {
        TentativePair {
            Objects.requireNonNull(a);
            Objects.requireNonNull(b);
        }
    }
}
