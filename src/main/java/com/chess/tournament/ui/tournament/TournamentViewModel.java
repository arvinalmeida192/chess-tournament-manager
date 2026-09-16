package com.chess.tournament.ui.tournament;

import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;

import java.util.Optional;

/**
 * State-driven enablement flags for the tournament dashboard (SDD §11.3).
 */
public final class TournamentViewModel {

    private final Tournament tournament;
    private final int enrolledCount;
    private final Optional<Round> round1;
    private final Optional<Round> pairableRound;
    private final Optional<Round> resultsRound;
    private final boolean enrollmentLocked;

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, boolean enrollmentLocked) {
        this(tournament, enrolledCount, round1, Optional.empty(), Optional.empty(), enrollmentLocked);
    }

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, Optional<Round> pairableRound,
                               boolean enrollmentLocked) {
        this(tournament, enrolledCount, round1, pairableRound, Optional.empty(), enrollmentLocked);
    }

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, Optional<Round> pairableRound,
                               Optional<Round> resultsRound, boolean enrollmentLocked) {
        this.tournament = tournament;
        this.enrolledCount = enrolledCount;
        this.round1 = round1;
        this.pairableRound = pairableRound;
        this.resultsRound = resultsRound;
        this.enrollmentLocked = enrollmentLocked;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public Optional<Round> getRound1() {
        return round1;
    }

    public Optional<Round> getPairableRound() {
        return pairableRound;
    }

    public Optional<Round> getResultsRound() {
        return resultsRound;
    }

    public boolean isEnrollmentLocked() {
        return enrollmentLocked;
    }

    public boolean canEnroll() {
        return !enrollmentLocked
                && (tournament.getStatus() == TournamentStatus.DRAFT
                || tournament.getStatus() == TournamentStatus.ACTIVE);
    }

    public boolean canStart() {
        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            return false;
        }
        return switch (tournament.getType()) {
            case ROUND_ROBIN -> enrolledCount >= 3;
            case KNOCKOUT, SWISS -> enrolledCount >= 2;
        };
    }

    public boolean canGeneratePairings() {
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            return false;
        }
        if (pairableRound.isPresent()) {
            return pairableRound.get().getStatus() == RoundStatus.PENDING_PAIRINGS;
        }
        return round1.isPresent() && round1.get().getStatus() == RoundStatus.PENDING_PAIRINGS;
    }

    /** Open pairings UI for ACTIVE tournaments that have at least round 1. */
    public boolean canOpenPairings() {
        return tournament.getStatus() == TournamentStatus.ACTIVE && round1.isPresent();
    }

    public boolean canEnterResults() {
        return tournament.getStatus() == TournamentStatus.ACTIVE && resultsRound.isPresent();
    }

    public boolean canCompleteRound() {
        return canEnterResults();
    }

    public boolean canFinalize() {
        return false; // Phase 9
    }

    public boolean canViewLeaderboard() {
        return tournament.getStatus() == TournamentStatus.ACTIVE
                || tournament.getStatus() == TournamentStatus.COMPLETED;
    }

    public String getCurrentRoundLabel() {
        if (resultsRound.isPresent()) {
            Round r = resultsRound.get();
            return "Round " + r.getRoundNumber() + " (" + r.getStatus() + ")";
        }
        if (pairableRound.isPresent()) {
            Round r = pairableRound.get();
            return "Round " + r.getRoundNumber() + " (" + r.getStatus() + ")";
        }
        if (round1.isEmpty()) {
            return tournament.getStatus() == TournamentStatus.DRAFT ? "Not started" : "—";
        }
        Round r = round1.get();
        return "Round " + r.getRoundNumber() + " (" + r.getStatus() + ")";
    }

    public String swissWarningIfAny() {
        if (tournament.getType() == TournamentType.SWISS && enrolledCount >= 2 && enrolledCount < 4) {
            return "Swiss works best with at least 4 players.";
        }
        return null;
    }
}
