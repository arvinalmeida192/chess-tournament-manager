package com.chess.tournament.domain;

import com.chess.tournament.domain.enums.QualificationStatus;

import java.math.BigDecimal;
import java.util.Objects;

public final class TournamentPlayer {

    private Long id;
    private long tournamentId;
    private long playerId;
    private int startRating;
    private int currentRating;
    private BigDecimal points;
    private int wins;
    private int draws;
    private int losses;
    private int gamesPlayed;
    private QualificationStatus qualificationStatus;
    private int colorBalance;

    public TournamentPlayer() {
        this.points = BigDecimal.ZERO;
        this.qualificationStatus = QualificationStatus.PENDING;
    }

    public TournamentPlayer(Long id, long tournamentId, long playerId, int startRating, int currentRating,
                            BigDecimal points, int wins, int draws, int losses, int gamesPlayed,
                            QualificationStatus qualificationStatus, int colorBalance) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.playerId = playerId;
        this.startRating = startRating;
        this.currentRating = currentRating;
        this.points = points;
        this.wins = wins;
        this.draws = draws;
        this.losses = losses;
        this.gamesPlayed = gamesPlayed;
        this.qualificationStatus = qualificationStatus;
        this.colorBalance = colorBalance;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getStartRating() {
        return startRating;
    }

    public void setStartRating(int startRating) {
        this.startRating = startRating;
    }

    public int getCurrentRating() {
        return currentRating;
    }

    public void setCurrentRating(int currentRating) {
        this.currentRating = currentRating;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public void setPoints(BigDecimal points) {
        this.points = points;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getDraws() {
        return draws;
    }

    public void setDraws(int draws) {
        this.draws = draws;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public QualificationStatus getQualificationStatus() {
        return qualificationStatus;
    }

    public void setQualificationStatus(QualificationStatus qualificationStatus) {
        this.qualificationStatus = qualificationStatus;
    }

    public int getColorBalance() {
        return colorBalance;
    }

    public void setColorBalance(int colorBalance) {
        this.colorBalance = colorBalance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TournamentPlayer that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
