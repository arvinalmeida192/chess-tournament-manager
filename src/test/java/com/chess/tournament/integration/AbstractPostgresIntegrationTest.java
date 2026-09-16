package com.chess.tournament.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

abstract class AbstractPostgresIntegrationTest {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("chess_tournament_test")
            .withUsername("test")
            .withPassword("test");

    protected static HikariDataSource dataSource;

    @BeforeAll
    static void startDatabase() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (dataSource == null) {
            dataSource = createDataSource(POSTGRES);
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
        }
    }

    @AfterAll
    static void stopDatabase() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }

    @BeforeEach
    void cleanTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE game, round, tournament_player, tournament, player RESTART IDENTITY CASCADE
                    """);
        }
    }

    protected static DataSource getDataSource() {
        return dataSource;
    }

    private static HikariDataSource createDataSource(PostgreSQLContainer<?> postgres) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(3);
        config.setPoolName("ctms-test-pool");
        return new HikariDataSource(config);
    }
}
