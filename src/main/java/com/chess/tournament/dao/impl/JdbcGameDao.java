package com.chess.tournament.dao.impl;

import com.chess.tournament.dao.GameDao;
import com.chess.tournament.dao.JdbcSupport;
import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.LongPair;
import com.chess.tournament.domain.enums.GameResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.chess.tournament.dao.JdbcSupport.wrap;

public final class JdbcGameDao implements GameDao {

    private static final String INSERT = """
            INSERT INTO game (round_id, board_number, white_tournament_player_id, black_tournament_player_id,
                              result, white_score, black_score, rematch, white_rating_delta, black_rating_delta)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, round_id, board_number, white_tournament_player_id, black_tournament_player_id,
                   result, white_score, black_score, rematch, white_rating_delta, black_rating_delta
            FROM game WHERE id = ?
            """;

    private static final String SELECT_BY_ROUND = """
            SELECT id, round_id, board_number, white_tournament_player_id, black_tournament_player_id,
                   result, white_score, black_score, rematch, white_rating_delta, black_rating_delta
            FROM game WHERE round_id = ? ORDER BY board_number
            """;

    private static final String UPDATE_RESULT = """
            UPDATE game
            SET result = ?, white_score = ?, black_score = ?, rematch = ?,
                white_rating_delta = ?, black_rating_delta = ?
            WHERE id = ?
            """;

    private static final String SELECT_PREVIOUS_PAIRINGS = """
            SELECT g.white_tournament_player_id, g.black_tournament_player_id
            FROM game g
            JOIN round r ON g.round_id = r.id
            WHERE r.tournament_id = ?
              AND g.white_tournament_player_id IS NOT NULL
              AND g.black_tournament_player_id IS NOT NULL
            """;

    private static final String SELECT_BY_TOURNAMENT = """
            SELECT g.id, g.round_id, g.board_number, g.white_tournament_player_id, g.black_tournament_player_id,
                   g.result, g.white_score, g.black_score, g.rematch, g.white_rating_delta, g.black_rating_delta
            FROM game g
            JOIN round r ON g.round_id = r.id
            WHERE r.tournament_id = ?
            ORDER BY r.round_number, g.board_number
            """;

    private static final String EXISTS_FOR_TOURNAMENT = """
            SELECT 1
            FROM game g
            JOIN round r ON g.round_id = r.id
            WHERE r.tournament_id = ?
            LIMIT 1
            """;

    private final DataSource dataSource;

    public JdbcGameDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insertBatch(List<Game> games) {
        JdbcSupport.withConnectionVoid(conn -> insertBatch(conn, games), dataSource, "Failed to insert games");
    }

    @Override
    public void insertBatch(Connection connection, List<Game> games) {
        if (games == null || games.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            for (Game game : games) {
                bindInsert(ps, game);
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int index = 0;
                while (keys.next() && index < games.size()) {
                    games.get(index).setId(keys.getLong(1));
                    index++;
                }
            }
        } catch (SQLException e) {
            throw wrap("Failed to insert games", e);
        }
    }

    @Override
    public List<Game> findByRound(long roundId) {
        return JdbcSupport.withConnection(conn -> findByRound(conn, roundId), dataSource, "Failed to find games");
    }

