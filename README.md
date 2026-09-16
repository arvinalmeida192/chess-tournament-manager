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

## Run the application

```bash
cp config/application.properties.example config/application.properties
mvn javafx:run
```

On success, the main window shows **Connected to database**. Use **Players → Manage Players** and **Tournaments → Manage Tournaments**.

Manual checklists:
- [docs/manual/phase3-players.md](docs/manual/phase3-players.md)
- [docs/manual/phase4-tournaments.md](docs/manual/phase4-tournaments.md)
- [docs/manual/phase5-round-robin.md](docs/manual/phase5-round-robin.md)

## Tests

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up a disposable PostgreSQL 16 instance. Docker must be running locally.

```bash
mvn test
```

If Docker 29+ reports an API version mismatch, the project ships `src/test/resources/docker-java.properties` with `api.version=1.44` as a workaround. Testcontainers 1.21.4+ is required for recent Docker Engine versions.

## Documentation

- [SRS](docs/SRS.md) — requirements
- [SDD](docs/SDD.md) — design
- [Development plan](docs/DEVELOPMENT_PLAN.md) — 10 phases
- [context.md](context.md) — handoff status for continuing development
