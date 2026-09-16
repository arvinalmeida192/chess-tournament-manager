package com.chess.tournament.dao;

import com.chess.tournament.domain.Game;
import com.chess.tournament.domain.LongPair;

import java.sql.Connection;
import java.util.List;
import java.util.Set;

public interface GameDao {

    void insertBatch(List<Game> games);

    void insertBatch(Connection connection, List<Game> games);

    List<Game> findByRound(long roundId);

    List<Game> findByRound(Connection connection, long roundId);

    void updateResult(Game game);

    void updateResult(Connection connection, Game game);

    Set<LongPair> findPreviousPairings(long tournamentId);

    Set<LongPair> findPreviousPairings(Connection connection, long tournamentId);

    boolean existsForTournament(long tournamentId);

    boolean existsForTournament(Connection connection, long tournamentId);
}