    @Override
    public List<Game> findByRound(Connection connection, long roundId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ROUND)) {
            ps.setLong(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Game> games = new ArrayList<>();
                while (rs.next()) {
                    games.add(mapRow(rs));
                }
                return games;
            }
        } catch (SQLException e) {
            throw wrap("Failed to find games by round", e);
        }
    }

    @Override
    public Optional<Game> findById(long gameId) {
        return JdbcSupport.withConnection(conn -> findById(conn, gameId), dataSource, "Failed to find game");
    }

    @Override
    public Optional<Game> findById(Connection connection, long gameId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw wrap("Failed to find game by id", e);
        }
    }

    @Override
    public void updateResult(Game game) {
        JdbcSupport.withConnectionVoid(conn -> updateResult(conn, game), dataSource, "Failed to update game result");
    }

    @Override
    public void updateResult(Connection connection, Game game) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_RESULT)) {
            ps.setString(1, game.getResult().name());
            ps.setBigDecimal(2, game.getWhiteScore());
            ps.setBigDecimal(3, game.getBlackScore());
            ps.setBoolean(4, game.isRematch());
            if (game.getWhiteRatingDelta() != null) {
                ps.setInt(5, game.getWhiteRatingDelta());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            if (game.getBlackRatingDelta() != null) {
                ps.setInt(6, game.getBlackRatingDelta());
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            ps.setLong(7, game.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap("Failed to update game result", e);
        }
    }

    @Override
    public Set<LongPair> findPreviousPairings(long tournamentId) {
        return JdbcSupport.withConnection(conn -> findPreviousPairings(conn, tournamentId), dataSource,
                "Failed to find previous pairings");
    }

    @Override
    public Set<LongPair> findPreviousPairings(Connection connection, long tournamentId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_PREVIOUS_PAIRINGS)) {
            ps.setLong(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                Set<LongPair> pairs = new HashSet<>();
                while (rs.next()) {
                    long white = rs.getLong("white_tournament_player_id");
                    long black = rs.getLong("black_tournament_player_id");
                    pairs.add(LongPair.of(white, black));
                }
                return pairs;
            }
        } catch (SQLException e) {
            throw wrap("Failed to find previous pairings", e);
        }
    }

    @Override
    public List<Game> findByTournament(long tournamentId) {
        return JdbcSupport.withConnection(conn -> findByTournament(conn, tournamentId), dataSource,
                "Failed to find games for tournament");
    }

    @Override
    public List<Game> findByTournament(Connection connection, long tournamentId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_TOURNAMENT)) {
            ps.setLong(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Game> games = new ArrayList<>();
                while (rs.next()) {
                    games.add(mapRow(rs));
                }
                return games;
            }
        } catch (SQLException e) {
            throw wrap("Failed to find games for tournament", e);
        }
    }

    @Override
    public boolean existsForTournament(long tournamentId) {
        return JdbcSupport.withConnection(conn -> existsForTournament(conn, tournamentId), dataSource,
                "Failed to check games for tournament");
    }

    @Override
    public boolean existsForTournament(Connection connection, long tournamentId) {
        try (PreparedStatement ps = connection.prepareStatement(EXISTS_FOR_TOURNAMENT)) {
            ps.setLong(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw wrap("Failed to check games for tournament", e);
        }
    }

    private static void bindInsert(PreparedStatement ps, Game game) throws SQLException {
        ps.setLong(1, game.getRoundId());
        ps.setInt(2, game.getBoardNumber());
        if (game.getWhiteTournamentPlayerId() != null) {
            ps.setLong(3, game.getWhiteTournamentPlayerId());
        } else {
            ps.setNull(3, java.sql.Types.BIGINT);
        }
        if (game.getBlackTournamentPlayerId() != null) {
            ps.setLong(4, game.getBlackTournamentPlayerId());
        } else {
            ps.setNull(4, java.sql.Types.BIGINT);
        }
        ps.setString(5, game.getResult() == null ? GameResult.PENDING.name() : game.getResult().name());
        ps.setBigDecimal(6, game.getWhiteScore());
        ps.setBigDecimal(7, game.getBlackScore());
        ps.setBoolean(8, game.isRematch());
        if (game.getWhiteRatingDelta() != null) {
            ps.setInt(9, game.getWhiteRatingDelta());
        } else {
            ps.setNull(9, java.sql.Types.INTEGER);
        }
        if (game.getBlackRatingDelta() != null) {
            ps.setInt(10, game.getBlackRatingDelta());
        } else {
            ps.setNull(10, java.sql.Types.INTEGER);
        }
    }

    private static Game mapRow(ResultSet rs) throws SQLException {
        Game game = new Game();
        game.setId(rs.getLong("id"));
        game.setRoundId(rs.getLong("round_id"));
        game.setBoardNumber(rs.getInt("board_number"));
        game.setWhiteTournamentPlayerId(JdbcSupport.getNullableLong(rs, "white_tournament_player_id"));
        game.setBlackTournamentPlayerId(JdbcSupport.getNullableLong(rs, "black_tournament_player_id"));
        game.setResult(GameResult.fromDbValue(rs.getString("result")));
        game.setWhiteScore(rs.getBigDecimal("white_score"));
        game.setBlackScore(rs.getBigDecimal("black_score"));
        game.setRematch(rs.getBoolean("rematch"));
        game.setWhiteRatingDelta(JdbcSupport.getNullableInt(rs, "white_rating_delta"));
        game.setBlackRatingDelta(JdbcSupport.getNullableInt(rs, "black_rating_delta"));
        return game;
    }
}
