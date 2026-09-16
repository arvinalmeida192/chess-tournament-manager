# CTMS Development Context (Handoff)

> **Read this file first** in any new AI session before writing code.
> Authoritative specs: `docs/SRS.md`, `docs/SDD.md`, `docs/DEVELOPMENT_PLAN.md`.
> Update this file at the end of every phase (and mid-phase if stopping early).

---

## Project identity

| Item | Value |
|------|--------|
| Project | Chess Tournament Management System (CTMS) |
| Root | `/home/arvinalmeida/Chess_Java` |
| GitHub | https://github.com/arvinalmeida192/chess-tournament-manager |
| Remote | `origin` → `main` |
| Stack | Java 17 (release), JavaFX 21, Maven, JDBC, HikariCP, Flyway, PostgreSQL |
| Base package | `com.chess.tournament` |
| Architecture | UI → Service → DAO → PostgreSQL (no Spring) |

---

## Current status (snapshot)

| Field | Value |
|-------|--------|
| **Last completed phase** | **Phase 4 — Tournaments & Enrollment** |
| **Next phase to execute** | **Phase 5 — Round Robin Pairings** |
| **Build health** | `mvn test` passes (41 tests); Testcontainers PostgreSQL 16 |
| **DB for local dev** | Docker container `ctms-postgres` (Postgres 16), port `5432` |
| **DB credentials** | user `ctms` / password `changeme` / db `chess_tournament` (see `config/application.properties.example`) |

---

## Phase checklist

| Phase | Title | Status |
|-------|--------|--------|
| 1 | Foundation & database bootstrap | **DONE** |
| 2 | Domain model & data access layer | **DONE** |
| 3 | Global player registry | **DONE** |
| 4 | Tournaments & enrollment | **DONE** |
| 5 | Round Robin pairings | **NOT STARTED** |
| 6 | Knockout pairings & elimination | **NOT STARTED** |
| 7 | Swiss/Dutch pairings | **NOT STARTED** |
| 8 | Results, scoring & Elo ratings | **NOT STARTED** |
| 9 | Leaderboards & qualification | **NOT STARTED** |
| 10 | End-to-end UI & release hardening | **NOT STARTED** |

---

## What Phase 4 delivered (DONE)

### Files created

```text
src/main/java/com/chess/tournament/service/
  TournamentService.java, EnrollmentService.java
  dto/CreateTournamentCommand.java
  validation/TournamentValidator.java, RoundRobinValidator.java,
             KnockoutValidator.java, SwissValidator.java
src/main/java/com/chess/tournament/ui/
  ContentNavigator.java
  tournament/TournamentListController.java, TournamentCreateController.java,
             TournamentDashboardController.java, EnrollmentController.java,
             TournamentViewModel.java
src/main/resources/fxml/
  tournament_list.fxml, tournament_create.fxml, tournament_dashboard.fxml, enrollment.fxml
src/test/java/.../service/TournamentServiceTest.java, EnrollmentServiceTest.java
src/test/java/.../service/validation/TournamentValidatorTest.java
src/test/java/.../integration/TournamentEnrollmentIntegrationTest.java
docs/manual/phase4-tournaments.md
```

### Files updated

```text
TournamentDao / JdbcTournamentDao  ← findAll()
GameDao / JdbcGameDao               ← existsForTournament()
AppContext                          ← TournamentService, EnrollmentService
MainController + main.fxml          ← Tournaments menu, ContentNavigator
README.md, context.md
```

### Behaviors verified

- [x] Create DRAFT tournaments for RR / KO / SWISS
- [x] Start validates min players + format round counts; inserts round 1 PENDING_PAIRINGS
- [x] Enroll copies global_rating → start/current; duplicate enroll fails
- [x] Enrollment locked when ACTIVE and round 1 ≠ PENDING_PAIRINGS
- [x] Dashboard ViewModel canStart / canEnroll / canGeneratePairings flags
- [x] Integration: create → enroll 4 → start → round 1 exists
- [x] `mvn test` — 41 tests pass

### Design notes from Phase 4 (keep)

- RR/KO round counts validated at **start** against enrollment N (BR-RR-001).
- Swiss warns (does not block) when 2 ≤ N < 4.
- `ContentNavigator` swaps main content pane views.
- Pairings / Leaderboard buttons are placeholders until Phases 5 / 9.

---

## What Phase 3 delivered (DONE)

### Files created

