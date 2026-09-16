package com.chess.tournament.service;

import com.chess.tournament.dao.PlayerDao;
import com.chess.tournament.domain.Player;
import com.chess.tournament.exception.NotFoundException;
import com.chess.tournament.exception.ValidationException;

import java.util.List;
import java.util.Objects;

/**
 * Global player registry: create, update, list, and deactivate players.
 */
public final class PlayerService {

    public static final int DEFAULT_RATING = 1500;

    private final PlayerDao playerDao;

    public PlayerService(PlayerDao playerDao) {
        this.playerDao = Objects.requireNonNull(playerDao, "playerDao");
    }

    /**
     * Creates a new active player. Blank rating defaults to {@link #DEFAULT_RATING}.
     *
     * @param name         required, trimmed
     * @param age          optional, must be &gt;= 0 if present
     * @param country      optional
     * @param globalRating optional; null uses default 1500
     * @return persisted player with generated id
     */
    public Player create(String name, Integer age, String country, Integer globalRating) {
        String trimmedName = requireName(name);
        Integer validatedAge = validateAge(age);
        int rating = globalRating == null ? DEFAULT_RATING : validateRating(globalRating);
        String trimmedCountry = blankToNull(country);

        Player player = new Player();
        player.setName(trimmedName);
        player.setAge(validatedAge);
        player.setCountry(trimmedCountry);
        player.setGlobalRating(rating);
        player.setActive(true);

        long id = playerDao.insert(player);
        player.setId(id);
        return playerDao.findById(id).orElse(player);
    }

    /**
     * Updates demographic fields and global rating for an existing player.
     */
    public Player update(long playerId, String name, Integer age, String country, int globalRating) {
        Player existing = playerDao.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found: " + playerId));

        existing.setName(requireName(name));
        existing.setAge(validateAge(age));
        existing.setCountry(blankToNull(country));
        existing.setGlobalRating(validateRating(globalRating));
        playerDao.update(existing);
        return playerDao.findById(playerId).orElse(existing);
    }

    /**
     * Returns active players sorted by name (DAO order).
     */
    public List<Player> list() {
        return playerDao.findAll(true);
    }

    /**
     * Soft-deactivates a player ({@code active=false}).
     */
    public void deactivate(long playerId) {
        Player existing = playerDao.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found: " + playerId));
        existing.setActive(false);
        playerDao.update(existing);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Player name is required");
        }
        return name.trim();
    }

    private static Integer validateAge(Integer age) {
        if (age == null) {
            return null;
        }
        if (age < 0) {
            throw new ValidationException("Age must be greater than or equal to 0");
        }
        return age;
    }

    private static int validateRating(int rating) {
        if (rating < 0) {
            throw new ValidationException("Rating must be greater than or equal to 0");
        }
        return rating;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
