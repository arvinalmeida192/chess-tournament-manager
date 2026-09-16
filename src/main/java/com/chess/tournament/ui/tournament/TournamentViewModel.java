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
    private final boolean enrollmentLocked;

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, boolean enrollmentLocked) {
        this.tournament = tournament;
        this.enrolledCount = enrolledCount;
        this.round1 = round1;
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
        // Enabled when ACTIVE and round 1 is PENDING_PAIRINGS; generation arrives in Phase 5+
        return tournament.getStatus() == TournamentStatus.ACTIVE
                && round1.isPresent()
                && round1.get().getStatus() == RoundStatus.PENDING_PAIRINGS;
    }

    public boolean canEnterResults() {
        return false; // Phase 8
    }

    public boolean canCompleteRound() {
        return false; // Phase 8
    }

    public boolean canFinalize() {
        return false; // Phase 9
    }

    public boolean canViewLeaderboard() {
        return tournament.getStatus() == TournamentStatus.ACTIVE
                || tournament.getStatus() == TournamentStatus.COMPLETED;
    }

    public String getCurrentRoundLabel() {
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
