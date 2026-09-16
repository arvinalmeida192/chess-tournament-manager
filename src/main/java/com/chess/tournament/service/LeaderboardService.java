package com.chess.tournament.service;

import com.chess.tournament.dao.GameDao;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.Player;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.GameResult;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.exception.NotFoundException;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.leaderboard.StandingComparator;
import com.chess.tournament.service.leaderboard.StandingEntry;
import com.chess.tournament.service.leaderboard.StandingRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds ranked standings with tie-breaks (SDD §7.7 / §10, SRS FR-LDB-*).
 */
public final class LeaderboardService {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final TournamentDao tournamentDao;
    private final TournamentPlayerDao tournamentPlayerDao;
    private final RoundDao roundDao;
    private final GameDao gameDao;
    private final PlayerDao playerDao;
    private final StandingComparator standingComparator;

    public LeaderboardService(TournamentDao tournamentDao,
                              TournamentPlayerDao tournamentPlayerDao,
                              RoundDao roundDao,
                              GameDao gameDao,
                              PlayerDao playerDao) {
        this(tournamentDao, tournamentPlayerDao, roundDao, gameDao, playerDao, new StandingComparator());
    }

    public LeaderboardService(TournamentDao tournamentDao,
                              TournamentPlayerDao tournamentPlayerDao,
                              RoundDao roundDao,
                              GameDao gameDao,
                              PlayerDao playerDao,
                              StandingComparator standingComparator) {
        this.tournamentDao = Objects.requireNonNull(tournamentDao);
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.roundDao = Objects.requireNonNull(roundDao);
        this.gameDao = Objects.requireNonNull(gameDao);
        this.playerDao = Objects.requireNonNull(playerDao);
        this.standingComparator = Objects.requireNonNull(standingComparator);
    }

