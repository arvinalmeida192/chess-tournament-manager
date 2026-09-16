package com.chess.tournament.dao;

import com.chess.tournament.exception.DataAccessException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public final class JdbcSupport {

    private JdbcSupport() {
    }

    public static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static DataAccessException wrap(String message, SQLException cause) {
        return new DataAccessException(message, cause);
    }

    public static <T> T withConnection(java.util.function.Function<Connection, T> action,
                                  javax.sql.DataSource dataSource, String errorMessage) {
        try (Connection connection = dataSource.getConnection()) {
            return action.apply(connection);
        } catch (SQLException e) {
            throw wrap(errorMessage, e);
        }
    }

    public static void withConnectionVoid(java.util.function.Consumer<Connection> action,
                                   javax.sql.DataSource dataSource, String errorMessage) {
        try (Connection connection = dataSource.getConnection()) {
            action.accept(connection);
        } catch (SQLException e) {
            throw wrap(errorMessage, e);
        }
    }
}