```text
src/main/java/com/chess/tournament/service/PlayerService.java
src/main/java/com/chess/tournament/ui/player/PlayerListController.java
src/main/java/com/chess/tournament/ui/player/PlayerFormController.java
src/main/java/com/chess/tournament/ui/util/Alerts.java
src/main/resources/fxml/player_list.fxml
src/main/resources/fxml/player_form.fxml
src/test/java/com/chess/tournament/service/PlayerServiceTest.java
docs/manual/phase3-players.md
```

### Files updated

```text
src/main/java/.../bootstrap/AppContext.java  ← getPlayerService()
src/main/java/.../ui/MainController.java     ← menu navigation, content pane
src/main/resources/fxml/main.fxml            ← MenuBar + StackPane content
README.md
context.md
```

### Behaviors verified

- [x] PlayerService create/update/list/deactivate with validation
- [x] Default rating 1500 when blank
- [x] Player list UI: Add / Edit / Deactivate / Refresh
- [x] Modal form dialog; ValidationException shown via Alert
- [x] DB work off FX thread via `Task` + background `Thread`
- [x] `mvn test` — PlayerServiceTest (8) + prior tests pass
- [x] Manual checklist: `docs/manual/phase3-players.md`

### Design notes from Phase 3 (keep)

- Controllers obtain `PlayerService` via `AppContext.get().getPlayerService()`.
- List shows **active** players only (`findAll(true)`).
- Homonyms allowed (no unique name constraint) — disambiguate by ID column.

---

## What Phase 2 delivered (DONE)

### Files created

```text
src/main/java/com/chess/tournament/domain/
  Player.java, Tournament.java, TournamentPlayer.java, Round.java, Game.java, LongPair.java
  enums/TournamentType.java, TournamentStatus.java, RoundStatus.java, GameResult.java,
         QualificationStatus.java, SwissFirstRoundMethod.java
src/main/java/com/chess/tournament/exception/
  DomainException.java, ValidationException.java, NotFoundException.java, DataAccessException.java
src/main/java/com/chess/tournament/dao/
  PlayerDao.java, TournamentDao.java, TournamentPlayerDao.java, RoundDao.java, GameDao.java,
  UnitOfWork.java, JdbcUnitOfWork.java, TransactionCallback.java, JdbcSupport.java
  impl/JdbcPlayerDao.java, JdbcTournamentDao.java, JdbcTournamentPlayerDao.java,
       JdbcRoundDao.java, JdbcGameDao.java
src/test/java/com/chess/tournament/integration/
  AbstractPostgresIntegrationTest.java, PlayerDaoIntegrationTest.java,
  TournamentDaoIntegrationTest.java, GameDaoIntegrationTest.java, JdbcUnitOfWorkIntegrationTest.java
src/test/resources/application-test.properties, docker-java.properties
```

### Files updated

```text
pom.xml                          ← Testcontainers 1.21.4
src/main/java/.../AppContext.java ← wires all DAOs + UnitOfWork
README.md                         ← test instructions
context.md                        ← this handoff file
```

### Behaviors verified

- [x] All SDD §6.3 DAO methods implemented with JDBC
- [x] `JdbcUnitOfWork` commit/rollback
- [x] `findPreviousPairings` returns normalized unordered TP id pairs
- [x] `mvn test` — 13 tests pass (Testcontainers + Flyway migrate in test)
- [x] `AppContext` exposes `getPlayerDao()`, `getTournamentDao()`, etc.

### Design notes from Phase 2 (keep)

- DAO interfaces have overloads accepting `Connection` for transactional use via `UnitOfWork`.
- `BigDecimal` used for `points`, `whiteScore`, `blackScore` on domain entities.
- `LongPair` normalizes `(minId, maxId)` for rematch detection.
- Integration tests share one Testcontainers PostgreSQL instance; tables truncated per test.
- `docker-java.properties` sets `api.version=1.44` for Docker 29+ compatibility.

---

## What Phase 1 delivered (DONE)

### Files created

```text
pom.xml
.gitignore
README.md
context.md                          ← this handoff file
config/application.properties.example
config/application.properties       ← LOCAL ONLY (gitignored); do not commit
src/main/resources/db/migration/V1__initial_schema.sql
src/main/resources/fxml/main.fxml
src/main/resources/logback.xml
src/main/java/com/chess/tournament/config/DatabaseConfig.java
src/main/java/com/chess/tournament/bootstrap/AppContext.java
src/main/java/com/chess/tournament/bootstrap/ChessTournamentApp.java
src/main/java/com/chess/tournament/ui/MainController.java
src/test/java/com/chess/tournament/config/DatabaseConfigTest.java
src/test/resources/test-db.properties
docs/SRS.md, docs/SDD.md, docs/DEVELOPMENT_PLAN.md  (from prior session)
```

### Behaviors verified

