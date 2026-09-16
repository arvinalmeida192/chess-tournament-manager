package com.chess.tournament.service.leaderboard;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Mutable standing used while sorting before ranks are assigned.
 */
public final class StandingEntry {

    private final long tournamentPlayerId;
    private final String playerName;
    private final int rating;
    private final BigDecimal points;
    private final int wins;
    private final int draws;
    private final int losses;
    private final int startRating;

    public StandingEntry(long tournamentPlayerId,
                         String playerName,
                         int rating,
                         BigDecimal points,
                         int wins,
                         int draws,
                         int losses,
                         int startRating) {
        this.tournamentPlayerId = tournamentPlayerId;
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.rating = rating;
        this.points = points == null ? BigDecimal.ZERO : points;
        this.wins = wins;
        this.draws = draws;
        this.losses = losses;
        this.startRating = startRating;
    }

    public long getTournamentPlayerId() {
        return tournamentPlayerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getRating() {
        return rating;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public int getWins() {
        return wins;
    }

    public int getDraws() {
        return draws;
    }

    public int getLosses() {
        return losses;
    }

    public int getStartRating() {
        return startRating;
    }
}
