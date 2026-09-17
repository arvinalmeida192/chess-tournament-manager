package com.chess.tournament.ui.util;

import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;

/**
 * Host-facing labels for domain enums (avoids raw enum names in the UI).
 */
public final class DisplayLabels {

    private DisplayLabels() {
    }

    public static String tournamentType(TournamentType type) {
        if (type == null) {
            return "—";
        }
        return switch (type) {
            case ROUND_ROBIN -> "Round Robin";
            case KNOCKOUT -> "Knockout";
            case SWISS -> "Swiss";
        };
    }

    public static String tournamentStatus(TournamentStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case DRAFT -> "Draft";
            case ACTIVE -> "Active";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    public static String roundStatus(RoundStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case PENDING_PAIRINGS -> "Awaiting pairings";
            case PAIRINGS_PUBLISHED -> "Pairings published";
            case IN_PROGRESS -> "In progress";
            case COMPLETED -> "Completed";
        };
    }

    public static String gameResult(GameResult result) {
        if (result == null) {
            return "";
        }
        return switch (result) {
            case PENDING -> "Pending";
            case WHITE_WIN -> "White wins";
            case BLACK_WIN -> "Black wins";
            case DRAW -> "Draw";
            case BYE -> "Bye";
        };
    }

    public static String qualification(QualificationStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case PENDING -> "Pending";
            case QUALIFIED -> "Qualified";
            case ELIMINATED -> "Eliminated";
            case NOT_APPLICABLE -> "N/A";
        };
    }

    public static String swissMethod(SwissFirstRoundMethod method) {
        if (method == null) {
            return "—";
        }
        return switch (method) {
            case RANDOM -> "Random";
            case RATING_SPLIT -> "Rating split";
        };
    }
}
