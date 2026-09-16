package com.chess.tournament.service.validation;

import com.chess.tournament.exception.ValidationException;

/**
 * Round Robin: schedule length is N-1 (even N) or N (odd N with bye rotation).
 * Validated against enrollment count at tournament start (BR-RR-001).
 */
public final class RoundRobinValidator {

    private RoundRobinValidator() {
    }

    public static int expectedRounds(int playerCount) {
        if (playerCount < 1) {
            throw new ValidationException("Round Robin requires at least 1 player to compute rounds");
        }
        return (playerCount % 2 == 0) ? playerCount - 1 : playerCount;
    }

    public static void validateRounds(int roundsPlanned, int playerCount) {
        int expected = expectedRounds(playerCount);
        if (roundsPlanned != expected) {
            throw new ValidationException(
                    "Round Robin rounds must be " + expected + " for " + playerCount
                            + " players (got " + roundsPlanned + ")");
        }
    }

    public static void validateMinimumPlayers(int playerCount) {
        if (playerCount < 3) {
            throw new ValidationException("Round Robin requires at least 3 enrolled players");
        }
    }
}
