package com.chess.tournament.service.pairing;

/**
 * Proposed board pairing before persistence.
 * For byes: {@code whiteTpId} is the player receiving the bye, {@code blackTpId} is null.
 */
public record PairingProposal(
        int boardNumber,
        Long whiteTpId,
        Long blackTpId,
        boolean bye,
        boolean rematch
) {
    public PairingProposal {
        if (boardNumber < 1) {
            throw new IllegalArgumentException("boardNumber must be >= 1");
        }
        if (bye) {
            if (whiteTpId == null) {
                throw new IllegalArgumentException("Bye proposal requires whiteTpId");
            }
            if (blackTpId != null) {
                throw new IllegalArgumentException("Bye proposal must have null blackTpId");
            }
            if (rematch) {
                throw new IllegalArgumentException("Bye proposal cannot be a rematch");
            }
        } else if (whiteTpId == null || blackTpId == null) {
            throw new IllegalArgumentException("Non-bye proposal requires both players");
        }
    }

    public static PairingProposal game(int boardNumber, long whiteTpId, long blackTpId) {
        return new PairingProposal(boardNumber, whiteTpId, blackTpId, false, false);
    }

    public static PairingProposal game(int boardNumber, long whiteTpId, long blackTpId, boolean rematch) {
        return new PairingProposal(boardNumber, whiteTpId, blackTpId, false, rematch);
    }

    public static PairingProposal bye(int boardNumber, long tournamentPlayerId) {
        return new PairingProposal(boardNumber, tournamentPlayerId, null, true, false);
    }
}
