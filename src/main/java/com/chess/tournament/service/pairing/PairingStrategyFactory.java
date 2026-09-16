package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a {@link PairingStrategy} by tournament type.
 * Phase 5 registers Round Robin only.
 */
public final class PairingStrategyFactory {

    private final List<PairingStrategy> strategies;

    public PairingStrategyFactory() {
        this(List.of(new RoundRobinPairingStrategy()));
    }

    public PairingStrategyFactory(List<PairingStrategy> strategies) {
        this.strategies = List.copyOf(new ArrayList<>(strategies));
    }

    public PairingStrategy forType(TournamentType type) {
        return strategies.stream()
                .filter(s -> s.supports(type))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "No pairing strategy registered for tournament type: " + type));
    }
}
