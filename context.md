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
| **Last completed phase** | **Phase 1 — Foundation & Database Bootstrap** |
| **Next phase to execute** | **Phase 2 — Domain Model & Data Access Layer** |
| **Build health** | `mvn test` passes; Flyway V1 applied; AppContext + `SELECT 1` smoke OK |
| **DB for local dev** | Docker container `ctms-postgres` (Postgres 16), port `5432` |
| **DB credentials** | user `ctms` / password `changeme` / db `chess_tournament` (see `config/application.properties.example`) |

---

## Phase checklist

| Phase | Title | Status |
|-------|--------|--------|
| 1 | Foundation & database bootstrap | **DONE** |
| 2 | Domain model & data access layer | **NOT STARTED** |
| 3 | Global player registry | **NOT STARTED** |
| 4 | Tournaments & enrollment | **NOT STARTED** |
| 5 | Round Robin pairings | **NOT STARTED** |
| 6 | Knockout pairings & elimination | **NOT STARTED** |
| 7 | Swiss/Dutch pairings | **NOT STARTED** |
| 8 | Results, scoring & Elo ratings | **NOT STARTED** |
| 9 | Leaderboards & qualification | **NOT STARTED** |
| 10 | End-to-end UI & release hardening | **NOT STARTED** |

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
- [x] `mvn test` — `DatabaseConfigTest` (4 tests) passes
- [x] `mvn flyway:migrate` creates tables: `player`, `tournament`, `tournament_player`, `round`, `game`, `flyway_schema_history`
- [x] Headless smoke: `AppContext.initialize()` + `SELECT 1` returns 1
- [x] Credentials not hard-coded in committed sources (example file only; real props gitignored)
- [ ] Full GUI `mvn javafx:run` — may need a display; not required if headless CI. Main window title is “Chess Tournament Manager”; label shows “Connected to database” on success.

### Design notes from Phase 1 (keep)

- Env vars `CTMS_DB_URL`, `CTMS_DB_USER`, `CTMS_DB_PASSWORD` override `config/application.properties`.
- `DatabaseConfig.loadFromClasspath` / `fromProperties` **do not** apply env overrides (for reliable unit tests).
- `AppContext` is a singleton composition root; Flyway runs on `initialize()`.
- Controllers must obtain `DataSource` via `AppContext.get().getDataSource()` (until DI expands in later phases).

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

## What is NOT done (Phases 2–10)

Do **not** implement these until the matching phase. Full task lists live in `docs/DEVELOPMENT_PLAN.md`.

### Phase 2 — next (start here)

Goal: domain enums/entities, JDBC DAOs for all tables, `UnitOfWork`, integration tests.

Must create (per plan):

- Enums: `TournamentType`, `TournamentStatus`, `RoundStatus`, `GameResult`, `QualificationStatus`, `SwissFirstRoundMethod`
- Entities: `Player`, `Tournament`, `TournamentPlayer`, `Round`, `Game` (`BigDecimal` for points/scores)
- Exceptions: `DomainException`, `ValidationException`, `NotFoundException`, `DataAccessException`
- `UnitOfWork` + `JdbcUnitOfWork`
- DAOs + `Jdbc*` impls: Player, Tournament, TournamentPlayer, Round, Game (methods in SDD §6.3)
- Tests: `PlayerDaoIntegrationTest`, `TournamentDaoIntegrationTest`, `findPreviousPairings` coverage
- Optional: Testcontainers PostgreSQL (preferred) or local DB + `application-test.properties`

**Out of scope for Phase 2:** services, pairing, JavaFX forms beyond existing shell.

### Phase 3

`PlayerService` + player list/form FXML + wire MainController menu “Players”.

### Phase 4

`TournamentService`, `EnrollmentService`, validators, tournament list/create/dashboard/enrollment UI.

### Phase 5

Round Robin schedule generator + pairing strategy + `PairingService` publish.

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

---

## Changelog

| Date | Change |
|------|--------|
| 2026-09-16 | Phase 1 completed. Created Maven/JavaFX/Flyway foundation, context.md handoff. Next: Phase 2. |
| 2026-09-16 | Pushed to GitHub: https://github.com/arvinalmeida192/chess-tournament-manager (public, branch `main`). |
