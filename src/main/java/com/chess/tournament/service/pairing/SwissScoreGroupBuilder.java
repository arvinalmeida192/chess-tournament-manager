package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.TournamentPlayer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Partitions Swiss players into score groups (equal points), highest score first (BR-SWI-001).
 */
public final class SwissScoreGroupBuilder {

    private SwissScoreGroupBuilder() {
    }

    public static Comparator<TournamentPlayer> standingOrder() {
        return Comparator
                .comparing(TournamentPlayer::getPoints, Comparator.nullsFirst(BigDecimal::compareTo)).reversed()
                .thenComparing(TournamentPlayer::getCurrentRating, Comparator.reverseOrder())
                .thenComparing(TournamentPlayer::getId, Comparator.nullsLast(Long::compareTo));
    }

    /**
     * @param players players still to pair this round (already bye-extracted if needed)
     * @return groups from highest points to lowest; within each group sorted by {@link #standingOrder()}
     */
    public static List<List<TournamentPlayer>> build(List<TournamentPlayer> players) {
        Objects.requireNonNull(players, "players");
        if (players.isEmpty()) {
            return List.of();
        }
        List<TournamentPlayer> sorted = new ArrayList<>(players);
        sorted.sort(standingOrder());

        List<List<TournamentPlayer>> groups = new ArrayList<>();
        List<TournamentPlayer> current = new ArrayList<>();
        BigDecimal currentPoints = null;

        for (TournamentPlayer tp : sorted) {
            BigDecimal pts = tp.getPoints() == null ? BigDecimal.ZERO : tp.getPoints();
            if (current.isEmpty()) {
                current.add(tp);
                currentPoints = pts;
            } else if (pts.compareTo(currentPoints) == 0) {
                current.add(tp);
            } else {
                groups.add(List.copyOf(current));
                current = new ArrayList<>();
                current.add(tp);
                currentPoints = pts;
            }
        }
        if (!current.isEmpty()) {
            groups.add(List.copyOf(current));
        }
        return List.copyOf(groups);
    }
}
