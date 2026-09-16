package com.chess.tournament.domain.enums;

public enum RoundStatus {
    PENDING_PAIRINGS,
    PAIRINGS_PUBLISHED,
    IN_PROGRESS,
    COMPLETED;

    public static RoundStatus fromDbValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Round status cannot be null");
        }
        return RoundStatus.valueOf(value.trim());
    }
}
