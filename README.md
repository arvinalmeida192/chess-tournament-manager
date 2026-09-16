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

On success, the main window shows **Connected to database**.

## Tests

```bash
mvn test
```

## Documentation

- [SRS](docs/SRS.md) — requirements
- [SDD](docs/SDD.md) — design
- [Development plan](docs/DEVELOPMENT_PLAN.md) — 10 phases
- [context.md](context.md) — handoff status for continuing development
