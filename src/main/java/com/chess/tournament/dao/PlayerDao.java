package com.chess.tournament.dao;

import com.chess.tournament.domain.Player;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface PlayerDao {

    long insert(Player player);

    long insert(Connection connection, Player player);

    Optional<Player> findById(long id);

    Optional<Player> findById(Connection connection, long id);

    List<Player> findAll(boolean activeOnly);

    List<Player> findAll(Connection connection, boolean activeOnly);

    void update(Player player);

    void update(Connection connection, Player player);
}
