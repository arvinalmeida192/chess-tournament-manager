package com.chess.tournament.ui.tournament;

import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.ui.util.DisplayLabels;

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
    private final boolean allRoundsComplete;
    private final boolean hasCompletedRound;
    private final int highestCompletedRound;

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, boolean enrollmentLocked) {
        this(tournament, enrolledCount, round1, Optional.empty(), Optional.empty(),
                enrollmentLocked, false, false, 0);
    }

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, Optional<Round> pairableRound,
                               boolean enrollmentLocked) {
        this(tournament, enrolledCount, round1, pairableRound, Optional.empty(),
                enrollmentLocked, false, false, 0);
    }

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, Optional<Round> pairableRound,
                               Optional<Round> resultsRound, boolean enrollmentLocked) {
        this(tournament, enrolledCount, round1, pairableRound, resultsRound,
                enrollmentLocked, false, false, 0);
    }

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, Optional<Round> pairableRound,
                               Optional<Round> resultsRound, boolean enrollmentLocked,
                               boolean allRoundsComplete, boolean hasCompletedRound) {
        this(tournament, enrolledCount, round1, pairableRound, resultsRound,
                enrollmentLocked, allRoundsComplete, hasCompletedRound, 0);
    }

    public TournamentViewModel(Tournament tournament, int enrolledCount,
                               Optional<Round> round1, Optional<Round> pairableRound,
                               Optional<Round> resultsRound, boolean enrollmentLocked,
                               boolean allRoundsComplete, boolean hasCompletedRound,
                               int highestCompletedRound) {
        this.tournament = tournament;
        this.enrolledCount = enrolledCount;
        this.round1 = round1;
        this.pairableRound = pairableRound;
        this.resultsRound = resultsRound;
        this.enrollmentLocked = enrollmentLocked;
        this.allRoundsComplete = allRoundsComplete;
        this.hasCompletedRound = hasCompletedRound;
        this.highestCompletedRound = highestCompletedRound;
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

    /**
     * Host can proceed to the next round when a pending-pairings round exists
     * (typically after completing the previous round).
     */
    public boolean canProceedToNextRound() {
        return canGeneratePairings() && pairableRound.isPresent()
                && pairableRound.get().getRoundNumber() > 1;
    }

    public boolean canEnterResults() {
        return tournament.getStatus() == TournamentStatus.ACTIVE && resultsRound.isPresent();
    }

    public boolean canCompleteRound() {
        return canEnterResults();
    }

    public boolean canFinalize() {
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            return false;
        }
        return allRoundsComplete;
    }

    public boolean canViewLeaderboard() {
        return tournament.getStatus() == TournamentStatus.ACTIVE
                || tournament.getStatus() == TournamentStatus.COMPLETED;
    }

    public boolean canApplyQualification() {
        return (tournament.getStatus() == TournamentStatus.ACTIVE
                || tournament.getStatus() == TournamentStatus.COMPLETED)
                && hasCompletedRound;
    }

    /** DRAFT or ACTIVE may be cancelled (FR-TNM-007). */
    public boolean canCancel() {
        return tournament.getStatus() == TournamentStatus.DRAFT
                || tournament.getStatus() == TournamentStatus.ACTIVE;
    }

    /** Enrollment screen is openable while DRAFT/ACTIVE (view-only when locked). */
    public boolean canOpenEnrollment() {
        return tournament.getStatus() == TournamentStatus.DRAFT
                || tournament.getStatus() == TournamentStatus.ACTIVE;
    }

    public String getCurrentRoundLabel() {
        if (resultsRound.isPresent()) {
            Round r = resultsRound.get();
            return "Round " + r.getRoundNumber() + " of " + tournament.getRoundsPlanned()
                    + " — " + DisplayLabels.roundStatus(r.getStatus());
        }
        if (pairableRound.isPresent()) {
            Round r = pairableRound.get();
            return "Round " + r.getRoundNumber() + " of " + tournament.getRoundsPlanned()
                    + " — " + DisplayLabels.roundStatus(r.getStatus());
        }
        if (round1.isEmpty()) {
            return tournament.getStatus() == TournamentStatus.DRAFT ? "Not started" : "—";
        }
        if (allRoundsComplete) {
            return "All " + tournament.getRoundsPlanned() + " rounds completed";
        }
        if (highestCompletedRound > 0) {
            return "Round " + highestCompletedRound + " of " + tournament.getRoundsPlanned()
                    + " — Completed";
        }
        Round r = round1.get();
        return "Round " + r.getRoundNumber() + " of " + tournament.getRoundsPlanned()
                + " — " + DisplayLabels.roundStatus(r.getStatus());
    }

    /** Short guidance for the host on what to do next. */
    public String getNextStepHint() {
        if (tournament.getStatus() == TournamentStatus.COMPLETED) {
            return "Tournament finished — view the leaderboard for final standings.";
        }
        if (tournament.getStatus() == TournamentStatus.CANCELLED) {
            return "This tournament was cancelled.";
        }
        if (tournament.getStatus() == TournamentStatus.DRAFT) {
            if (enrolledCount == 0) {
                return "Next: enroll players, then start the tournament.";
            }
            if (canStart()) {
                return "Next: start the tournament to create round 1.";
            }
            return "Next: enroll enough players, then start.";
        }
        if (canFinalize()) {
            return "Next: finalize the tournament (applies qualification and locks results).";
        }
        if (canGeneratePairings()) {
            int n = pairableRound.map(Round::getRoundNumber).orElse(1);
            return "Next: generate pairings for round " + n + ".";
        }
        if (canEnterResults()) {
            int n = resultsRound.map(Round::getRoundNumber).orElse(1);
            return "Next: enter results and complete round " + n + ".";
        }
        if (canViewLeaderboard() && hasCompletedRound) {
            return "Next: review the leaderboard, or wait for the current round to finish.";
        }
        return "Tournament is active.";
    }

    public String swissWarningIfAny() {
        if (tournament.getType() == TournamentType.SWISS && enrolledCount >= 2 && enrolledCount < 4) {
            return "Swiss works best with at least 4 players.";
        }
        return null;
    }
}
