package com.chess.tournament.domain;

import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;

import java.time.Instant;
import java.util.Objects;

public final class Tournament {

    private Long id;
    private String name;
    private TournamentType type;
    private int roundsPlanned;
    private int qualifiersCount;
    private SwissFirstRoundMethod swissFirstRoundMethod;
    private TournamentStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    public Tournament() {
        this.status = TournamentStatus.DRAFT;
        this.swissFirstRoundMethod = SwissFirstRoundMethod.RANDOM;
    }

    public Tournament(Long id, String name, TournamentType type, int roundsPlanned, int qualifiersCount,
                      SwissFirstRoundMethod swissFirstRoundMethod, TournamentStatus status,
                      Instant createdAt, Instant startedAt, Instant completedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.roundsPlanned = roundsPlanned;
        this.qualifiersCount = qualifiersCount;
        this.swissFirstRoundMethod = swissFirstRoundMethod;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TournamentType getType() {
        return type;
    }

    public void setType(TournamentType type) {
        this.type = type;
    }

    public int getRoundsPlanned() {
        return roundsPlanned;
    }

    public void setRoundsPlanned(int roundsPlanned) {
        this.roundsPlanned = roundsPlanned;
    }

    public int getQualifiersCount() {
        return qualifiersCount;
    }

    public void setQualifiersCount(int qualifiersCount) {
        this.qualifiersCount = qualifiersCount;
    }

    public SwissFirstRoundMethod getSwissFirstRoundMethod() {
        return swissFirstRoundMethod;
    }

    public void setSwissFirstRoundMethod(SwissFirstRoundMethod swissFirstRoundMethod) {
        this.swissFirstRoundMethod = swissFirstRoundMethod;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public void setStatus(TournamentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
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
        if (!(o instanceof Tournament tournament)) {
            return false;
        }
        return Objects.equals(id, tournament.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
