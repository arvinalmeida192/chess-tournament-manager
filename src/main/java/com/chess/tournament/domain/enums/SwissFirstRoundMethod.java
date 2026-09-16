package com.chess.tournament.domain.enums;

public enum SwissFirstRoundMethod {
    RANDOM,
    RATING_SPLIT;

    public static SwissFirstRoundMethod fromDbValue(String value) {
        if (value == null) {
            return RANDOM;
        }
        return SwissFirstRoundMethod.valueOf(value.trim());
    }
}
