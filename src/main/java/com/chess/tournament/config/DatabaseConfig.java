package com.chess.tournament.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads database connection settings from environment variables first,
 * then from {@code config/application.properties} relative to the working directory.
 */
public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    public static final String ENV_URL = "CTMS_DB_URL";
    public static final String ENV_USER = "CTMS_DB_USER";
    public static final String ENV_PASSWORD = "CTMS_DB_PASSWORD";

    private static final String KEY_URL = "db.url";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASSWORD = "db.password";
    private static final String KEY_POOL_SIZE = "db.pool.size";

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int poolSize;

    public DatabaseConfig(String jdbcUrl, String username, String password, int poolSize) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be >= 1");
        }
        this.poolSize = poolSize;
    }

    public static DatabaseConfig load() {
        return load(Path.of("config", "application.properties"));
    }

    public static DatabaseConfig load(Path propertiesFile) {
        Properties fileProps = new Properties();
        if (propertiesFile != null && Files.isRegularFile(propertiesFile)) {
            try (Reader reader = Files.newBufferedReader(propertiesFile)) {
                fileProps.load(reader);
                log.info("Loaded database config from {}", propertiesFile.toAbsolutePath());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + propertiesFile.toAbsolutePath(), e);
            }
        } else {
            log.warn("Properties file not found at {}; relying on environment variables / defaults",
                    propertiesFile == null ? "<null>" : propertiesFile.toAbsolutePath());
        }

        String url = firstNonBlank(System.getenv(ENV_URL), fileProps.getProperty(KEY_URL),
                "jdbc:postgresql://localhost:5432/chess_tournament");
        String user = firstNonBlank(System.getenv(ENV_USER), fileProps.getProperty(KEY_USER), "ctms");
        String password = firstNonBlank(System.getenv(ENV_PASSWORD), fileProps.getProperty(KEY_PASSWORD), "changeme");
        int poolSize = parsePoolSize(fileProps.getProperty(KEY_POOL_SIZE), 5);

        return new DatabaseConfig(url, user, password, poolSize);
    }

    /**
     * Loads configuration from a classpath resource only (no environment override).
     * Intended for unit tests and deterministic fixtures.
     */
    public static DatabaseConfig loadFromClasspath(String resourcePath) {
        Properties props = new Properties();
        try (InputStream in = DatabaseConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resourcePath);
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + resourcePath, e);
        }
        return fromProperties(props);
    }

    /**
     * Builds config from a {@link Properties} object only (no environment override).
     */
    public static DatabaseConfig fromProperties(Properties props) {
        Objects.requireNonNull(props, "props");
        String url = firstNonBlank(props.getProperty(KEY_URL), null);
        String user = firstNonBlank(props.getProperty(KEY_USER), null);
        String password = firstNonBlank(props.getProperty(KEY_PASSWORD), "");
        if (url == null || user == null) {
            throw new IllegalStateException("db.url and db.user are required");
        }
        int poolSize = parsePoolSize(props.getProperty(KEY_POOL_SIZE), 5);
        return new DatabaseConfig(url, user, password, poolSize);
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getPoolSize() {
        return poolSize;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static int parsePoolSize(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid db.pool.size: " + raw, e);
        }
    }
}
