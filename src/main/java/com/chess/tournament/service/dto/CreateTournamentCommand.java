package com.chess.tournament.service.dto;

import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentType;

/**
 * Command to create a tournament in DRAFT status.
 */
public final class CreateTournamentCommand {

    private final String name;
    private final TournamentType type;
    private final int roundsPlanned;
    private final int qualifiersCount;
    private final SwissFirstRoundMethod swissFirstRoundMethod;

    public CreateTournamentCommand(String name, TournamentType type, int roundsPlanned,
                                   int qualifiersCount, SwissFirstRoundMethod swissFirstRoundMethod) {
        this.name = name;
        this.type = type;
        this.roundsPlanned = roundsPlanned;
        this.qualifiersCount = qualifiersCount;
        this.swissFirstRoundMethod = swissFirstRoundMethod;
    }

    public String getName() {
        return name;
    }

    public TournamentType getType() {
        return type;
    }

    public int getRoundsPlanned() {
        return roundsPlanned;
    }

    public int getQualifiersCount() {
        return qualifiersCount;
    }

    public SwissFirstRoundMethod getSwissFirstRoundMethod() {
        return swissFirstRoundMethod;
    }
}
