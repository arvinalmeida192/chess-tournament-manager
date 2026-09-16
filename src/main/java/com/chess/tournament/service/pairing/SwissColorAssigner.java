package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.TournamentPlayer;

import java.util.Objects;

/**
 * Assigns white/black for Swiss games (BR-SWI-004 / SDD §8.5).
 * Prefer white for the player with lower {@code color_balance} (needs white);
 * on tie, higher rating gets white; then lower tournament-player id.
 */
public final class SwissColorAssigner {

    private SwissColorAssigner() {
    }

    /**
     * @return {@code [white, black]}
     */
    public static TournamentPlayer[] assign(TournamentPlayer a, TournamentPlayer b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        int cmp = Integer.compare(a.getColorBalance(), b.getColorBalance());
        if (cmp < 0) {
            return new TournamentPlayer[]{a, b};
        }
        if (cmp > 0) {
            return new TournamentPlayer[]{b, a};
        }
        cmp = Integer.compare(b.getCurrentRating(), a.getCurrentRating());
        if (cmp != 0) {
            return cmp > 0 ? new TournamentPlayer[]{b, a} : new TournamentPlayer[]{a, b};
        }
        if (a.getId() <= b.getId()) {
            return new TournamentPlayer[]{a, b};
        }
        return new TournamentPlayer[]{b, a};
    }
}
