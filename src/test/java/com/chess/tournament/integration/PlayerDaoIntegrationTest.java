package com.chess.tournament.integration;

import com.chess.tournament.dao.impl.JdbcPlayerDao;
import com.chess.tournament.domain.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDaoIntegrationTest extends AbstractPostgresIntegrationTest {

    private final JdbcPlayerDao playerDao = new JdbcPlayerDao(getDataSource());

    @Test
    void insertAndFindById_returnsPersistedPlayer() {
        Player player = new Player();
        player.setName("Alice");
        player.setAge(25);
        player.setCountry("USA");
        player.setGlobalRating(1600);

        long id = playerDao.insert(player);

        Optional<Player> found = playerDao.findById(id);
        assertTrue(found.isPresent());
        assertEquals("Alice", found.get().getName());
        assertEquals(25, found.get().getAge());
        assertEquals("USA", found.get().getCountry());
        assertEquals(1600, found.get().getGlobalRating());
        assertTrue(found.get().isActive());
        assertNotNull(found.get().getCreatedAt());
        assertNotNull(found.get().getUpdatedAt());
    }

    @Test
    void update_touchesFieldsAndUpdatedAt() {
        Player player = new Player();
        player.setName("Bob");
        player.setGlobalRating(1500);
        long id = playerDao.insert(player);

        Player loaded = playerDao.findById(id).orElseThrow();
        loaded.setName("Robert");
        loaded.setGlobalRating(1700);
        loaded.setActive(false);
        playerDao.update(loaded);

        Player updated = playerDao.findById(id).orElseThrow();
        assertEquals("Robert", updated.getName());
        assertEquals(1700, updated.getGlobalRating());
        assertFalse(updated.isActive());
        assertTrue(updated.getUpdatedAt().isAfter(loaded.getCreatedAt())
                || updated.getUpdatedAt().equals(loaded.getUpdatedAt()));
    }

    @Test
    void findAll_activeOnly_filtersInactivePlayers() {
        Player active = new Player();
        active.setName("Active");
        playerDao.insert(active);

        Player inactive = new Player();
        inactive.setName("Inactive");
        inactive.setActive(false);
        playerDao.insert(inactive);

        List<Player> all = playerDao.findAll(false);
        assertEquals(2, all.size());

        List<Player> activeOnly = playerDao.findAll(true);
        assertEquals(1, activeOnly.size());
        assertEquals("Active", activeOnly.get(0).getName());
    }
}
