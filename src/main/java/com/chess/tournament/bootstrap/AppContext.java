package com.chess.tournament.bootstrap;

import com.chess.tournament.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Manual composition root: loads config, creates the connection pool, runs Flyway migrations,
 * and exposes shared dependencies for controllers/services.
 */
public final class AppContext implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AppContext.class);

    private static AppContext instance;

    private final DatabaseConfig databaseConfig;
    private final HikariDataSource dataSource;

    private AppContext(DatabaseConfig databaseConfig, HikariDataSource dataSource) {
        this.databaseConfig = databaseConfig;
        this.dataSource = dataSource;
    }

    public static synchronized AppContext initialize() {
        if (instance != null) {
            return instance;
        }
        DatabaseConfig config = DatabaseConfig.load();
        HikariDataSource ds = createDataSource(config);
        runMigrations(ds);
        instance = new AppContext(config, ds);
        log.info("AppContext initialized");
        return instance;
    }

    public static synchronized AppContext get() {
        if (instance == null) {
            throw new IllegalStateException("AppContext has not been initialized");
        }
        return instance;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    @Override
    public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("DataSource closed");
        }
        if (instance == this) {
            instance = null;
        }
    }

    static synchronized void resetForTests() {
        if (instance != null) {
            instance.close();
        }
        instance = null;
    }

    private static HikariDataSource createDataSource(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());
        hikari.setMaximumPoolSize(config.getPoolSize());
        hikari.setPoolName("ctms-pool");
        return new HikariDataSource(hikari);
    }

    private static void runMigrations(DataSource dataSource) {
        log.info("Running Flyway migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
