# Software Requirements Specification (SRS)

## Chess Tournament Management System

| Document | Software Requirements Specification |
|----------|-------------------------------------|
| Version | 1.0 |
| Status | Draft for implementation |
| Target stack | Java 17+, JavaFX, JDBC, Maven, PostgreSQL 14+ |
| Primary user | Tournament host (single operator) |

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Overall Description](#2-overall-description)
3. [System Context and Interfaces](#3-system-context-and-interfaces)
4. [User Roles and Permissions](#4-user-roles-and-permissions)
5. [Functional Requirements](#5-functional-requirements)
6. [Business Rules](#6-business-rules)
7. [Data Requirements](#7-data-requirements)
8. [Non-Functional Requirements](#8-non-functional-requirements)
9. [Use Cases](#9-use-cases)
10. [User Interface Requirements](#10-user-interface-requirements)
11. [Reporting and Outputs](#11-reporting-and-outputs)
12. [Constraints and Assumptions](#12-constraints-and-assumptions)
13. [Acceptance Criteria](#13-acceptance-criteria)
14. [Requirements Traceability Matrix](#14-requirements-traceability-matrix)
15. [Glossary](#15-glossary)

---

## 1. Introduction

### 1.1 Purpose

This document specifies the complete functional and non-functional requirements for the **Chess Tournament Management System** (CTMS): an offline desktop application used by a tournament host to create tournaments, register players, generate pairings, record results, update ratings and leaderboards, and determine qualification through the end of the event.

Implementers, testers, and reviewers shall use this SRS as the authoritative definition of *what* the system must do. The companion **SDD.md** defines *how* it is built.

### 1.2 Scope

**In scope:**

- Persistent storage of players, tournaments, enrollments, rounds, and games in PostgreSQL.
- Tournament formats: Round Robin, Knockout, Swiss/Dutch.
- Automatic first-round and subsequent-round pairings (format-specific).
- Manual result entry by the host (Win, Draw, Loss, Bye).
- Automatic tournament points, Elo-style rating updates, leaderboard ordering, and qualification marking.
- JavaFX GUI for all host operations.
- Single-user operation on one machine (no multi-user concurrency model required beyond safe local DB access).

**Out of scope (explicit):**

- Player self-registration or public web portal.
- Online multiplayer or live game integration.
- FIDE arbiter certification, tie-break rule catalogs beyond those defined in this document.
- Payment, venue, or scheduling outside round/game pairing.
- Mobile clients.
- Multi-host synchronization or cloud deployment (unless added in a future version).

### 1.3 Definitions, Acronyms, Abbreviations

See [Section 15 Glossary](#15-glossary).

### 1.4 References

- FIDE Handbook (pairing and rating concepts) — used as informal guidance only; this SRS defines the implemented subset.
- PostgreSQL documentation — JDBC connectivity and SQL types.
- JavaFX documentation — desktop UI.

### 1.5 Document Conventions

- Requirements use IDs: `FR-*` (functional), `BR-*` (business rule), `NFR-*` (non-functional), `UI-*` (interface), `DATA-*` (data).
- Priority: **Must** (release blocker), **Should** (important), **Could** (nice-to-have).
- “System” means CTMS unless stated otherwise.

---

## 2. Overall Description

### 2.1 Product Perspective

CTMS is a standalone Java desktop application with a layered architecture (Presentation → Service → Data Access → PostgreSQL). It replaces manual spreadsheets and ad-hoc pairing for local chess events.

```text
+------------------+     JDBC      +------------------+
|  JavaFX UI       | <-----------> |  PostgreSQL      |
|  (Host)          |               |  (Local/Network) |
+------------------+               +------------------+
         |
         v
+------------------+
|  Service Layer   |  Pairing, scoring, Elo, qualification
+------------------+
         |
         v
+------------------+
|  Data Access     |  Repositories, transactions
+------------------+
```

### 2.2 Product Functions (Summary)

| Area | Capability |
|------|------------|
| Players | CRUD global player registry; enroll in tournament |
| Tournaments | Create, configure, lifecycle (Draft → Active → Completed) |
| Pairing | Format-specific round pairings; display boards |
| Results | Enter outcomes; validate completeness per round |
| Scoring | 1 / 0.5 / 0 points; bye handling |
| Ratings | Post-round Elo update per game |
| Standings | Leaderboard after each round; final board |
| Qualification | Mark top N after configured rounds |

### 2.3 User Characteristics

The host is assumed to:

- Understand basic chess tournament concepts (rounds, pairings, byes).
- Operate a desktop OS and install Java and PostgreSQL (or use provided setup scripts).
- Enter results accurately; the system validates but does not adjudicate disputes.

No chess expertise is required to install the software; operational training is limited to the GUI workflow described in use cases.

### 2.4 Operating Environment

| Component | Requirement |
|-----------|-------------|
| OS | Linux, Windows, or macOS supported by Java 17 LTS and JavaFX |
| JRE/JDK | 17 or 21 (project standard to be fixed in Phase 1) |
| Database | PostgreSQL 14+ reachable via JDBC URL |
| Display | Minimum 1280×720; responsive layouts within desktop window |
| Network | Optional; DB may be localhost only |

### 2.5 Design and Implementation Constraints

- **Must** use Java, JavaFX, JDBC, Maven, PostgreSQL as stated in the project charter.
- **Must** use layered architecture; UI must not contain SQL or pairing algorithms.
- **Must** persist all tournament state in PostgreSQL (no file-based primary store).
- **Should** use parameterized SQL only (no string-concatenated queries).

### 2.6 Dependencies

- PostgreSQL server running and database created before first launch.
- JDBC driver on classpath (Maven dependency).
- JavaFX modules configured in Maven (javafx-maven-plugin or module path as per SDD).

---

## 3. System Context and Interfaces

### 3.1 External Systems

| Interface | Direction | Description |
|-----------|-----------|-------------|
| PostgreSQL | Bidirectional | All persistent entities |
| OS filesystem | Read | Optional `application.properties` / `.env` for DB credentials (not committed) |
| OS clipboard | Optional | Copy leaderboard text (Could) |

No other external APIs are required for v1.

### 3.2 Application Configuration Interface

The system **Must** read database connection settings from a configuration source (priority order to be implemented in SDD):

1. Environment variables: `CTMS_DB_URL`, `CTMS_DB_USER`, `CTMS_DB_PASSWORD`
2. `config/application.properties` on classpath or beside JAR

**FR-CFG-001** (Must): On startup, if DB connection fails, show a clear error dialog with connection hint; do not crash silently.

**FR-CFG-002** (Must): Support schema initialization or migration script execution documented in README (manual or on first run).

### 3.3 Data Import/Export (Could — v1.1)

Not required for v1. **Could** export final leaderboard to CSV in a later release.

---

## 4. User Roles and Permissions

| Role | Description | Access |
|------|-------------|--------|
| Host | Tournament organizer | Full access to all features |

**FR-SEC-001** (Must): No login required for v1 (single trusted operator machine).

**NFR-SEC-001** (Should): DB credentials must not be hard-coded in source control.

---

## 5. Functional Requirements

### 5.1 Player Registry

**FR-PLR-001** (Must): Host can create a player with: full name (required), age (optional integer ≥ 0), country (optional text), initial rating (required integer, default 1500 if host leaves blank per BR-RAT-001).

**FR-PLR-002** (Must): Each player receives a system-wide unique identifier (`player_id`, surrogate key).

**FR-PLR-003** (Must): Host can view list of all registered players (global registry).

**FR-PLR-004** (Must): Host can edit player demographic fields and **global** rating before any active tournament enrollment affects locked fields (see FR-TNP-005).

**FR-PLR-005** (Should): Host can deactivate or soft-delete a player not enrolled in an active tournament.

**FR-PLR-006** (Must): Player name uniqueness is **not** required globally (homonyms allowed); disambiguation via ID in UI.

### 5.2 Tournament Management

**FR-TNM-001** (Must): Host can create a tournament with:

- Name (required, 1–200 chars)
- Type: `ROUND_ROBIN` | `KNOCKOUT` | `SWISS`
- Number of rounds (required, integer ≥ 1)
- Number of qualifiers (required, integer ≥ 0)

**FR-TNM-002** (Must): System stores tournament status: `DRAFT`, `ACTIVE`, `COMPLETED`, `CANCELLED`.

**FR-TNM-003** (Must): Host can only start a tournament when enrollment count meets format minimum (see BR-FMT-* ).

**FR-TNM-004** (Must): Host can view list of tournaments filtered by status.

**FR-TNM-005** (Must): Host can open a tournament “dashboard” showing config, enrolled count, current round, and actions (register players, generate pairings, enter results, view leaderboard).

**FR-TNM-006** (Must): When all configured rounds are complete and results validated, host can finalize tournament → status `COMPLETED`, freeze pairings and recalc final standings.

**FR-TNM-007** (Should): Host can cancel a `DRAFT` tournament; `ACTIVE` cancellation requires confirmation and marks `CANCELLED` without deleting history.

### 5.3 Tournament Enrollment (Tournament Player)

**FR-TNP-001** (Must): Host enrolls existing global players into a tournament (`DRAFT` or `ACTIVE` before round 1 pairings generated — see BR-ENR-001).

**FR-TNP-002** (Must): For each enrollment, system stores tournament-specific:

- Starting rating (copied from global rating at enrollment time)
- Current rating (updated after each round)
- Tournament points (default 0)
- Wins, draws, losses, games played (integers, default 0)
- Qualification status: `PENDING` | `QUALIFIED` | `ELIMINATED` | `NOT_APPLICABLE`

**FR-TNP-003** (Must): Same global player may enroll in multiple tournaments; statistics are isolated per `tournament_player` row.

**FR-TNP-004** (Must): Host can remove enrollment only before first round pairings exist.

**FR-TNP-005** (Must): After tournament start (first pairing generated), enrollment list is locked.

### 5.4 Round and Game Management

**FR-RND-001** (Must): System creates `round` records numbered 1..N sequentially per tournament.

**FR-RND-002** (Must): A round has status: `PENDING_PAIRINGS`, `PAIRINGS_PUBLISHED`, `IN_PROGRESS`, `COMPLETED`.

**FR-RND-003** (Must): Each game belongs to one round and records: board number, white player, black player (nullable for bye), result code, points awarded to white and black.

**FR-RND-004** (Must): Host cannot generate round `k+1` pairings until round `k` is `COMPLETED`.

**FR-RND-005** (Must): System prevents duplicate pairing of the same two players in Swiss and Round Robin where rules forbid rematches (see format sections).

### 5.5 First-Round Pairing

**FR-PAIR-001** (Must): Host triggers “Generate Round 1 pairings” for an `ACTIVE` tournament with valid enrollment.

**FR-PAIR-002** (Must): Round Robin round 1: pair according to fixed schedule algorithm (circle method); round 1 is deterministic given player ordering seed.

**FR-PAIR-003** (Must): Knockout round 1: pair players into bracket slots; if player count not power of two, assign byes per BR-KO-002.

**FR-PAIR-004** (Must): Swiss round 1: default **random shuffle** pairing within halves OR **rating split** (top half vs bottom half) — host selects method in tournament settings (`SWISS_FIRST_ROUND`: `RANDOM` | `RATING_SPLIT`).

**FR-PAIR-005** (Must): Display pairings grouped by board number: “Board B: White vs Black”.

**FR-PAIR-006** (Must): Odd player count: exactly one bye per round in Swiss/Round Robin (unless format specifies otherwise); bye awards 1 point (BR-SCR-003).

### 5.6 Subsequent-Round Pairing

**FR-PAIR-010** (Must): Round Robin: generate next round pairings from schedule; no rematch before schedule dictates.

**FR-PAIR-011** (Must): Knockout: auto-create next round games from winners of previous round; losers marked `ELIMINATED` for knockout path.

**FR-PAIR-012** (Must): Swiss: pair players in score groups; prefer same score; avoid previous opponents; improve color balance (BR-SWI-* ).

**FR-PAIR-013** (Should): If no valid pairing exists without rematch, system pairs with rematch and flags game metadata `rematch=true` for host review.

**FR-PAIR-014** (Must): Knockout does not use Swiss pairing; only winner advancement.

### 5.7 Result Entry

**FR-RES-001** (Must): Host enters result per game: `WHITE_WIN`, `BLACK_WIN`, `DRAW`, `BYE` (bye typically pre-filled).

**FR-RES-002** (Must): For `WHITE_WIN`: white +1.0, black +0.0 tournament points for that game; increment W/L counters accordingly.

**FR-RES-003** (Must): For `BLACK_WIN`: black +1.0, white +0.0.

**FR-RES-004** (Must): For `DRAW`: both +0.5; increment draw counters.

**FR-RES-005** (Must): Host can save results incrementally; system validates no duplicate result submission overwriting without confirmation.

**FR-RES-006** (Must): “Complete round” action requires every game in the round to have a valid result.

**FR-RES-007** (Must): On round completion, trigger rating update (FR-RAT-*), standings refresh (FR-LDB-*), and unlock next round pairing generation.

**FR-RES-008** (Must): Prevent editing results of a round if a later round already has pairings generated (unless host uses “Revert round” admin action — Could for v1: **Must** block edits once next round exists).

### 5.8 Rating Calculation

**FR-RAT-001** (Must): After each completed round, for each non-bye game, update both players’ **tournament current rating** and **global player rating** using Elo with K-factor from BR-RAT-002.

**FR-RAT-002** (Must): Expected score: `E = 1 / (1 + 10^((R_opp - R_player) / 400))`.

**FR-RAT-003** (Must): Actual score S: win 1.0, draw 0.5, loss 0.0.

**FR-RAT-004** (Must): `R_new = R_old + K * (S - E)` rounded to nearest integer.

**FR-RAT-005** (Must): Store per-game rating delta optional in `game` table for audit (Should).

**FR-RAT-006** (Must): Bye games do not alter rating.

### 5.9 Leaderboard

**FR-LDB-001** (Must): After each completed round, display leaderboard for that tournament.

**FR-LDB-002** (Must): Columns: Rank, Player name, Current rating, Points, Wins, Draws, Losses.

**FR-LDB-003** (Must): Sort primary: tournament points descending; tie-breakers per BR-TIE-001.

**FR-LDB-004** (Must): Header shows round number: “Round R Leaderboard”.

**FR-LDB-005** (Must): Final leaderboard adds: starting rating, final rating, qualification status.

### 5.10 Qualification

**FR-QLF-001** (Must): `qualifiers_count` from tournament config defines how many top players receive `QUALIFIED` after qualification trigger.

**FR-QLF-002** (Must): Qualification evaluation runs when: (a) host explicitly clicks “Apply qualification”, or (b) tournament finalized after last round — **Must** implement both; auto on finalize.

**FR-QLF-003** (Must): Players ranked 1..Q marked `QUALIFIED`; others `ELIMINATED` (if Q < enrollment count). If Q = 0, all `NOT_APPLICABLE`.

**FR-QLF-004** (Must): Knockout: eliminated players already `ELIMINATED` on loss; qualification count may mark top Q by final placement for hybrid events — for pure knockout, qualifiers = 1 (champion) unless host sets Q > 1 for “top N finish” tracking (BR-KO-003).

### 5.11 Format-Specific Requirements

#### 5.11.1 Round Robin

**FR-RR-001** (Must): Minimum 3 players.

**FR-RR-002** (Must): Default rounds = N-1 for even N, N for odd N with bye rotation — host-entered round count **Must** match schedule length or system warns (BR-RR-001).

**FR-RR-003** (Must): Total games = N×(N-1)/2 over full schedule.

**FR-RR-004** (Must): Each player plays each other exactly once across full schedule.

#### 5.11.2 Knockout

**FR-KO-001** (Must): Minimum 2 players.

**FR-KO-002** (Must): Single elimination; one loss eliminates from main bracket.

**FR-KO-003** (Must): Display bracket view (tree or round-grouped list).

**FR-KO-004** (Must): Round count = ceil(log2(N)) adjusted for byes in first round.

#### 5.11.3 Swiss/Dutch

**FR-SW-001** (Must): Minimum 4 players recommended; minimum 2 allowed with warning.

**FR-SW-002** (Must): Host-configured fixed number of rounds (independent of log2(N)).

**FR-SW-003** (Must): No player plays same opponent twice if alternative exists.

**FR-SW-004** (Should): Track color (white/black) per player across rounds for balance.

---

## 6. Business Rules

### 6.1 Scoring

**BR-SCR-001**: Win = 1.0 point, Draw = 0.5, Loss = 0.0.

**BR-SCR-002**: Tournament points are cumulative sum of game points.

**BR-SCR-003**: Bye = 1.0 point for receiving player; counts as win for W-L record (optional: bye as win without rating change — **Must** not count as rated game).

### 6.2 Rating

**BR-RAT-001**: Default initial rating 1500 if not specified.

**BR-RAT-002**: K-factor = 32 for all players (v1 constant; store in config for future).

**BR-RAT-003**: Rating used for Elo is **current rating at start of round** before processing that round’s games (batch per round).

### 6.3 Tie-breaking (Leaderboard)

**BR-TIE-001** Order:

1. Total tournament points (desc)
2. Head-to-head points among tied (if exactly two tied)
3. Higher current rating
4. Alphabetical by name (stable)

**BR-TIE-002**: For more than two tied on points, skip head-to-head and use rating then name.

### 6.4 Enrollment

**BR-ENR-001**: Enrollment allowed in `DRAFT` anytime; in `ACTIVE` only before round 1 pairings.

### 6.5 Round Robin schedule

**BR-RR-001**: If host sets `rounds` ≠ computed schedule rounds, system **Must** show validation error before start.

### 6.6 Knockout byes

**BR-KO-001**: Byes in round 1 assigned to lowest-rated players (or random if equal — document in SDD).

**BR-KO-002**: Bye in knockout advances player automatically to next round.

**BR-KO-003**: For knockout-only events, `qualifiers_count` typically 1; system **Should** warn if Q > 1.

### 6.7 Swiss pairing

**BR-SWI-001**: Sort players by points desc, then rating desc.

**BR-SWI-002**: Pair top half against bottom half within score group when using Dutch approach.

**BR-SWI-003**: Avoid pairing players who already met; swap adjacent pairs if needed.

**BR-SWI-004**: Color: alternate where possible; player with excess white gets black.

---

## 7. Data Requirements

### 7.1 Entity Summary

| Entity | Purpose |
|--------|---------|
| `player` | Global identity and rating |
| `tournament` | Event configuration and status |
| `tournament_player` | Enrollment and per-event stats |
| `round` | Round index and status |
| `game` | Match pairing and result |

### 7.2 Retention

**DATA-001** (Must): No hard delete of completed tournaments; soft delete flag optional.

**DATA-002** (Must): All timestamps stored in UTC (`timestamptz`).

**DATA-003** (Must): Referential integrity: games reference valid tournament players of same tournament.

### 7.3 Detailed attributes

See SDD.md Section 4 for physical schema. SRS logical model:

**Player**: id, name, age, country, global_rating, active, created_at, updated_at.

**Tournament**: id, name, type, rounds_planned, qualifiers_count, swiss_first_round_method, status, created_at, started_at, completed_at.

**Tournament_player**: id, tournament_id, player_id, start_rating, current_rating, points, wins, draws, losses, games_played, qualification_status, color_balance (integer, + = extra white).

**Round**: id, tournament_id, round_number, status, paired_at, completed_at.

**Game**: id, round_id, board_number, white_tp_id, black_tp_id, result, white_score, black_score, rematch_flag, white_rating_delta, black_rating_delta.

---

## 8. Non-Functional Requirements

### 8.1 Performance

**NFR-PER-001**: Generate Swiss pairings for 200 players in < 5 seconds on typical host laptop.

**NFR-PER-002**: Leaderboard refresh < 1 second for 200 players.

### 8.2 Reliability

**NFR-REL-001**: Result submission and round completion **Must** be transactional (all-or-nothing DB commit).

**NFR-REL-002**: On partial failure, show error and roll back; no inconsistent points.

### 8.3 Usability

**NFR-USE-001**: Common host workflow (open tournament → enter results → next pairings) ≤ 5 clicks from dashboard.

**NFR-USE-002**: All destructive actions require confirmation dialog.

### 8.4 Maintainability

**NFR-MNT-001**: Layer boundaries enforced; services unit-testable without JavaFX.

**NFR-MNT-002**: SQL migrations versioned in `src/main/resources/db/migration/`.

### 8.5 Portability

**NFR-PRT-001**: Same JAR runs on major desktop OS with appropriate JavaFX native deps.

### 8.6 Logging

**NFR-LOG-001**: Log errors and pairing decisions to SLF4J (INFO for round complete, WARN for rematch).

---

## 9. Use Cases

### UC-01 Create Tournament

| Field | Detail |
|-------|--------|
| Actor | Host |
| Precondition | DB available |
| Main flow | 1. Host opens Create Tournament. 2. Enters name, type, rounds, qualifiers, Swiss first-round option if Swiss. 3. Saves. 4. System creates `DRAFT` tournament. |
| Postcondition | Tournament persisted |
| Exceptions | Validation failure → inline messages |

### UC-02 Register Players in Tournament

| Field | Detail |
|-------|--------|
| Precondition | Tournament `DRAFT` or `ACTIVE` before R1 pairings |
| Main flow | Select global players → enroll → snapshot start ratings |
| Postcondition | `tournament_player` rows created |

### UC-03 Start Tournament

| Field | Detail |
|-------|--------|
| Precondition | Minimum players met; rounds valid for format |
| Main flow | Host clicks Start → status `ACTIVE`, round 1 `PENDING_PAIRINGS` |
| Postcondition | Enrollment locked after first pairing |

### UC-04 Generate Pairings

| Field | Detail |
|-------|--------|
| Main flow | Host clicks Generate → service computes pairings → games inserted → round `PAIRINGS_PUBLISHED` |
| Alternate | Validation error (incomplete previous round) |

### UC-05 Enter Results and Complete Round

| Field | Detail |
|-------|--------|
| Main flow | Enter each game result → Complete Round → ratings/points updated → round `COMPLETED` |
| Postcondition | Leaderboard available; next round unlocks |

### UC-06 View Leaderboard

| Field | Detail |
|-------|--------|
| Main flow | Select round or “current” → table sorted with tie-breaks |

### UC-07 Finalize and Qualification

| Field | Detail |
|-------|--------|
| Precondition | All rounds completed |
| Main flow | Finalize → qualification statuses set → final leaderboard |

---

## 10. User Interface Requirements

### 10.1 Screen Inventory

| ID | Screen | Purpose |
|----|--------|---------|
| UI-S01 | Main shell | Navigation menu / toolbar |
| UI-S02 | Player registry | CRUD players |
| UI-S03 | Tournament list | Filter, create, open |
| UI-S04 | Tournament wizard/create | Form |
| UI-S05 | Tournament dashboard | Status, actions, enrolled count |
| UI-S06 | Enrollment | Add/remove players |
| UI-S07 | Pairings view | Boards for current round |
| UI-S08 | Result entry | Grid of games with result controls |
| UI-S09 | Leaderboard | Table + round selector |
| UI-S10 | Knockout bracket | Bracket visualization |
| UI-S11 | Final report | Final leaderboard + export placeholder |

### 10.2 UI Behavior

**UI-001** (Must): Disable actions invalid for current state (e.g., Generate R2 if R1 incomplete).

**UI-002** (Must): Show tournament type and round progress prominently.

**UI-003** (Must): Result entry: dropdown or radio per game — White Win / Draw / Black Win.

**UI-004** (Should): Keyboard navigation in result grid (Tab between games).

**UI-005** (Must): Error messages human-readable; include corrective hint.

---

## 11. Reporting and Outputs

**FR-RPT-001** (Must): On-screen final leaderboard per FR-LDB-005.

**FR-RPT-002** (Should): Print-friendly summary (JavaFX print or text area).

**FR-RPT-003** (Must): Pairing sheet printable layout for current round.

---

## 12. Constraints and Assumptions

### 12.1 Assumptions

- Host runs PostgreSQL locally or on LAN with stable connectivity during event.
- One tournament is actively managed at a time (UI may still list many).
- English UI for v1.

### 12.2 Constraints

- Offline-first: no dependency on internet at runtime.
- Algorithms implement documented subset of FIDE Swiss (simplified Dutch).

---

## 13. Acceptance Criteria

### 13.1 Release checklist

1. Create Round Robin tournament with 6 players, 5 rounds, complete all results — standings match manual Excel verification.
2. Create Knockout with 8 players — bracket and winner match manual trace.
3. Create Swiss 5 rounds, 16 players — no duplicate pairings unless forced; points and ratings update correctly.
4. Qualification top 4 applied — exactly four `QUALIFIED`.
5. DB restart — all data intact.
6. Invalid actions blocked with clear messages.

### 13.2 Test data

Hosts create players and tournaments through the UI. No bundled demo seed is required.

---

## 14. Requirements Traceability Matrix

| Requirement | Phase (Plan) | Test type |
|-------------|--------------|-----------|
| FR-PLR-* | Phase 3 | Unit + UI |
| FR-TNM-*, FR-TNP-* | Phase 4 | Integration |
| FR-RR-*, FR-PAIR-002,010 | Phase 5 | Unit |
| FR-KO-* | Phase 6 | Unit + UI |
| FR-SW-*, FR-PAIR-012 | Phase 7 | Unit |
| FR-RES-*, FR-RAT-* | Phase 8 | Integration |
| FR-LDB-*, FR-QLF-* | Phase 9 | Unit |
| UI-* | Phase 10 | Manual E2E |
| NFR-REL-001 | Phase 8 | Integration |

---

## 15. Glossary

| Term | Definition |
|------|------------|
| Bye | Round without paired opponent; receiver gets 1 point |
| Board | Display order of a game in a round |
| Dutch system | Swiss pairing pairing score groups in halves |
| Enrollment | Linking a global player to a tournament |
| Elo | Rating system used post-game |
| Host | Human operator of CTMS |
| Pairing | Assignment of opponents for a round |
| Round | Discrete stage of tournament play |
| Swiss | Multiple rounds, pair by score |
| Tournament points | Scoring points within event (not rating) |

---

*End of SRS v1.0*
