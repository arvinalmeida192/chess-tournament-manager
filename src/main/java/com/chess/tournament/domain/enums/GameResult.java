package com.chess.tournament.domain.enums;

public enum GameResult {
    PENDING,
    WHITE_WIN,
    BLACK_WIN,
    DRAW,
    BYE;

    public static GameResult fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        return GameResult.valueOf(value.trim());
    }
}
