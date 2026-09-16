package com.chess.tournament.service;

/**
 * Elo rating helper (SDD §9.1, BR-RAT-002 K=32).
 */
public class RatingService {

    public static final int K = 32;

    public record RatingOutcome(int newRating, int delta) {
    }

    /**
     * @param actualScore 1.0 win, 0.5 draw, 0.0 loss
     */
    public RatingOutcome rate(int playerRating, int opponentRating, double actualScore) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (opponentRating - playerRating) / 400.0));
        int delta = (int) Math.round(K * (actualScore - expected));
        return new RatingOutcome(playerRating + delta, delta);
    }
}
