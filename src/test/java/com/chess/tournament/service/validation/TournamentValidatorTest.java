package com.chess.tournament.service.validation;

import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentValidatorTest {

    @Test
    void validateCreate_rejectsBlankName() {
        CreateTournamentCommand cmd = new CreateTournamentCommand(
                "  ", TournamentType.SWISS, 5, 0, SwissFirstRoundMethod.RANDOM);
        assertThrows(ValidationException.class, () -> TournamentValidator.validateCreate(cmd));
    }

    @Test
    void validateCreate_requiresSwissMethod() {
        CreateTournamentCommand cmd = new CreateTournamentCommand(
                "Open", TournamentType.SWISS, 5, 0, null);
        assertThrows(ValidationException.class, () -> TournamentValidator.validateCreate(cmd));
    }

    @Test
    void validateCreate_acceptsValidSwiss() {
        CreateTournamentCommand cmd = new CreateTournamentCommand(
                "Open", TournamentType.SWISS, 5, 2, SwissFirstRoundMethod.RATING_SPLIT);
        assertDoesNotThrow(() -> TournamentValidator.validateCreate(cmd));
    }

    @Test
    void roundRobin_expectedRounds_evenAndOdd() {
        assertEquals(3, RoundRobinValidator.expectedRounds(4));
        assertEquals(5, RoundRobinValidator.expectedRounds(5));
    }

    @Test
    void roundRobin_wrongRoundCountFails() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> RoundRobinValidator.validateRounds(2, 4));
        assertTrue(ex.getMessage().contains("3"));
    }

    @Test
    void roundRobin_minPlayers() {
        assertThrows(ValidationException.class, () -> RoundRobinValidator.validateMinimumPlayers(2));
        assertDoesNotThrow(() -> RoundRobinValidator.validateMinimumPlayers(3));
    }

    @Test
    void knockout_expectedRounds() {
        assertEquals(2, KnockoutValidator.expectedRounds(4));
        assertEquals(3, KnockoutValidator.expectedRounds(5));
        assertEquals(3, KnockoutValidator.expectedRounds(8));
    }

    @Test
    void knockout_wrongRoundCountFails() {
        assertThrows(ValidationException.class, () -> KnockoutValidator.validateRounds(2, 5));
    }

    @Test
    void swiss_rejectsZeroRounds() {
        assertThrows(ValidationException.class, () -> SwissValidator.validateRounds(0));
    }

    @Test
    void validateStart_delegatesToFormatValidators() {
        assertDoesNotThrow(() -> TournamentValidator.validateStart(TournamentType.ROUND_ROBIN, 3, 4));
        assertThrows(ValidationException.class,
                () -> TournamentValidator.validateStart(TournamentType.ROUND_ROBIN, 3, 2));
        assertDoesNotThrow(() -> TournamentValidator.validateStart(TournamentType.KNOCKOUT, 3, 5));
        assertDoesNotThrow(() -> TournamentValidator.validateStart(TournamentType.SWISS, 7, 2));
    }
}
