package com.chess.tournament.dao;

import com.chess.tournament.exception.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class JdbcUnitOfWork implements UnitOfWork {

    private final DataSource dataSource;

    public JdbcUnitOfWork(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T executeInTransaction(TransactionCallback<T> callback) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = callback.doInTransaction(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new DataAccessException("Transaction failed", e);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to acquire connection for transaction", e);
        }
    }
}
