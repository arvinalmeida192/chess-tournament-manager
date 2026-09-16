package com.chess.tournament.service.pairing;

/**
 * Proposed board pairing before persistence.
 * For byes: {@code whiteTpId} is the player receiving the bye, {@code blackTpId} is null.
 */
public record PairingProposal(
        int boardNumber,
        Long whiteTpId,
        Long blackTpId,
        boolean bye
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
        } else if (whiteTpId == null || blackTpId == null) {
            throw new IllegalArgumentException("Non-bye proposal requires both players");
        }
    }

    public static PairingProposal game(int boardNumber, long whiteTpId, long blackTpId) {
        return new PairingProposal(boardNumber, whiteTpId, blackTpId, false);
    }

    public static PairingProposal bye(int boardNumber, long tournamentPlayerId) {
        return new PairingProposal(boardNumber, tournamentPlayerId, null, true);
    }
}
