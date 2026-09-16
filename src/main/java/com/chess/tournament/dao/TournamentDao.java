package com.chess.tournament.dao;

import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.TournamentStatus;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TournamentDao {

    long insert(Tournament tournament);

    long insert(Connection connection, Tournament tournament);

    Optional<Tournament> findById(long id);

    Optional<Tournament> findById(Connection connection, long id);

    List<Tournament> findByStatus(TournamentStatus status);

    List<Tournament> findByStatus(Connection connection, TournamentStatus status);

    List<Tournament> findAll();

    List<Tournament> findAll(Connection connection);

    void updateStatus(long id, TournamentStatus status, Instant startedAt, Instant completedAt);

    void updateStatus(Connection connection, long id, TournamentStatus status,
                      Instant startedAt, Instant completedAt);
}
