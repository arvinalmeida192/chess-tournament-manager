package com.chess.tournament.dao;

import com.chess.tournament.domain.TournamentPlayer;
import com.chess.tournament.domain.enums.QualificationStatus;

import java.sql.Connection;
import java.util.List;

public interface TournamentPlayerDao {

    long insert(TournamentPlayer tournamentPlayer);

    long insert(Connection connection, TournamentPlayer tournamentPlayer);

    List<TournamentPlayer> findByTournament(long tournamentId);

    List<TournamentPlayer> findByTournament(Connection connection, long tournamentId);

    void updateStats(TournamentPlayer tournamentPlayer);

    void updateStats(Connection connection, TournamentPlayer tournamentPlayer);

    void updateQualification(long tournamentPlayerId, QualificationStatus status);

    void updateQualification(Connection connection, long tournamentPlayerId, QualificationStatus status);

    void deleteByTournamentAndPlayer(long tournamentId, long playerId);

    void deleteByTournamentAndPlayer(Connection connection, long tournamentId, long playerId);
}
