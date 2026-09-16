package com.chess.tournament.integration;

import com.chess.tournament.dao.impl.JdbcTournamentDao;
import com.chess.tournament.domain.Tournament;
import com.chess.tournament.domain.enums.SwissFirstRoundMethod;
import com.chess.tournament.domain.enums.TournamentStatus;
import com.chess.tournament.domain.enums.TournamentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentDaoIntegrationTest extends AbstractPostgresIntegrationTest {

    private final JdbcTournamentDao tournamentDao = new JdbcTournamentDao(getDataSource());

    @Test
    void insertAndFindById_returnsPersistedTournament() {
        Tournament tournament = new Tournament();
        tournament.setName("Spring Open");
        tournament.setType(TournamentType.SWISS);
        tournament.setRoundsPlanned(5);
        tournament.setQualifiersCount(4);
        tournament.setSwissFirstRoundMethod(SwissFirstRoundMethod.RATING_SPLIT);
        tournament.setStatus(TournamentStatus.DRAFT);

        long id = tournamentDao.insert(tournament);

        Optional<Tournament> found = tournamentDao.findById(id);
        assertTrue(found.isPresent());
        assertEquals("Spring Open", found.get().getName());
        assertEquals(TournamentType.SWISS, found.get().getType());
        assertEquals(5, found.get().getRoundsPlanned());
        assertEquals(4, found.get().getQualifiersCount());
        assertEquals(SwissFirstRoundMethod.RATING_SPLIT, found.get().getSwissFirstRoundMethod());
        assertEquals(TournamentStatus.DRAFT, found.get().getStatus());
        assertNotNull(found.get().getCreatedAt());
    }

    @Test
    void findByStatus_andUpdateStatus() {
        Tournament draft = new Tournament();
        draft.setName("Draft Event");
        draft.setType(TournamentType.ROUND_ROBIN);
        draft.setRoundsPlanned(7);
        draft.setQualifiersCount(0);
        long draftId = tournamentDao.insert(draft);

        Tournament active = new Tournament();
        active.setName("Active Event");
        active.setType(TournamentType.KNOCKOUT);
        active.setRoundsPlanned(3);
        active.setQualifiersCount(1);
        active.setStatus(TournamentStatus.ACTIVE);
        long activeId = tournamentDao.insert(active);

        List<Tournament> drafts = tournamentDao.findByStatus(TournamentStatus.DRAFT);
        assertEquals(1, drafts.size());
        assertEquals(draftId, drafts.get(0).getId());

        Instant startedAt = Instant.parse("2026-01-15T10:00:00Z");
        tournamentDao.updateStatus(activeId, TournamentStatus.COMPLETED, startedAt, Instant.now());

        Tournament updated = tournamentDao.findById(activeId).orElseThrow();
        assertEquals(TournamentStatus.COMPLETED, updated.getStatus());
        assertEquals(startedAt, updated.getStartedAt());
        assertNotNull(updated.getCompletedAt());
    }
}
