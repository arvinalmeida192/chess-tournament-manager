package com.chess.tournament.service.leaderboard;

import com.chess.tournament.domain.Game;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Orders standings per BR-TIE-001 / BR-TIE-002 (SDD §10.1).
 * <p>
 * Within a points group of size 2, head-to-head is applied; for larger ties H2H is skipped.
 */
public final class StandingComparator {

    private final HeadToHeadCalculator headToHeadCalculator;

    public StandingComparator() {
        this(new HeadToHeadCalculator());
    }

    public StandingComparator(HeadToHeadCalculator headToHeadCalculator) {
        this.headToHeadCalculator = Objects.requireNonNull(headToHeadCalculator);
    }

    /**
     * Returns a new list sorted by tournament points (desc) then tie-breakers.
     */
    public List<StandingEntry> sort(List<StandingEntry> entries, List<Game> games) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(games, "games");

        Map<BigDecimal, List<StandingEntry>> byPoints = new TreeMap<>(Comparator.reverseOrder());
        for (StandingEntry entry : entries) {
            byPoints.computeIfAbsent(entry.getPoints(), k -> new ArrayList<>()).add(entry);
        }

        List<StandingEntry> sorted = new ArrayList<>(entries.size());
        for (List<StandingEntry> group : byPoints.values()) {
            List<StandingEntry> ordered = new ArrayList<>(group);
            if (ordered.size() == 2) {
                ordered.sort(twoWayTieComparator(games));
            } else {
                ordered.sort(ratingThenName());
            }
            sorted.addAll(ordered);
        }
        return sorted;
    }

    private Comparator<StandingEntry> twoWayTieComparator(List<Game> games) {
        return (a, b) -> {
            int h2h = headToHeadCalculator.compare(
                    a.getTournamentPlayerId(), b.getTournamentPlayerId(), games);
            if (h2h != 0) {
                // Higher head-to-head points ranks first
                return -h2h;
            }
            return ratingThenName().compare(a, b);
        };
    }

    private static Comparator<StandingEntry> ratingThenName() {
        return Comparator.comparingInt(StandingEntry::getRating).reversed()
                .thenComparing(StandingEntry::getPlayerName, String.CASE_INSENSITIVE_ORDER);
    }
}
