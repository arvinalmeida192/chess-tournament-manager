package com.chess.tournament.service.pairing;

import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.ValidationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Round Robin pairings via {@link RoundRobinScheduleGenerator} (SDD §8.2).
 * Colors alternate by round: odd rounds left-half white; even rounds swap.
 */
public final class RoundRobinPairingStrategy implements PairingStrategy {

    @Override
    public boolean supports(TournamentType type) {
        return type == TournamentType.ROUND_ROBIN;
    }

    @Override
    public List<PairingProposal> pair(PairingContext ctx) {
        if (!supports(ctx.tournament().getType())) {
            throw new ValidationException("RoundRobinPairingStrategy only supports ROUND_ROBIN");
        }

        List<Long> orderedIds = ctx.players().stream()
                .sorted(Comparator.comparing(TournamentPlayer::getId))
                .map(TournamentPlayer::getId)
                .toList();

        int expectedRounds = RoundRobinScheduleGenerator.expectedRoundCount(orderedIds.size());
        if (ctx.roundNumber() > expectedRounds) {
            throw new ValidationException(
                    "Round " + ctx.roundNumber() + " exceeds Round Robin schedule length "
                            + expectedRounds);
        }

        List<RoundRobinScheduleGenerator.ScheduledPair> pairs =
                RoundRobinScheduleGenerator.round(orderedIds, ctx.roundNumber());

        boolean leftIsWhite = (ctx.roundNumber() % 2) == 1;
        List<PairingProposal> proposals = new ArrayList<>();
        int board = 1;

        for (RoundRobinScheduleGenerator.ScheduledPair pair : pairs) {
            if (pair.isBye()) {
                Long byeId = pair.byePlayerId();
                if (byeId == null) {
                    continue; // phantom vs phantom should not occur
                }
                proposals.add(PairingProposal.bye(board++, byeId));
            } else {
                long left = pair.a();
                long right = pair.b();
                if (leftIsWhite) {
                    proposals.add(PairingProposal.game(board++, left, right));
                } else {
                    proposals.add(PairingProposal.game(board++, right, left));
                }
            }
        }
        return List.copyOf(proposals);
    }
}
