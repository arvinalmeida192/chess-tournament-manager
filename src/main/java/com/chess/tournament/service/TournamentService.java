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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TournamentService {

    private static final Logger log = LoggerFactory.getLogger(TournamentService.class);

    private final TournamentDao tournamentDao;
    private final TournamentPlayerDao tournamentPlayerDao;
    private final RoundDao roundDao;
    private final UnitOfWork unitOfWork;
    private final QualificationService qualificationService;

    public TournamentService(TournamentDao tournamentDao,
                             TournamentPlayerDao tournamentPlayerDao,
                             RoundDao roundDao,
                             UnitOfWork unitOfWork,
                             QualificationService qualificationService) {
        this.tournamentDao = Objects.requireNonNull(tournamentDao);
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.roundDao = Objects.requireNonNull(roundDao);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.qualificationService = Objects.requireNonNull(qualificationService);
    }

    /**
     * Backward-compatible constructor for tests that do not exercise finalize.
     */
    public TournamentService(TournamentDao tournamentDao,
                             TournamentPlayerDao tournamentPlayerDao,
                             RoundDao roundDao,
                             UnitOfWork unitOfWork) {
        this.tournamentDao = Objects.requireNonNull(tournamentDao);
        this.tournamentPlayerDao = Objects.requireNonNull(tournamentPlayerDao);
        this.roundDao = Objects.requireNonNull(roundDao);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.qualificationService = null;
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

    /**
     * Finalizes an ACTIVE tournament when all planned rounds are COMPLETED:
     * applies qualification and sets status COMPLETED.
     */
    public void finalizeTournament(long tournamentId) {
        if (qualificationService == null) {
            throw new IllegalStateException("QualificationService is not configured");
        }

        Tournament tournament = tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));

        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new ValidationException("Only ACTIVE tournaments can be finalized");
        }
        if (!areAllRoundsComplete(tournament)) {
            throw new ValidationException(
                    "Cannot finalize: all " + tournament.getRoundsPlanned()
                            + " planned rounds must be COMPLETED");
        }

        // Qualify while still ACTIVE, then mark COMPLETED (FR-QLF-002)
        qualificationService.applyQualification(tournamentId);

        Instant completedAt = Instant.now();
        unitOfWork.executeInTransaction(connection -> {
            tournamentDao.updateStatus(connection, tournamentId, TournamentStatus.COMPLETED,
                    tournament.getStartedAt(), completedAt);
            return null;
        });
        log.info("Finalized tournament {}", tournamentId);
    }

    public boolean areAllRoundsComplete(long tournamentId) {
        Tournament tournament = tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));
        return areAllRoundsComplete(tournament);
    }

    private boolean areAllRoundsComplete(Tournament tournament) {
        List<Round> rounds = roundDao.findByTournament(tournament.getId());
        if (rounds.size() < tournament.getRoundsPlanned()) {
            return false;
        }
        for (int n = 1; n <= tournament.getRoundsPlanned(); n++) {
            final int roundNumber = n;
            Optional<Round> round = rounds.stream()
                    .filter(r -> r.getRoundNumber() == roundNumber)
                    .findFirst();
            if (round.isEmpty() || round.get().getStatus() != RoundStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    public int enrolledCount(long tournamentId) {
        return tournamentPlayerDao.findByTournament(tournamentId).size();
    }

    /**
     * Cancels a DRAFT or ACTIVE tournament (FR-TNM-007). History is retained.
     */
    public void cancelTournament(long tournamentId) {
        Tournament tournament = tournamentDao.findById(tournamentId)
                .orElseThrow(() -> new NotFoundException("Tournament not found: " + tournamentId));

        if (tournament.getStatus() != TournamentStatus.DRAFT
                && tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new ValidationException("Only DRAFT or ACTIVE tournaments can be cancelled");
        }

        Instant completedAt = tournament.getStatus() == TournamentStatus.ACTIVE
                ? Instant.now()
                : null;
        tournamentDao.updateStatus(tournamentId, TournamentStatus.CANCELLED,
                tournament.getStartedAt(), completedAt);
        log.info("Cancelled tournament {}", tournamentId);
    }
}
