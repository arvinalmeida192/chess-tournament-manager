package com.chess.tournament.dao.impl;

import com.chess.tournament.dao.JdbcSupport;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.enums.RoundStatus;
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

public final class JdbcRoundDao implements RoundDao {

    private static final String INSERT = """
            INSERT INTO round (tournament_id, round_number, status, paired_at, completed_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_TOURNAMENT_AND_NUMBER = """
            SELECT id, tournament_id, round_number, status, paired_at, completed_at
            FROM round WHERE tournament_id = ? AND round_number = ?
            """;

    private static final String SELECT_BY_TOURNAMENT = """
            SELECT id, tournament_id, round_number, status, paired_at, completed_at
            FROM round WHERE tournament_id = ? ORDER BY round_number
            """;

    private static final String UPDATE_STATUS = """
            UPDATE round SET status = ?, paired_at = ?, completed_at = ? WHERE id = ?
            """;

    private final DataSource dataSource;

    public JdbcRoundDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long insert(Round round) {
        return JdbcSupport.withConnection(conn -> insert(conn, round), dataSource, "Failed to insert round");
    }

    @Override
    public long insert(Connection connection, Round round) {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, round.getTournamentId());
            ps.setInt(2, round.getRoundNumber());
            ps.setString(3, round.getStatus().name());
            ps.setTimestamp(4, toTimestamp(round.getPairedAt()));
            ps.setTimestamp(5, toTimestamp(round.getCompletedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DataAccessException("Insert round did not return generated key");
        } catch (SQLException e) {
            throw wrap("Failed to insert round", e);
        }
    }

    @Override
    public Optional<Round> findByTournamentAndNumber(long tournamentId, int roundNumber) {
        return JdbcSupport.withConnection(conn -> findByTournamentAndNumber(conn, tournamentId, roundNumber),
                dataSource, "Failed to find round");
    }

    @Override
    public Optional<Round> findByTournamentAndNumber(Connection connection, long tournamentId, int roundNumber) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_TOURNAMENT_AND_NUMBER)) {
            ps.setLong(1, tournamentId);
            ps.setInt(2, roundNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw wrap("Failed to find round by tournament and number", e);
        }
    }

    @Override
    public List<Round> findByTournament(long tournamentId) {
        return JdbcSupport.withConnection(conn -> findByTournament(conn, tournamentId), dataSource,
                "Failed to list rounds");
    }

    @Override
    public List<Round> findByTournament(Connection connection, long tournamentId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_TOURNAMENT)) {
            ps.setLong(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Round> rounds = new ArrayList<>();
                while (rs.next()) {
                    rounds.add(mapRow(rs));
                }
                return rounds;
            }
        } catch (SQLException e) {
            throw wrap("Failed to list rounds", e);
        }
    }

    @Override
    public void updateStatus(long roundId, RoundStatus status, Instant pairedAt, Instant completedAt) {
        JdbcSupport.withConnectionVoid(conn -> updateStatus(conn, roundId, status, pairedAt, completedAt),
                dataSource, "Failed to update round status");
    }

    @Override
    public void updateStatus(Connection connection, long roundId, RoundStatus status,
                             Instant pairedAt, Instant completedAt) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.name());
            ps.setTimestamp(2, toTimestamp(pairedAt));
            ps.setTimestamp(3, toTimestamp(completedAt));
            ps.setLong(4, roundId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap("Failed to update round status", e);
        }
    }

    private static Round mapRow(ResultSet rs) throws SQLException {
        Round round = new Round();
        round.setId(rs.getLong("id"));
        round.setTournamentId(rs.getLong("tournament_id"));
        round.setRoundNumber(rs.getInt("round_number"));
        round.setStatus(RoundStatus.fromDbValue(rs.getString("status")));
        round.setPairedAt(toInstant(rs.getTimestamp("paired_at")));
        round.setCompletedAt(toInstant(rs.getTimestamp("completed_at")));
        return round;
    }
}
