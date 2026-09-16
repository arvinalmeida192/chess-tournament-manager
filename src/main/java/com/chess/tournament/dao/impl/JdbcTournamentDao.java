package com.chess.tournament.dao.impl;

import com.chess.tournament.dao.JdbcSupport;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import com.chess.tournament.exception.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.chess.tournament.dao.JdbcSupport.toInstant;
import static com.chess.tournament.dao.JdbcSupport.toTimestamp;
import static com.chess.tournament.dao.JdbcSupport.wrap;

public final class JdbcTournamentDao implements TournamentDao {

    private static final String INSERT = """
            INSERT INTO tournament (name, type, rounds_planned, qualifiers_count,
                                      swiss_first_round_method, status)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, name, type, rounds_planned, qualifiers_count, swiss_first_round_method,
                   status, created_at, started_at, completed_at
            FROM tournament WHERE id = ?
            """;

    private static final String SELECT_BY_STATUS = """
            SELECT id, name, type, rounds_planned, qualifiers_count, swiss_first_round_method,
                   status, created_at, started_at, completed_at
            FROM tournament WHERE status = ? ORDER BY created_at DESC
            """;

    private static final String SELECT_ALL = """
            SELECT id, name, type, rounds_planned, qualifiers_count, swiss_first_round_method,
                   status, created_at, started_at, completed_at
            FROM tournament ORDER BY created_at DESC
            """;

    private static final String UPDATE_STATUS = """
            UPDATE tournament SET status = ?, started_at = ?, completed_at = ? WHERE id = ?
            """;

    private final DataSource dataSource;

    public JdbcTournamentDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long insert(Tournament tournament) {
        return JdbcSupport.withConnection(conn -> insert(conn, tournament), dataSource, "Failed to insert tournament");
    }

    @Override
    public long insert(Connection connection, Tournament tournament) {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tournament.getName());
            ps.setString(2, tournament.getType().name());
            ps.setInt(3, tournament.getRoundsPlanned());
            ps.setInt(4, tournament.getQualifiersCount());
            ps.setString(5, tournament.getSwissFirstRoundMethod().name());
            ps.setString(6, tournament.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DataAccessException("Insert tournament did not return generated key");
        } catch (SQLException e) {
            throw wrap("Failed to insert tournament", e);
        }
    }

    @Override
    public Optional<Tournament> findById(long id) {
        return JdbcSupport.withConnection(conn -> findById(conn, id), dataSource, "Failed to find tournament");
    }

    @Override
    public Optional<Tournament> findById(Connection connection, long id) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw wrap("Failed to find tournament by id", e);
        }
    }

    @Override
    public List<Tournament> findByStatus(TournamentStatus status) {
        return JdbcSupport.withConnection(conn -> findByStatus(conn, status), dataSource,
                "Failed to find tournaments by status");
    }

    @Override
    public List<Tournament> findByStatus(Connection connection, TournamentStatus status) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_STATUS)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Tournament> tournaments = new ArrayList<>();
                while (rs.next()) {
                    tournaments.add(mapRow(rs));
                }
                return tournaments;
            }
        } catch (SQLException e) {
            throw wrap("Failed to find tournaments by status", e);
        }
    }

    @Override
    public List<Tournament> findAll() {
        return JdbcSupport.withConnection(this::findAll, dataSource, "Failed to list tournaments");
    }

    @Override
    public List<Tournament> findAll(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            List<Tournament> tournaments = new ArrayList<>();
            while (rs.next()) {
                tournaments.add(mapRow(rs));
            }
            return tournaments;
        } catch (SQLException e) {
            throw wrap("Failed to list tournaments", e);
        }
    }

    @Override
    public void updateStatus(long id, TournamentStatus status, Instant startedAt, Instant completedAt) {
        JdbcSupport.withConnectionVoid(conn -> updateStatus(conn, id, status, startedAt, completedAt),
                dataSource, "Failed to update tournament status");
    }

    @Override
    public void updateStatus(Connection connection, long id, TournamentStatus status,
                             Instant startedAt, Instant completedAt) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.name());
            ps.setTimestamp(2, toTimestamp(startedAt));
            ps.setTimestamp(3, toTimestamp(completedAt));
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap("Failed to update tournament status", e);
        }
    }

    private static Tournament mapRow(ResultSet rs) throws SQLException {
        Tournament tournament = new Tournament();
        tournament.setId(rs.getLong("id"));
        tournament.setName(rs.getString("name"));
        tournament.setType(TournamentType.fromDbValue(rs.getString("type")));
        tournament.setRoundsPlanned(rs.getInt("rounds_planned"));
        tournament.setQualifiersCount(rs.getInt("qualifiers_count"));
        tournament.setSwissFirstRoundMethod(
                SwissFirstRoundMethod.fromDbValue(rs.getString("swiss_first_round_method")));
        tournament.setStatus(TournamentStatus.fromDbValue(rs.getString("status")));
        tournament.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        tournament.setStartedAt(toInstant(rs.getTimestamp("started_at")));
        tournament.setCompletedAt(toInstant(rs.getTimestamp("completed_at")));
        return tournament;
    }
}
