package com.chess.tournament.service.validation;

import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.dto.CreateTournamentCommand;

/**
 * Common tournament field validation plus format-specific rules.
 */
public final class TournamentValidator {

    private TournamentValidator() {
    }

    public static void validateCreate(CreateTournamentCommand command) {
        if (command == null) {
            throw new ValidationException("Tournament command is required");
        }
        if (command.getName() == null || command.getName().isBlank()) {
            throw new ValidationException("Tournament name is required");
        }
        if (command.getType() == null) {
            throw new ValidationException("Tournament type is required");
        }
        if (command.getRoundsPlanned() < 1) {
            throw new ValidationException("Rounds planned must be at least 1");
        }
        if (command.getQualifiersCount() < 0) {
            throw new ValidationException("Qualifiers count must be greater than or equal to 0");
        }
        if (command.getType() == TournamentType.SWISS) {
            SwissValidator.validateRounds(command.getRoundsPlanned());
            if (command.getSwissFirstRoundMethod() == null) {
                throw new ValidationException("Swiss first-round method is required for Swiss tournaments");
            }
        }
    }

    /**
     * Validates enrollment count and that rounds_planned matches the format schedule for N players.
     */
    public static void validateStart(TournamentType type, int roundsPlanned, int playerCount) {
        switch (type) {
            case ROUND_ROBIN -> {
                RoundRobinValidator.validateMinimumPlayers(playerCount);
                RoundRobinValidator.validateRounds(roundsPlanned, playerCount);
            }
            case KNOCKOUT -> {
                KnockoutValidator.validateMinimumPlayers(playerCount);
                KnockoutValidator.validateRounds(roundsPlanned, playerCount);
            }
            case SWISS -> {
                SwissValidator.validateMinimumPlayers(playerCount);
                SwissValidator.validateRounds(roundsPlanned);
            }
            default -> throw new ValidationException("Unsupported tournament type: " + type);
        }
    }

    public static SwissFirstRoundMethod resolveSwissMethod(TournamentType type,
                                                           SwissFirstRoundMethod method) {
        if (type != TournamentType.SWISS) {
            return SwissFirstRoundMethod.RANDOM;
        }
        return method == null ? SwissFirstRoundMethod.RANDOM : method;
    }
}
