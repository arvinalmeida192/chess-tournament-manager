package com.chess.tournament.service;

import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.domain.Player;
import com.chess.tournament.exception.NotFoundException;
import com.chess.tournament.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerDao playerDao;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(playerDao);
    }

    @Test
    void create_rejectsBlankName() {
        assertThrows(ValidationException.class, () -> playerService.create("  ", null, null, null));
        assertThrows(ValidationException.class, () -> playerService.create(null, null, null, null));
    }

    @Test
    void create_defaultsRatingTo1500() {
        when(playerDao.insert(any(Player.class))).thenReturn(1L);
        Player persisted = new Player();
        persisted.setId(1L);
        persisted.setName("Alice");
        persisted.setGlobalRating(1500);
        when(playerDao.findById(1L)).thenReturn(Optional.of(persisted));

        Player created = playerService.create("Alice", null, null, null);

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerDao).insert(captor.capture());
        assertEquals(1500, captor.getValue().getGlobalRating());
        assertEquals("Alice", created.getName());
        assertEquals(1500, created.getGlobalRating());
    }

    @Test
    void create_rejectsNegativeAge() {
        assertThrows(ValidationException.class, () -> playerService.create("Bob", -1, "USA", 1600));
    }

    @Test
    void update_rejectsMissingPlayer() {
        when(playerDao.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> playerService.update(99L, "Name", null, null, 1500));
    }

    @Test
    void update_persistsEditedFields() {
        Player existing = new Player();
        existing.setId(5L);
        existing.setName("Old");
        existing.setGlobalRating(1500);
        existing.setActive(true);
        when(playerDao.findById(5L)).thenReturn(Optional.of(existing));

        playerService.update(5L, " New Name ", 30, " India ", 1700);

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerDao).update(captor.capture());
        assertEquals("New Name", captor.getValue().getName());
        assertEquals(30, captor.getValue().getAge());
        assertEquals("India", captor.getValue().getCountry());
        assertEquals(1700, captor.getValue().getGlobalRating());
    }

    @Test
    void list_returnsActivePlayersFromDao() {
        Player a = new Player();
        a.setName("A");
        when(playerDao.findAll(true)).thenReturn(List.of(a));

        List<Player> result = playerService.list();

        assertEquals(1, result.size());
        verify(playerDao).findAll(true);
    }

    @Test
    void deactivate_setsActiveFalse() {
        Player existing = new Player();
        existing.setId(3L);
        existing.setName("Carol");
        existing.setActive(true);
        when(playerDao.findById(3L)).thenReturn(Optional.of(existing));

        playerService.deactivate(3L);

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerDao).update(captor.capture());
        assertFalse(captor.getValue().isActive());
    }

    @Test
    void create_trimsNameAndUsesProvidedRating() {
        when(playerDao.insert(any(Player.class))).thenReturn(2L);
        Player persisted = new Player();
        persisted.setId(2L);
        persisted.setName("Dave");
        persisted.setGlobalRating(1800);
        when(playerDao.findById(2L)).thenReturn(Optional.of(persisted));

        playerService.create("  Dave  ", 20, "UK", 1800);

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerDao).insert(captor.capture());
        assertEquals("Dave", captor.getValue().getName());
        assertEquals(1800, captor.getValue().getGlobalRating());
        assertTrue(captor.getValue().isActive());
    }
}
