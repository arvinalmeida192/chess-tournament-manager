package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.enums.TournamentType;

import java.util.List;

public interface PairingStrategy {

    boolean supports(TournamentType type);

    List<PairingProposal> pair(PairingContext ctx);
}
