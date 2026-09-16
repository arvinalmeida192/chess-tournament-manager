package com.chess.tournament.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RatingServiceTest {

    private final RatingService ratingService = new RatingService();

    @Test
    void equalRatings_draw_zeroDeltaBoth() {
        RatingService.RatingOutcome white = ratingService.rate(1500, 1500, 0.5);
        RatingService.RatingOutcome black = ratingService.rate(1500, 1500, 0.5);
        assertEquals(0, white.delta());
        assertEquals(0, black.delta());
        assertEquals(1500, white.newRating());
        assertEquals(1500, black.newRating());
    }

    @Test
    void equalRatings_whiteWin_plusMinusSixteen() {
        RatingService.RatingOutcome white = ratingService.rate(1500, 1500, 1.0);
        RatingService.RatingOutcome black = ratingService.rate(1500, 1500, 0.0);
        assertEquals(16, white.delta());
        assertEquals(-16, black.delta());
        assertEquals(1516, white.newRating());
        assertEquals(1484, black.newRating());
    }

    @Test
    void favoriteWin_smallerGain() {
        // 1600 vs 1400, white (favorite) wins
        // E_w = 1/(1+10^((1400-1600)/400)) = 1/(1+10^(-0.5)) ≈ 0.7597469
        // delta = round(32 * (1 - 0.7597469)) = round(7.688) = 8
        RatingService.RatingOutcome white = ratingService.rate(1600, 1400, 1.0);
        RatingService.RatingOutcome black = ratingService.rate(1400, 1600, 0.0);
        assertEquals(8, white.delta());
        assertEquals(-8, black.delta());
        assertEquals(1608, white.newRating());
        assertEquals(1392, black.newRating());
    }

    @Test
    void underdogWin_largerGain() {
        // 1400 vs 1600, white (underdog) wins
        // E_w = 1/(1+10^((1600-1400)/400)) = 1/(1+10^(0.5)) ≈ 0.2402531
        // delta = round(32 * (1 - 0.2402531)) = round(24.3119) = 24
        RatingService.RatingOutcome white = ratingService.rate(1400, 1600, 1.0);
        RatingService.RatingOutcome black = ratingService.rate(1600, 1400, 0.0);
        assertEquals(24, white.delta());
        assertEquals(-24, black.delta());
        assertEquals(1424, white.newRating());
        assertEquals(1576, black.newRating());
    }

    @Test
    void kFactor_isThirtyTwo() {
        assertEquals(32, RatingService.K);
    }
}
