package com.chess.tournament.service.validation;

import com.chess.tournament.exception.ValidationException;

/**
 * Knockout: rounds = ceil(log2(N)) — bracket depth with byes to next power of two.
 */
public final class KnockoutValidator {

    private KnockoutValidator() {
    }

    public static int expectedRounds(int playerCount) {
        if (playerCount < 2) {
            throw new ValidationException("Knockout requires at least 2 players to compute rounds");
        }
        return (int) Math.ceil(Math.log(playerCount) / Math.log(2));
    }

    public static void validateRounds(int roundsPlanned, int playerCount) {
        int expected = expectedRounds(playerCount);
        if (roundsPlanned != expected) {
            throw new ValidationException(
                    "Knockout rounds must be " + expected + " for " + playerCount
                            + " players (got " + roundsPlanned + ")");
        }
    }

    public static void validateMinimumPlayers(int playerCount) {
        if (playerCount < 2) {
            throw new ValidationException("Knockout requires at least 2 enrolled players");
        }
    }
}
