package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Knockout single-elimination pairings (SDD §8.3).
 * <p>
 * Round 1 uses {@link KnockoutSeeding}. Later rounds pair winners of adjacent
 * boards from the previous round (board 1 winner vs board 2 winner, etc.).
 */
public final class KnockoutPairingStrategy implements PairingStrategy {

    @Override
    public boolean supports(TournamentType type) {
        return type == TournamentType.KNOCKOUT;
    }

    @Override
    public List<PairingProposal> pair(PairingContext ctx) {
        if (!supports(ctx.tournament().getType())) {
            throw new ValidationException("KnockoutPairingStrategy only supports KNOCKOUT");
        }
        if (ctx.roundNumber() == 1) {
            return pairRound1(ctx);
        }
        return pairFromWinners(ctx);
    }

    private static List<PairingProposal> pairRound1(PairingContext ctx) {
        List<KnockoutSeeding.Slot> slots = KnockoutSeeding.buildRound1Slots(ctx.players());
        List<PairingProposal> proposals = new ArrayList<>(slots.size());
        int board = 1;
        for (KnockoutSeeding.Slot slot : slots) {
            if (slot.bye()) {
                proposals.add(PairingProposal.bye(board++, slot.white().getId()));
            } else {
                proposals.add(PairingProposal.game(board++, slot.white().getId(), slot.black().getId()));
            }
        }
        return List.copyOf(proposals);
    }

    private static List<PairingProposal> pairFromWinners(PairingContext ctx) {
        List<Game> previous = ctx.previousRoundGames();
        if (previous.isEmpty()) {
            throw new ValidationException(
                    "Cannot pair knockout round " + ctx.roundNumber()
                            + " without previous round games");
        }

        List<Game> ordered = previous.stream()
                .sorted(Comparator.comparingInt(Game::getBoardNumber))
                .toList();

        Map<Long, TournamentPlayer> byId = new HashMap<>();
        for (TournamentPlayer tp : ctx.players()) {
            byId.put(tp.getId(), tp);
        }

        List<TournamentPlayer> winners = new ArrayList<>();
        for (Game game : ordered) {
            Long winnerId = winnerTpId(game);
            TournamentPlayer winner = byId.get(winnerId);
            if (winner == null) {
                throw new ValidationException("Winner TP not found: " + winnerId);
            }
            if (winner.getQualificationStatus() == QualificationStatus.ELIMINATED) {
                throw new ValidationException(
                        "Eliminated player cannot advance: TP " + winnerId);
            }
            winners.add(winner);
        }

        if (winners.size() < 2) {
            throw new ValidationException("Need at least 2 winners to pair next knockout round");
        }
        if (winners.size() % 2 != 0) {
            throw new ValidationException(
                    "Odd number of winners (" + winners.size()
                            + ") — bracket corruption or missing results");
        }

        List<PairingProposal> proposals = new ArrayList<>(winners.size() / 2);
        int board = 1;
        for (int i = 0; i < winners.size(); i += 2) {
            TournamentPlayer a = winners.get(i);
            TournamentPlayer b = winners.get(i + 1);
            if (KnockoutSeeding.compareRatingDesc(a, b) <= 0) {
                proposals.add(PairingProposal.game(board++, a.getId(), b.getId()));
            } else {
                proposals.add(PairingProposal.game(board++, b.getId(), a.getId()));
            }
        }
        return List.copyOf(proposals);
    }

    static Long winnerTpId(Game game) {
        GameResult result = game.getResult();
        if (result == null || result == GameResult.PENDING) {
            throw new ValidationException(
                    "Previous round game board " + game.getBoardNumber() + " has no result");
        }
        return switch (result) {
            case WHITE_WIN, BYE -> {
                if (game.getWhiteTournamentPlayerId() == null) {
                    throw new ValidationException("White winner missing on board " + game.getBoardNumber());
                }
                yield game.getWhiteTournamentPlayerId();
            }
            case BLACK_WIN -> {
                if (game.getBlackTournamentPlayerId() == null) {
                    throw new ValidationException("Black winner missing on board " + game.getBoardNumber());
                }
                yield game.getBlackTournamentPlayerId();
            }
            case DRAW -> throw new ValidationException(
                    "Knockout games cannot be draws (board " + game.getBoardNumber() + ")");
            case PENDING -> throw new ValidationException("Unreachable");
        };
    }
}
