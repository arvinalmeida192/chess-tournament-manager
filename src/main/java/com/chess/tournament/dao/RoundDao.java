package com.chess.tournament.dao;

import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.enums.RoundStatus;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoundDao {

    long insert(Round round);

    long insert(Connection connection, Round round);

    Optional<Round> findByTournamentAndNumber(long tournamentId, int roundNumber);

    Optional<Round> findByTournamentAndNumber(Connection connection, long tournamentId, int roundNumber);

    Optional<Round> findById(long roundId);

    Optional<Round> findById(Connection connection, long roundId);

    List<Round> findByTournament(long tournamentId);

    List<Round> findByTournament(Connection connection, long tournamentId);

    void updateStatus(long roundId, RoundStatus status, Instant pairedAt, Instant completedAt);

    void updateStatus(Connection connection, long roundId, RoundStatus status,
                      Instant pairedAt, Instant completedAt);
}
