package com.chess.tournament.domain.enums;

public enum QualificationStatus {
    PENDING,
    QUALIFIED,
    ELIMINATED,
    NOT_APPLICABLE;

    public static QualificationStatus fromDbValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Qualification status cannot be null");
        }
        return QualificationStatus.valueOf(value.trim());
    }
}
