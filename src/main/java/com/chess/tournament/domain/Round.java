package com.chess.tournament.domain;

import com.chess.tournament.domain.enums.RoundStatus;

import java.time.Instant;
import java.util.Objects;

public final class Round {

    private Long id;
    private long tournamentId;
    private int roundNumber;
    private RoundStatus status;
    private Instant pairedAt;
    private Instant completedAt;

    public Round() {
        this.status = RoundStatus.PENDING_PAIRINGS;
    }

    public Round(Long id, long tournamentId, int roundNumber, RoundStatus status,
                 Instant pairedAt, Instant completedAt) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.roundNumber = roundNumber;
        this.status = status;
        this.pairedAt = pairedAt;
        this.completedAt = completedAt;
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

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public RoundStatus getStatus() {
        return status;
    }

    public void setStatus(RoundStatus status) {
        this.status = status;
    }

    public Instant getPairedAt() {
        return pairedAt;
    }

    public void setPairedAt(Instant pairedAt) {
        this.pairedAt = pairedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Round round)) {
            return false;
        }
        return Objects.equals(id, round.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