    /**
     * @param upToRound completed round number to include, or {@code null} for latest completed
     */
    public List<StandingRow> getLeaderboard(long tournamentId, Integer upToRound) {
        Tournament tournament = tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));

        List<Round> completedRounds = roundDao.findByTournament(tournamentId).stream()
                .filter(r -> r.getStatus() == RoundStatus.COMPLETED)
                .sorted((a, b) -> Integer.compare(a.getRoundNumber(), b.getRoundNumber()))
                .toList();

        if (completedRounds.isEmpty()) {
            return standingsFromEnrollment(tournamentId, List.of());
        }

        int latest = completedRounds.get(completedRounds.size() - 1).getRoundNumber();
        int cutoff = upToRound == null ? latest : upToRound;
        if (cutoff < 1 || cutoff > latest) {
            throw new ValidationException(
                    "upToRound must be between 1 and latest completed round (" + latest + ")");
        }

        Set<Long> includedRoundIds = new HashSet<>();
        for (Round round : completedRounds) {
            if (round.getRoundNumber() <= cutoff) {
                includedRoundIds.add(round.getId());
            }
        }

        List<Game> allGames = gameDao.findByTournament(tournamentId);
        List<Game> includedGames = allGames.stream()
                .filter(g -> includedRoundIds.contains(g.getRoundId()))
                .toList();

        List<TournamentPlayer> players = tournamentPlayerDao.findByTournament(tournamentId);
        boolean trustStoredStats = cutoff == latest;

        List<StandingEntry> entries = new ArrayList<>();
        if (trustStoredStats) {
            for (TournamentPlayer tp : players) {
                entries.add(toEntry(tp, playerName(tp.getPlayerId()),
                        tp.getPoints(), tp.getWins(), tp.getDraws(), tp.getLosses()));
            }
        } else {
            Map<Long, Aggregates> aggregates = recomputeAggregates(players, includedGames);
            for (TournamentPlayer tp : players) {
                Aggregates agg = aggregates.getOrDefault(tp.getId(), Aggregates.zero());
                entries.add(toEntry(tp, playerName(tp.getPlayerId()),
                        agg.points, agg.wins, agg.draws, agg.losses));
            }
        }

        List<StandingEntry> sorted = standingComparator.sort(entries, includedGames);
        List<StandingRow> rows = new ArrayList<>(sorted.size());
        int rank = 1;
        for (StandingEntry entry : sorted) {
            TournamentPlayer tp = players.stream()
                    .filter(p -> Objects.equals(p.getId(), entry.getTournamentPlayerId()))
                    .findFirst()
                    .orElseThrow();
            rows.add(new StandingRow(
                    rank++,
                    entry.getTournamentPlayerId(),
                    entry.getPlayerName(),
                    entry.getRating(),
                    entry.getPoints(),
                    entry.getWins(),
                    entry.getDraws(),
                    entry.getLosses(),
                    entry.getStartRating(),
                    tp.getQualificationStatus()));
        }
        return rows;
    }

    public List<Integer> listCompletedRoundNumbers(long tournamentId) {
        requireTournament(tournamentId);
        return roundDao.findByTournament(tournamentId).stream()
                .filter(r -> r.getStatus() == RoundStatus.COMPLETED)
                .map(Round::getRoundNumber)
                .sorted()
                .toList();
    }

    /**
     * Latest completed round number, or empty if none.
     */
    public java.util.Optional<Integer> latestCompletedRound(long tournamentId) {
        List<Integer> rounds = listCompletedRoundNumbers(tournamentId);
        if (rounds.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(rounds.get(rounds.size() - 1));
    }

    private List<StandingRow> standingsFromEnrollment(long tournamentId, List<Game> games) {
        List<TournamentPlayer> players = tournamentPlayerDao.findByTournament(tournamentId);
        List<StandingEntry> entries = new ArrayList<>();
        for (TournamentPlayer tp : players) {
            entries.add(toEntry(tp, playerName(tp.getPlayerId()),
                    BigDecimal.ZERO, 0, 0, 0));
        }
        List<StandingEntry> sorted = standingComparator.sort(entries, games);
        List<StandingRow> rows = new ArrayList<>(sorted.size());
        int rank = 1;
        for (StandingEntry entry : sorted) {
            TournamentPlayer tp = players.stream()
                    .filter(p -> Objects.equals(p.getId(), entry.getTournamentPlayerId()))
                    .findFirst()
                    .orElseThrow();
            rows.add(new StandingRow(
                    rank++,
                    entry.getTournamentPlayerId(),
                    entry.getPlayerName(),
                    entry.getRating(),
                    entry.getPoints(),
                    entry.getWins(),
                    entry.getDraws(),
                    entry.getLosses(),
                    entry.getStartRating(),
                    tp.getQualificationStatus()));
        }
        return rows;
    }

    private StandingEntry toEntry(TournamentPlayer tp, String name,
                                  BigDecimal points, int wins, int draws, int losses) {
        return new StandingEntry(
                tp.getId(),
                name,
                tp.getCurrentRating(),
                points,
                wins,
                draws,
                losses,
                tp.getStartRating());
    }

    private String playerName(long playerId) {
        return playerDao.findById(playerId)
                .map(Player::getName)
                .orElse("Player #" + playerId);
    }

    private Tournament requireTournament(long tournamentId) {
        return tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));
    }

    private static Map<Long, Aggregates> recomputeAggregates(List<TournamentPlayer> players,
                                                             List<Game> games) {
        Map<Long, Aggregates> map = new HashMap<>();
        for (TournamentPlayer tp : players) {
            map.put(tp.getId(), Aggregates.zero());
        }
        for (Game game : games) {
            if (game.getResult() == null || game.getResult() == GameResult.PENDING) {
                continue;
            }
            if (game.getResult() == GameResult.BYE) {
                Long whiteId = game.getWhiteTournamentPlayerId();
                if (whiteId != null && map.containsKey(whiteId)) {
                    map.put(whiteId, map.get(whiteId).plusWin(BigDecimal.ONE));
                }
                continue;
            }
            Long whiteId = game.getWhiteTournamentPlayerId();
            Long blackId = game.getBlackTournamentPlayerId();
            BigDecimal whiteScore = scoreForWhite(game);
            BigDecimal blackScore = BigDecimal.ONE.subtract(whiteScore);
            if (whiteId != null && map.containsKey(whiteId)) {
                map.put(whiteId, map.get(whiteId).plusScore(whiteScore));
            }
            if (blackId != null && map.containsKey(blackId)) {
                map.put(blackId, map.get(blackId).plusScore(blackScore));
            }
        }
        return map;
    }

    private static BigDecimal scoreForWhite(Game game) {
        if (game.getWhiteScore() != null) {
            return game.getWhiteScore();
        }
        return switch (game.getResult()) {
            case WHITE_WIN -> BigDecimal.ONE;
            case BLACK_WIN -> BigDecimal.ZERO;
            case DRAW -> HALF;
            default -> BigDecimal.ZERO;
        };
    }

    private static final class Aggregates {
        private final BigDecimal points;
        private final int wins;
        private final int draws;
        private final int losses;

        private Aggregates(BigDecimal points, int wins, int draws, int losses) {
            this.points = points;
            this.wins = wins;
            this.draws = draws;
            this.losses = losses;
        }

        static Aggregates zero() {
            return new Aggregates(BigDecimal.ZERO, 0, 0, 0);
        }

        Aggregates plusWin(BigDecimal score) {
            return new Aggregates(points.add(score), wins + 1, draws, losses);
        }

        Aggregates plusScore(BigDecimal score) {
            int cmp = score.compareTo(BigDecimal.ONE);
            if (cmp == 0) {
                return new Aggregates(points.add(score), wins + 1, draws, losses);
            }
            if (score.compareTo(HALF) == 0) {
                return new Aggregates(points.add(score), wins, draws + 1, losses);
            }
            return new Aggregates(points.add(score), wins, draws, losses + 1);
        }
    }
}
