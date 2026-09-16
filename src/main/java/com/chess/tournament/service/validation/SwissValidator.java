package com.chess.tournament.service.validation;

import com.chess.tournament.exception.ValidationException;

/**
 * Swiss: rounds_planned &gt;= 1; minimum 2 players (recommend 4 in UI).
 */
public final class SwissValidator {

    private SwissValidator() {
    }

    public static void validateRounds(int roundsPlanned) {
        if (roundsPlanned < 1) {
            throw new ValidationException("Swiss tournaments require at least 1 round");
        }
    }

    public static void validateMinimumPlayers(int playerCount) {
        if (playerCount < 2) {
            throw new ValidationException("Swiss requires at least 2 enrolled players");
        }
    }

    public static boolean shouldWarnLowPlayerCount(int playerCount) {
        return playerCount >= 2 && playerCount < 4;
    }
}
