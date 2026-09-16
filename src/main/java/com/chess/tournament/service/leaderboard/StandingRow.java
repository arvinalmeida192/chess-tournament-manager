package com.chess.tournament.service.leaderboard;

import com.chess.tournament.domain.enums.QualificationStatus;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One row of a tournament leaderboard (SDD §5.2 StandingRow).
 */
public final class StandingRow {

    private final int rank;
    private final long tournamentPlayerId;
    private final String playerName;
    private final int rating;
    private final BigDecimal points;
    private final int wins;
    private final int draws;
    private final int losses;
    private final int startRating;
    private final QualificationStatus qualificationStatus;

    public StandingRow(int rank,
                       long tournamentPlayerId,
                       String playerName,
                       int rating,
                       BigDecimal points,
                       int wins,
                       int draws,
                       int losses,
                       int startRating,
                       QualificationStatus qualificationStatus) {
        this.rank = rank;
        this.tournamentPlayerId = tournamentPlayerId;
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.rating = rating;
        this.points = points == null ? BigDecimal.ZERO : points;
        this.wins = wins;
        this.draws = draws;
        this.losses = losses;
        this.startRating = startRating;
        this.qualificationStatus = qualificationStatus;
    }

    public int getRank() {
        return rank;
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

    public int getFinalRating() {
        return rating;
    }

    public QualificationStatus getQualificationStatus() {
        return qualificationStatus;
    }
}
