package com.chess.tournament.service;

import com.chess.tournament.dao.RoundDao;
import com.chess.tournament.dao.TournamentDao;
import com.chess.tournament.dao.TournamentPlayerDao;
import com.chess.tournament.dao.UnitOfWork;
import com.chess.tournament.domain.Round;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.RoundStatus;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.exception.NotFoundException;
import com.chess.tournament.exception.ValidationException;
import com.chess.tournament.service.dto.CreateTournamentCommand;
import com.chess.tournament.service.validation.SwissValidator;
import com.chess.tournament.service.validation.TournamentValidator;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TournamentService {

    private final TournamentDao tournamentDao;
    private final TournamentPlayerDao tournamentPlayerDao;
    private final RoundDao roundDao;
    private final UnitOfWork unitOfWork;

    public TournamentService(TournamentDao tournamentDao,
                             TournamentPlayerDao tournamentPlayerDao,
                             RoundDao roundDao,
                             UnitOfWork unitOfWork) {
        this.tournamentDao = Objects.requireNonNull(tournamentDao);
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.roundDao = Objects.requireNonNull(roundDao);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    public Tournament create(CreateTournamentCommand command) {
        TournamentValidator.validateCreate(command);

        Tournament tournament = new Tournament();
        tournament.setName(command.getName().trim());
        tournament.setType(command.getType());
        tournament.setRoundsPlanned(command.getRoundsPlanned());
        tournament.setQualifiersCount(command.getQualifiersCount());
        tournament.setSwissFirstRoundMethod(
                TournamentValidator.resolveSwissMethod(command.getType(), command.getSwissFirstRoundMethod()));
        tournament.setStatus(TournamentStatus.DRAFT);

        long id = tournamentDao.insert(tournament);
        tournament.setId(id);
        return tournamentDao.findById(id).orElse(tournament);
    }

    public Optional<Tournament> findById(long id) {
        return tournamentDao.findById(id);
    }

    public List<Tournament> listAll() {
        return tournamentDao.findAll();
    }

    public List<Tournament> listByStatus(TournamentStatus status) {
        return tournamentDao.findByStatus(status);
    }

    /**
     * Starts a DRAFT tournament: validates enrollment, sets ACTIVE, inserts round 1.
     *
     * @return warning message for Swiss with fewer than 4 players, otherwise empty
     */
    public Optional<String> start(long tournamentId) {
        Tournament tournament = tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));

        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new ValidationException("Only DRAFT tournaments can be started");
        }

        int enrolled = tournamentPlayerDao.findByTournament(tournamentId).size();
        TournamentValidator.validateStart(tournament.getType(), tournament.getRoundsPlanned(), enrolled);

        Instant startedAt = Instant.now();
        unitOfWork.executeInTransaction(connection -> {
            tournamentDao.updateStatus(connection, tournamentId, TournamentStatus.ACTIVE, startedAt, null);

            Round round = new Round();
            round.setTournamentId(tournamentId);
            round.setRoundNumber(1);
            round.setStatus(RoundStatus.PENDING_PAIRINGS);
            roundDao.insert(connection, round);
            return null;
        });

        if (tournament.getType() == com.chess.tournament.domain.enums.TournamentType.SWISS
                && SwissValidator.shouldWarnLowPlayerCount(enrolled)) {
            return Optional.of("Swiss tournaments work best with at least 4 players (currently "
                    + enrolled + ")");
        }
        return Optional.empty();
    }

    public int enrolledCount(long tournamentId) {
        return tournamentPlayerDao.findByTournament(tournamentId).size();
    }
}
