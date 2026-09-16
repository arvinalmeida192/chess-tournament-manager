# End-to-End Checklist (Phase 10)

Maps to [SRS §13.1 Release checklist](../SRS.md#131-release-checklist).

**Environment:** PostgreSQL via Docker (`ctms-postgres`), `mvn javafx:run`, fresh or migrated DB.

| # | Scenario | Steps | Expected | Result |
|---|----------|-------|----------|--------|
| 1 | Round Robin (6 players, 5 rounds) | Create RR `rounds=5`, enroll 6 players, Start → for each round: Pairings → Generate & Publish → Enter Results → Complete → Leaderboard | All 5 rounds complete; standings points = W+0.5D; finalize works | ☐ PASS / ☐ FAIL |
| 2 | Knockout (8 players) | Create KO, enroll 8, Start → publish R1 (4 games) → results → complete → R2 → R3 final | Bracket shows winners; champion sole remaining non-eliminated; losers `ELIMINATED` | ☐ PASS / ☐ FAIL |
| 3 | Swiss (8 players, 4 rounds)* | Create Swiss `rounds=4`, Q=4, enroll 8, run all rounds | No rematch when alternatives exist; points/Elo update each complete; leaderboard sorts by BR-TIE | ☐ PASS / ☐ FAIL |
| 4 | Qualification top 4 | After Swiss (or any) with Q=4 and completed rounds: Apply Qualification or Finalize | Exactly four `QUALIFIED`, others `ELIMINATED` | ☐ PASS / ☐ FAIL |
| 5 | DB restart persistence | Complete a tournament; stop app; restart `mvn javafx:run` | Players, tournaments, games, standings still present | ☐ PASS / ☐ FAIL |
| 6 | Invalid actions blocked | Attempt: generate R2 before R1 complete; finalize early; enroll after pairings published; complete round with PENDING | Clear validation errors; buttons disabled per UI-001 | ☐ PASS / ☐ FAIL |

\*SRS mentions 16 players / 5 rounds for Swiss stress; 8×4 is the practical manual path. Automated Swiss coverage lives in unit/integration tests (including rematch avoidance).

## Demo seed verification

```bash
docker exec -i ctms-postgres psql -U ctms -d chess_tournament < src/main/resources/db/seed/demo.sql
mvn javafx:run
```

Open **Tournaments** → **Demo Swiss Showcase** (COMPLETED) → **Leaderboard** → confirm final columns and 4× `QUALIFIED`.

## UI polish checks (Phase 10)

| Check | Result |
|-------|--------|
| Toolbar: Home / Players / Tournaments / Test DB | ☐ |
| Settings → Test Database Connection shows OK dialog | ☐ |
| Dashboard disables Start / Pairings / Results / Finalize / Cancel by state | ☐ |
| Confirm dialogs on Start, Complete Round, Finalize, Cancel | ☐ |
| `app.css` applied (table headers, status colors) | ☐ |

## Automated gate

```bash
mvn test
```

Must remain green before release.

## Sign-off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Host / tester | | | |
| Developer | | 2026-09-16 | Phase 10 implemented; manual rows above for host run |
