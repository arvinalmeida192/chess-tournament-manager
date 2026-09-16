# Phase 7 — Swiss/Dutch Pairings manual checklist

## Prerequisites

- Players exist (Phase 3): at least 8 with varied ratings
- `mvn javafx:run`

## Checklist

1. Create **Swiss** tournament for **8 players**, rounds **3**, qualifiers **2**, first round **RATING_SPLIT**.
2. Enroll 8 players, **Start**.
3. Dashboard → **Generate Pairings** opens the pairings dialog (same as Round Robin).
4. **Generate & Publish** → 4 boards, all `PENDING`.
5. Create Swiss for **5 players**, first round **RANDOM** → publish round 1 → 2 games + 1 `BYE`.
6. After completing round 1 via **Enter Results** (Phase 8), Generate Pairings for round 2 is enabled.
7. Knockout still opens the bracket view; Round Robin unchanged.

## SRS coverage

- FR-SW-001..004 Swiss basics / no rematch when alternative exists / color tracking
- FR-PAIR-004, FR-PAIR-012 Swiss first round + score-group Dutch pairing
- BR-SWI-001..004 standing order, Dutch halves, rematch avoid, color balance
