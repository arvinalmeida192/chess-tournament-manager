package com.chess.tournament.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadFromClasspath_readsTestProperties() {
        DatabaseConfig config = DatabaseConfig.loadFromClasspath("test-db.properties");

        assertEquals("jdbc:postgresql://localhost:5432/chess_tournament_test", config.getJdbcUrl());
        assertEquals("test_user", config.getUsername());
        assertEquals("test_pass", config.getPassword());
        assertEquals(3, config.getPoolSize());
    }

    @Test
    void fromProperties_readsApplicationProperties() throws Exception {
        Path propsFile = tempDir.resolve("application.properties");
        Files.writeString(propsFile, """
                db.url=jdbc:postgresql://127.0.0.1:5432/demo
                db.user=demo_user
                db.password=demo_pass
                db.pool.size=7
                """);

        java.util.Properties props = new java.util.Properties();
        try (var reader = Files.newBufferedReader(propsFile)) {
            props.load(reader);
        }

        // fromProperties ignores CTMS_DB_* env so file contents are asserted reliably
        DatabaseConfig config = DatabaseConfig.fromProperties(props);

        assertEquals("jdbc:postgresql://127.0.0.1:5432/demo", config.getJdbcUrl());
        assertEquals("demo_user", config.getUsername());
        assertEquals("demo_pass", config.getPassword());
        assertEquals(7, config.getPoolSize());
    }

    @Test
    void loadFromClasspath_missingResource_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.loadFromClasspath("does-not-exist.properties"));
        assertTrue(ex.getMessage().contains("does-not-exist.properties"));
    }

    @Test
    void constructor_rejectsInvalidPoolSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("jdbc:postgresql://localhost/db", "u", "p", 0));
    }
}
