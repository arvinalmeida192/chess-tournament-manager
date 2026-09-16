package com.chess.tournament.dao.impl;

import com.chess.tournament.dao.JdbcSupport;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.QualificationStatus;
import com.chess.tournament.exception.DataAccessException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static com.chess.tournament.dao.JdbcSupport.wrap;

public final class JdbcTournamentPlayerDao implements TournamentPlayerDao {

    private static final String INSERT = """
            INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                           points, wins, draws, losses, games_played,
                                           qualification_status, color_balance)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_TOURNAMENT = """
            SELECT id, tournament_id, player_id, start_rating, current_rating, points, wins, draws,
                   losses, games_played, qualification_status, color_balance
            FROM tournament_player WHERE tournament_id = ? ORDER BY id
            """;

    private static final String UPDATE_STATS = """
            UPDATE tournament_player
            SET current_rating = ?, points = ?, wins = ?, draws = ?, losses = ?,
                games_played = ?, color_balance = ?
            WHERE id = ?
            """;

    private static final String UPDATE_QUALIFICATION = """
            UPDATE tournament_player SET qualification_status = ? WHERE id = ?
            """;

    private static final String DELETE = """
            DELETE FROM tournament_player WHERE tournament_id = ? AND player_id = ?
            """;

    private final DataSource dataSource;

    public JdbcTournamentPlayerDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long insert(TournamentPlayer tournamentPlayer) {
        return JdbcSupport.withConnection(conn -> insert(conn, tournamentPlayer), dataSource,
                "Failed to insert tournament player");
    }

    @Override
    public long insert(Connection connection, TournamentPlayer tournamentPlayer) {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, tournamentPlayer.getTournamentId());
            ps.setLong(2, tournamentPlayer.getPlayerId());
            ps.setInt(3, tournamentPlayer.getStartRating());
            ps.setInt(4, tournamentPlayer.getCurrentRating());
            ps.setBigDecimal(5, tournamentPlayer.getPoints());
            ps.setInt(6, tournamentPlayer.getWins());
            ps.setInt(7, tournamentPlayer.getDraws());
            ps.setInt(8, tournamentPlayer.getLosses());
            ps.setInt(9, tournamentPlayer.getGamesPlayed());
            ps.setString(10, tournamentPlayer.getQualificationStatus().name());
            ps.setInt(11, tournamentPlayer.getColorBalance());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DataAccessException("Insert tournament player did not return generated key");
        } catch (SQLException e) {
            throw wrap("Failed to insert tournament player", e);
        }
    }

    @Override
    public List<TournamentPlayer> findByTournament(long tournamentId) {
        return JdbcSupport.withConnection(conn -> findByTournament(conn, tournamentId), dataSource,
                "Failed to find tournament players");
    }

    @Override
    public List<TournamentPlayer> findByTournament(Connection connection, long tournamentId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_TOURNAMENT)) {
            ps.setLong(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TournamentPlayer> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw wrap("Failed to find tournament players", e);
        }
    }

    @Override
    public void updateStats(TournamentPlayer tournamentPlayer) {
        JdbcSupport.withConnectionVoid(conn -> updateStats(conn, tournamentPlayer), dataSource,
                "Failed to update tournament player stats");
    }

    @Override
    public void updateStats(Connection connection, TournamentPlayer tournamentPlayer) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATS)) {
            ps.setInt(1, tournamentPlayer.getCurrentRating());
            ps.setBigDecimal(2, tournamentPlayer.getPoints());
            ps.setInt(3, tournamentPlayer.getWins());
            ps.setInt(4, tournamentPlayer.getDraws());
            ps.setInt(5, tournamentPlayer.getLosses());
            ps.setInt(6, tournamentPlayer.getGamesPlayed());
            ps.setInt(7, tournamentPlayer.getColorBalance());
            ps.setLong(8, tournamentPlayer.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap("Failed to update tournament player stats", e);
        }
    }

    @Override
    public void updateQualification(long tournamentPlayerId, QualificationStatus status) {
        JdbcSupport.withConnectionVoid(conn -> updateQualification(conn, tournamentPlayerId, status),
                dataSource, "Failed to update qualification status");
    }

    @Override
    public void updateQualification(Connection connection, long tournamentPlayerId,
                                    QualificationStatus status) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_QUALIFICATION)) {
            ps.setString(1, status.name());
            ps.setLong(2, tournamentPlayerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap("Failed to update qualification status", e);
        }
    }

    @Override
    public void deleteByTournamentAndPlayer(long tournamentId, long playerId) {
        JdbcSupport.withConnectionVoid(conn -> deleteByTournamentAndPlayer(conn, tournamentId, playerId),
                dataSource, "Failed to delete tournament player");
    }

    @Override
    public void deleteByTournamentAndPlayer(Connection connection, long tournamentId, long playerId) {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setLong(1, tournamentId);
            ps.setLong(2, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap("Failed to delete tournament player", e);
        }
    }

    private static TournamentPlayer mapRow(ResultSet rs) throws SQLException {
        TournamentPlayer tp = new TournamentPlayer();
        tp.setId(rs.getLong("id"));
        tp.setTournamentId(rs.getLong("tournament_id"));
        tp.setPlayerId(rs.getLong("player_id"));
        tp.setStartRating(rs.getInt("start_rating"));
        tp.setCurrentRating(rs.getInt("current_rating"));
        tp.setPoints(rs.getBigDecimal("points"));
        tp.setWins(rs.getInt("wins"));
        tp.setDraws(rs.getInt("draws"));
        tp.setLosses(rs.getInt("losses"));
        tp.setGamesPlayed(rs.getInt("games_played"));
        tp.setQualificationStatus(QualificationStatus.fromDbValue(rs.getString("qualification_status")));
        tp.setColorBalance(rs.getInt("color_balance"));
        return tp;
    }
}
