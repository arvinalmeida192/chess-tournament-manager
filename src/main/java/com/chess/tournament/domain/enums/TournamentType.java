package com.chess.tournament.domain.enums;

public enum TournamentType {
    ROUND_ROBIN,
    KNOCKOUT,
    SWISS;

    public static TournamentType fromDbValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Tournament type cannot be null");
        }
        return TournamentType.valueOf(value.trim());
    }
}
