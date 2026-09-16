package com.chess.tournament.domain.enums;

public enum TournamentStatus {
    DRAFT,
    ACTIVE,
    COMPLETED,
    CANCELLED;

    public static TournamentStatus fromDbValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Tournament status cannot be null");
        }
        return TournamentStatus.valueOf(value.trim());
    }
}