- [x] `mvn -q compile` succeeds
- [x] `mvn flyway:migrate` creates tables: `player`, `tournament`, `tournament_player`, `round`, `game`, `flyway_schema_history`
- [x] Headless smoke: `AppContext.initialize()` + `SELECT 1` returns 1
- [x] Credentials not hard-coded in committed sources (example file only; real props gitignored)
- [ ] Full GUI `mvn javafx:run` — may need a display; not required if headless CI. Main window title is “Chess Tournament Manager”; label shows “Connected to database” on success.

### Design notes from Phase 1 (keep)

- Env vars `CTMS_DB_URL`, `CTMS_DB_USER`, `CTMS_DB_PASSWORD` override `config/application.properties`.
- `DatabaseConfig.loadFromClasspath` / `fromProperties` **do not** apply env overrides (for reliable unit tests).
- `AppContext` is a singleton composition root; Flyway runs on `initialize()`.
- Controllers must obtain services/DAOs via `AppContext.get()` (e.g. `getDataSource()`, `getPlayerDao()`).

### Local DB quick start (if container missing)

```bash
docker start ctms-postgres || docker run -d --name ctms-postgres \
  -e POSTGRES_DB=chess_tournament \
  -e POSTGRES_USER=ctms \
  -e POSTGRES_PASSWORD=changeme \
  -p 5432:5432 \
  postgres:16-alpine

cp config/application.properties.example config/application.properties
export CTMS_DB_URL=jdbc:postgresql://localhost:5432/chess_tournament
export CTMS_DB_USER=ctms
export CTMS_DB_PASSWORD=changeme
mvn flyway:migrate
mvn test
mvn javafx:run   # needs display
```

---

## What is NOT done (Phases 5–10)

Do **not** implement these until the matching phase. Full task lists live in `docs/DEVELOPMENT_PLAN.md`.

### Phase 5 — next (start here)

Goal: Round Robin schedule generator + pairing strategy + `PairingService` publish; enable Generate Pairings on dashboard for RR.

Must create: `RoundRobinScheduleGenerator`, `RoundRobinPairingStrategy`, `PairingStrategyFactory`, `PairingContext`, `PairingProposal`, `PairingService`, `pairings.fxml`.

### Phase 6

Knockout strategy, seeding, byes, advancement/loser hooks.

### Phase 7

Swiss first round + Dutch subsequent rounds, rematch flag, color balance.

### Phase 8

`ResultService.completeRound` (transactional), `RatingService` Elo K=32, results UI.

### Phase 9

Leaderboard tie-breaks, qualification, finalize tournament, leaderboard UI.

### Phase 10

Full workflow polish, CSS, demo seed, `docs/manual/E2E_CHECKLIST.md`, SRS §13.1 acceptance.

---

## Rules for the next AI session

1. Read `context.md` → then Phase N section in `docs/DEVELOPMENT_PLAN.md` → then relevant SDD sections.
2. Execute **only the next incomplete phase** unless the user asks otherwise.
3. After finishing a phase: run that phase’s **Exit Checklist**, then **update this file** (status table, “Last completed”, “What Phase N delivered”, “Next phase”).
4. Do not put SQL in UI or pairing logic in DAOs.
5. Do not commit secrets (`config/application.properties`).
6. Prefer extending `AppContext` when wiring new services/DAOs.
7. Keep packages under `com.chess.tournament.{domain,dao,service,ui,bootstrap,config,exception}`.

---

## Known environment notes

- Host JDK may be newer than 17 (e.g. 25); POM uses `maven.compiler.release=17`.
- Native PostgreSQL client packages may be incomplete; Docker Postgres is the verified path.
- Port 5432 used by `ctms-postgres`; stop/start with `docker start|stop ctms-postgres`.
- Integration tests require Docker; Testcontainers 1.21.4+ for Docker Engine 29+.

---

## Changelog

| Date | Change |
|------|--------|
| 2026-09-16 | Phase 4 completed. Tournament/Enrollment services, validators, UI, integration tests. Next: Phase 5. |
| 2026-09-16 | Phase 3 completed. PlayerService + Players UI (list/form), AppContext wiring, unit tests, manual checklist. Next: Phase 4. |
| 2026-09-16 | Phase 2 completed. Domain model, JDBC DAOs, UnitOfWork, integration tests. Next: Phase 3. |
| 2026-09-16 | Phase 1 completed. Created Maven/JavaFX/Flyway foundation, context.md handoff. Next: Phase 2. |
| 2026-09-16 | Pushed to GitHub: https://github.com/arvinalmeida192/chess-tournament-manager (public, branch `main`). |
