package com.chess.tournament.integration;

import com.chess.tournament.dao.JdbcUnitOfWork;
import com.chess.tournament.dao.impl.JdbcPlayerDao;
import com.chess.tournament.domain.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcUnitOfWorkIntegrationTest extends AbstractPostgresIntegrationTest {

    private final JdbcPlayerDao playerDao = new JdbcPlayerDao(getDataSource());
    private final JdbcUnitOfWork unitOfWork = new JdbcUnitOfWork(getDataSource());

    @Test
    void executeInTransaction_commitsOnSuccess() {
        Long id = unitOfWork.executeInTransaction(connection -> {
            Player player = new Player();
            player.setName("Transactional");
            return playerDao.insert(connection, player);
        });

        assertTrue(playerDao.findById(id).isPresent());
    }

    @Test
    void executeInTransaction_rollsBackOnFailure() {
        assertThrows(RuntimeException.class, () -> unitOfWork.executeInTransaction(connection -> {
            Player player = new Player();
            player.setName("Rollback");
            playerDao.insert(connection, player);
            throw new RuntimeException("force rollback");
        }));

        assertEquals(0, playerDao.findAll(false).size());
    }
}
