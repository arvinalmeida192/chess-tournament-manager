# Chess Tournament Management System

Offline desktop application for tournament hosts. Built with Java 17, JavaFX, JDBC, Maven, and PostgreSQL.

## Prerequisites

- JDK 17 or newer (`java -version`)
- Maven 3.8+ (`mvn -version`)
- PostgreSQL 14+ (local install or Docker)

## Database setup

Create a database and user (example with Docker):

```bash
docker run -d --name ctms-postgres \
  -e POSTGRES_DB=chess_tournament \
  -e POSTGRES_USER=ctms \
  -e POSTGRES_PASSWORD=changeme \
  -p 5432:5432 \
  postgres:16-alpine
```

Or with a local PostgreSQL server:

```bash
createdb chess_tournament
# create role ctms with password and grant privileges as needed
```

## Configuration

1. Copy the example config:

```bash
cp config/application.properties.example config/application.properties
```

2. Edit credentials if needed. Environment variables override the file:

| Variable | Purpose |
|----------|---------|
| `CTMS_DB_URL` | JDBC URL (e.g. `jdbc:postgresql://localhost:5432/chess_tournament`) |
| `CTMS_DB_USER` | Database user |
| `CTMS_DB_PASSWORD` | Database password |

`config/application.properties` is gitignored — do not commit secrets.

## Migrate schema

Flyway runs automatically when the application starts. You can also migrate from the CLI:

```bash
export CTMS_DB_URL=jdbc:postgresql://localhost:5432/chess_tournament
export CTMS_DB_USER=ctms
export CTMS_DB_PASSWORD=changeme
mvn flyway:migrate
```

## Optional demo data

Load 8 players and a completed Swiss showcase tournament:

```bash
docker exec -i ctms-postgres psql -U ctms -d chess_tournament \
  < src/main/resources/db/seed/demo.sql
```

The script is idempotent (skips if `Demo Swiss Showcase` already exists).

## Run the application

```bash
cp config/application.properties.example config/application.properties
mvn javafx:run
```

On success, the home screen shows **Connected to database**. Use the toolbar or menus:

- **Players** — global player registry
- **Tournaments** — create, enroll, run, finalize
- **Settings → Test Database Connection** — re-check JDBC

### Host workflow

1. Add players (or load demo seed).
2. Create a tournament (Round Robin / Knockout / Swiss).
3. Enroll players → **Start Tournament**.
4. **Pairings** → generate & publish the current round.
5. **Enter Results** → save → **Complete Round** (applies points + Elo).
6. Repeat until all planned rounds are complete.
7. **Leaderboard** → optionally **Apply Qualification** → **Finalize**.

Invalid actions stay disabled (or return clear validation errors).

## Architecture

Layered desktop app (no Spring): **UI → Service → DAO → PostgreSQL**.

See the layer diagram and pairing/rating design in [docs/SDD.md](docs/SDD.md). Requirements live in [docs/SRS.md](docs/SRS.md). Phase plan: [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md).

## Tests

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up a disposable PostgreSQL 16 instance. Docker must be running locally.

```bash
mvn test
```

If Docker 29+ reports an API version mismatch, the project ships `src/test/resources/docker-java.properties` with `api.version=1.44` as a workaround. Testcontainers 1.21.4+ is required for recent Docker Engine versions.

## Troubleshooting

| Symptom | What to try |
|---------|-------------|
| `Database connection failed` on home | Start `ctms-postgres` (`docker start ctms-postgres`); verify `CTMS_DB_*` or `config/application.properties` |
| Port 5432 already in use | Stop the other Postgres, or map Docker to another host port and update the JDBC URL |
| Flyway / schema errors | Ensure empty DB or compatible history; do not mix hand-edited schema with migrations |
| JavaFX fails to launch | Need a display (local desktop or X11); headless CI can still run `mvn test` |
| Testcontainers API version error | Confirm `docker-java.properties` is on the test classpath; upgrade Testcontainers if needed |

## Manual checklists

- [E2E release checklist](docs/manual/E2E_CHECKLIST.md) (SRS §13.1)
- [docs/manual/phase3-players.md](docs/manual/phase3-players.md)
- [docs/manual/phase4-tournaments.md](docs/manual/phase4-tournaments.md)
- [docs/manual/phase5-round-robin.md](docs/manual/phase5-round-robin.md)
- [docs/manual/phase6-knockout.md](docs/manual/phase6-knockout.md)
- [docs/manual/phase7-swiss.md](docs/manual/phase7-swiss.md)
- [docs/manual/phase8-results.md](docs/manual/phase8-results.md)
- [docs/manual/phase9-leaderboard.md](docs/manual/phase9-leaderboard.md)
- [docs/manual/phase10-e2e.md](docs/manual/phase10-e2e.md)

## Documentation

- [SRS](docs/SRS.md) — requirements
- [SDD](docs/SDD.md) — design
- [Development plan](docs/DEVELOPMENT_PLAN.md) — 10 phases
- [context.md](context.md) — handoff status
