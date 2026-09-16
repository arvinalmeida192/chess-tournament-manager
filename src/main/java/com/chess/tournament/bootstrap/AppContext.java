package com.chess.tournament.bootstrap;

import com.chess.tournament.config.DatabaseConfig;
import com.chess.tournament.dao.GameDao;
import com.chess.tournament.dao.JdbcUnitOfWork;
import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.dao.UnitOfWork;
import com.chess.tournament.dao.impl.JdbcGameDao;
import com.chess.tournament.dao.impl.JdbcPlayerDao;
import com.chess.tournament.dao.impl.JdbcRoundDao;
import com.chess.tournament.dao.impl.JdbcTournamentDao;
import com.chess.tournament.dao.impl.JdbcTournamentPlayerDao;
import com.chess.tournament.service.EnrollmentService;
import com.chess.tournament.service.PlayerService;
import com.chess.tournament.service.TournamentService;
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
    private final PlayerDao playerDao;
    private final TournamentDao tournamentDao;
    private final TournamentPlayerDao tournamentPlayerDao;
    private final RoundDao roundDao;
    private final GameDao gameDao;
    private final UnitOfWork unitOfWork;
    private final PlayerService playerService;
    private final TournamentService tournamentService;
    private final EnrollmentService enrollmentService;

    private AppContext(DatabaseConfig databaseConfig, HikariDataSource dataSource) {
        this.databaseConfig = databaseConfig;
        this.dataSource = dataSource;
        this.playerDao = new JdbcPlayerDao(dataSource);
        this.tournamentDao = new JdbcTournamentDao(dataSource);
        this.tournamentPlayerDao = new JdbcTournamentPlayerDao(dataSource);
        this.roundDao = new JdbcRoundDao(dataSource);
        this.gameDao = new JdbcGameDao(dataSource);
        this.unitOfWork = new JdbcUnitOfWork(dataSource);
        this.playerService = new PlayerService(playerDao);
        this.tournamentService = new TournamentService(
                tournamentDao, tournamentPlayerDao, roundDao, unitOfWork);
        this.enrollmentService = new EnrollmentService(
                tournamentDao, tournamentPlayerDao, playerDao, roundDao, gameDao);
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

    public PlayerDao getPlayerDao() {
        return playerDao;
    }

    public TournamentDao getTournamentDao() {
        return tournamentDao;
    }

    public TournamentPlayerDao getTournamentPlayerDao() {
        return tournamentPlayerDao;
    }

    public RoundDao getRoundDao() {
        return roundDao;
    }

    public GameDao getGameDao() {
        return gameDao;
    }

    public UnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    public PlayerService getPlayerService() {
        return playerService;
    }

    public TournamentService getTournamentService() {
        return tournamentService;
    }

    public EnrollmentService getEnrollmentService() {
        return enrollmentService;
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
