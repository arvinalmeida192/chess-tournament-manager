package com.chess.tournament.service.leaderboard;

import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.enums.GameResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingComparatorTest {

    private final StandingComparator comparator = new StandingComparator();

    @Test
    void sortsByPointsDescending() {
        StandingEntry low = entry(1, "Ann", 1600, "1.0");
        StandingEntry high = entry(2, "Bob", 1400, "2.0");

        List<StandingEntry> sorted = comparator.sort(List.of(low, high), List.of());
        assertEquals(2L, sorted.get(0).getTournamentPlayerId());
        assertEquals(1L, sorted.get(1).getTournamentPlayerId());
    }

    @Test
    void twoWayTie_usesHeadToHead() {
        // Same points; Alice beat Bob head-to-head
        StandingEntry alice = entry(1, "Alice", 1500, "1.0");
        StandingEntry bob = entry(2, "Bob", 1600, "1.0");
        Game h2h = game(1L, 2L, GameResult.WHITE_WIN, "1", "0");

        List<StandingEntry> sorted = comparator.sort(List.of(bob, alice), List.of(h2h));
        assertEquals(1L, sorted.get(0).getTournamentPlayerId());
        assertEquals(2L, sorted.get(1).getTournamentPlayerId());
    }

    @Test
    void twoWayTie_equalH2H_usesHigherRating() {
        StandingEntry lower = entry(1, "Zoe", 1400, "2.0");
        StandingEntry higher = entry(2, "Amy", 1550, "2.0");
        Game draw = game(1L, 2L, GameResult.DRAW, "0.5", "0.5");

        List<StandingEntry> sorted = comparator.sort(List.of(lower, higher), List.of(draw));
        assertEquals(2L, sorted.get(0).getTournamentPlayerId());
        assertEquals(1L, sorted.get(1).getTournamentPlayerId());
    }

    @Test
    void threeWayTie_skipsHeadToHead_usesRatingThenName() {
        // Carol beat Ann, but three-way tie must ignore H2H (BR-TIE-002)
        StandingEntry ann = entry(1, "Ann", 1500, "2.0");
        StandingEntry bob = entry(2, "Bob", 1600, "2.0");
        StandingEntry carol = entry(3, "Carol", 1400, "2.0");
        Game carolBeatAnn = game(3L, 1L, GameResult.WHITE_WIN, "1", "0");

        List<StandingEntry> sorted = comparator.sort(
                List.of(ann, carol, bob), List.of(carolBeatAnn));

        assertEquals(2L, sorted.get(0).getTournamentPlayerId()); // Bob highest rating
        assertEquals(1L, sorted.get(1).getTournamentPlayerId()); // Ann next rating
        assertEquals(3L, sorted.get(2).getTournamentPlayerId()); // Carol lowest rating
    }

    @Test
    void equalPointsRating_usesAlphabeticalName() {
        StandingEntry zed = entry(1, "Zed", 1500, "1.0");
        StandingEntry amy = entry(2, "Amy", 1500, "1.0");
        // three+ group would use rating then name; with 2 and no games, H2H=0 then rating/name
        List<StandingEntry> sorted = comparator.sort(List.of(zed, amy), List.of());
        assertEquals(2L, sorted.get(0).getTournamentPlayerId());
        assertEquals(1L, sorted.get(1).getTournamentPlayerId());
    }

    @Test
    void differentPointGroups_preservedIndependently() {
        StandingEntry first = entry(1, "Top", 1200, "3.0");
        StandingEntry tiedA = entry(2, "Bee", 1800, "1.0");
        StandingEntry tiedB = entry(3, "Cee", 1700, "1.0");
        StandingEntry last = entry(4, "Low", 2000, "0.0");

        List<StandingEntry> sorted = comparator.sort(
                List.of(last, tiedB, first, tiedA), List.of());

        assertEquals(1L, sorted.get(0).getTournamentPlayerId());
        assertEquals(2L, sorted.get(1).getTournamentPlayerId()); // higher rating in 1.0 group
        assertEquals(3L, sorted.get(2).getTournamentPlayerId());
        assertEquals(4L, sorted.get(3).getTournamentPlayerId());
    }

    private static StandingEntry entry(long tpId, String name, int rating, String points) {
        return new StandingEntry(tpId, name, rating, new BigDecimal(points), 0, 0, 0, rating);
    }

    private static Game game(long white, long black, GameResult result, String ws, String bs) {
        Game g = new Game();
        g.setWhiteTournamentPlayerId(white);
        g.setBlackTournamentPlayerId(black);
        g.setResult(result);
        g.setWhiteScore(new BigDecimal(ws));
        g.setBlackScore(new BigDecimal(bs));
        return g;
    }
}
