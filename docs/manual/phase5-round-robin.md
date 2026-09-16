# Phase 5 — Round Robin Pairings manual checklist

## Prerequisites

- Players exist (Phase 3): at least 4–6
- Phase 4: create a Round Robin tournament with correct rounds (`N-1` even / `N` odd)
- `mvn javafx:run`

## Checklist

1. Create RR tournament for **4 players**, rounds **3**, enroll 4, **Start**.
2. Dashboard → **Generate Pairings** opens pairings dialog; Round 1 is pending.
3. Click **Generate & Publish** → table shows 2 boards (White | Black), result `PENDING`.
4. Enrollment is locked after publish (re-open Manage Enrollment).
5. Close pairings, reopen → published boards still visible; Generate disabled for round 1.
6. Create RR for **5 players**, rounds **5**, enroll 5, start, publish round 1 → 2 games + 1 `BYE` row.
7. Knockout: Generate Pairings opens the bracket view (Phase 6). Swiss uses the same pairings dialog (Phase 7).

## SRS coverage

- FR-RR-003 / FR-RR-004 schedule completeness (verified in unit/integration tests)
- FR-PAIR-002, FR-PAIR-010 Round Robin pairings
- FR-PAIR-005 board display
- FR-PAIR-006 bye for odd N
