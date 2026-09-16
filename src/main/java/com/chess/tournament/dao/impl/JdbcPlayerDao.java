package com.chess.tournament.dao.impl;

import com.chess.tournament.dao.JdbcSupport;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.domain.Player;
import com.chess.tournament.exception.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.chess.tournament.dao.JdbcSupport.toInstant;
import static com.chess.tournament.dao.JdbcSupport.wrap;

public final class JdbcPlayerDao implements PlayerDao {

    private static final String INSERT = """
            INSERT INTO player (name, age, country, global_rating, active)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT id, name, age, country, global_rating, active, created_at, updated_at
            FROM player WHERE id = ?
            """;

    private static final String SELECT_ALL = """
            SELECT id, name, age, country, global_rating, active, created_at, updated_at
            FROM player
            """;

    private static final String UPDATE = """
            UPDATE player
            SET name = ?, age = ?, country = ?, global_rating = ?, active = ?, updated_at = NOW()
            WHERE id = ?
            """;

    private final DataSource dataSource;

    public JdbcPlayerDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long insert(Player player) {
        return JdbcSupport.withConnection(conn -> insert(conn, player), dataSource, "Failed to insert player");
    }

    @Override
    public long insert(Connection connection, Player player) {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, player.getName());
            if (player.getAge() != null) {
                ps.setInt(2, player.getAge());
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setString(3, player.getCountry());
            ps.setInt(4, player.getGlobalRating());
            ps.setBoolean(5, player.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DataAccessException("Insert player did not return generated key");
        } catch (SQLException e) {
            throw wrap("Failed to insert player", e);
        }
    }

    @Override
    public Optional<Player> findById(long id) {
        return JdbcSupport.withConnection(conn -> findById(conn, id), dataSource, "Failed to find player");
    }

    @Override
    public Optional<Player> findById(Connection connection, long id) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw wrap("Failed to find player by id", e);
        }
    }

    @Override
    public List<Player> findAll(boolean activeOnly) {
        return JdbcSupport.withConnection(conn -> findAll(conn, activeOnly), dataSource, "Failed to list players");
    }

    @Override
    public List<Player> findAll(Connection connection, boolean activeOnly) {
        String sql = activeOnly ? SELECT_ALL + " WHERE active = TRUE ORDER BY name" : SELECT_ALL + " ORDER BY name";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Player> players = new ArrayList<>();
            while (rs.next()) {
                players.add(mapRow(rs));
            }
            return players;
        } catch (SQLException e) {
            throw wrap("Failed to list players", e);
        }
    }

    @Override
    public void update(Player player) {
        JdbcSupport.withConnectionVoid(conn -> update(conn, player), dataSource, "Failed to update player");
    }

    @Override
    public void update(Connection connection, Player player) {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            ps.setString(1, player.getName());
            if (player.getAge() != null) {
                ps.setInt(2, player.getAge());
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setString(3, player.getCountry());
            ps.setInt(4, player.getGlobalRating());
            ps.setBoolean(5, player.isActive());
            ps.setLong(6, player.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw wrap("Failed to update player", e);
        }
    }

    private static Player mapRow(ResultSet rs) throws SQLException {
        Player player = new Player();
        player.setId(rs.getLong("id"));
        player.setName(rs.getString("name"));
        player.setAge(JdbcSupport.getNullableInt(rs, "age"));
        player.setCountry(rs.getString("country"));
        player.setGlobalRating(rs.getInt("global_rating"));
        player.setActive(rs.getBoolean("active"));
        player.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        player.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return player;
    }
}
