package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.LongPair;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable inputs for a {@link PairingStrategy}.
 *
 * @param previousRoundGames games from round {@code roundNumber - 1} (empty for round 1);
 *                           required for knockout advancement pairing
 * @param previousByeRecipients tournament-player ids that already received a bye (Swiss)
 */
public record PairingContext(
        Tournament tournament,
        int roundNumber,
        List<TournamentPlayer> players,
        Set<LongPair> previousPairings,
        List<Game> currentRoundGames,
        List<Game> previousRoundGames,
        Set<Long> previousByeRecipients
) {
    public PairingContext {
        Objects.requireNonNull(tournament, "tournament");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(previousPairings, "previousPairings");
        Objects.requireNonNull(currentRoundGames, "currentRoundGames");
        Objects.requireNonNull(previousRoundGames, "previousRoundGames");
        Objects.requireNonNull(previousByeRecipients, "previousByeRecipients");
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be >= 1");
        }
        players = List.copyOf(players);
        previousPairings = Set.copyOf(previousPairings);
        currentRoundGames = List.copyOf(currentRoundGames);
        previousRoundGames = List.copyOf(previousRoundGames);
        previousByeRecipients = Set.copyOf(previousByeRecipients);
    }

    /** Convenience for strategies that do not need prior-round games or bye history. */
    public static PairingContext withoutPrevious(Tournament tournament, int roundNumber,
                                                 List<TournamentPlayer> players,
                                                 Set<LongPair> previousPairings) {
        return new PairingContext(tournament, roundNumber, players, previousPairings,
                List.of(), List.of(), Set.of());
    }
}
