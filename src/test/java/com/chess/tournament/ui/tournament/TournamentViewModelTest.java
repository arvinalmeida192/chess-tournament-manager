package com.chess.tournament.ui.tournament;

import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentViewModelTest {

    @Test
    void draftWithEnoughPlayers_canStartAndEnroll_cannotFinalize() {
        Tournament t = swiss(TournamentStatus.DRAFT, 3);
        TournamentViewModel vm = new TournamentViewModel(t, 4, Optional.empty(), false);
        assertTrue(vm.canStart());
        assertTrue(vm.canEnroll());
        assertTrue(vm.canOpenEnrollment());
        assertTrue(vm.canCancel());
        assertFalse(vm.canFinalize());
        assertFalse(vm.canViewLeaderboard());
        assertFalse(vm.canOpenPairings());
    }

    @Test
    void activeWithPairableRound_canOpenPairings() {
        Tournament t = swiss(TournamentStatus.ACTIVE, 3);
        Round r1 = round(1, RoundStatus.PENDING_PAIRINGS);
        TournamentViewModel vm = new TournamentViewModel(
                t, 8, Optional.of(r1), Optional.of(r1), Optional.empty(), false, false, false);
        assertTrue(vm.canOpenPairings());
        assertTrue(vm.canGeneratePairings());
        assertFalse(vm.canEnterResults());
        assertFalse(vm.canFinalize());
        assertTrue(vm.canViewLeaderboard());
    }

    @Test
    void activeAllRoundsComplete_canFinalize() {
        Tournament t = swiss(TournamentStatus.ACTIVE, 3);
        Round r1 = round(1, RoundStatus.COMPLETED);
        TournamentViewModel vm = new TournamentViewModel(
                t, 8, Optional.of(r1), Optional.empty(), Optional.empty(), true, true, true);
        assertTrue(vm.canFinalize());
        assertTrue(vm.canApplyQualification());
        assertTrue(vm.canCancel());
        assertFalse(vm.canStart());
        assertFalse(vm.canEnroll());
    }

    @Test
    void completed_cannotCancelOrFinalize_canViewLeaderboard() {
        Tournament t = swiss(TournamentStatus.COMPLETED, 3);
        TournamentViewModel vm = new TournamentViewModel(
                t, 8, Optional.of(round(1, RoundStatus.COMPLETED)),
                Optional.empty(), Optional.empty(), true, true, true);
        assertFalse(vm.canCancel());
        assertFalse(vm.canFinalize());
        assertTrue(vm.canViewLeaderboard());
        assertFalse(vm.canOpenEnrollment());
    }

    private static Tournament swiss(TournamentStatus status, int rounds) {
        Tournament t = new Tournament();
        t.setId(1L);
        t.setName("Test");
        t.setType(TournamentType.SWISS);
        t.setRoundsPlanned(rounds);
        t.setQualifiersCount(4);
        t.setSwissFirstRoundMethod(SwissFirstRoundMethod.RANDOM);
        t.setStatus(status);
        return t;
    }

    private static Round round(int number, RoundStatus status) {
        Round r = new Round();
        r.setId((long) number);
        r.setTournamentId(1L);
        r.setRoundNumber(number);
        r.setStatus(status);
        return r;
    }
}
