# Phase 8 — Results, Scoring & Elo manual checklist

## Prerequisites

- A started tournament with published pairings (Phases 5–7)
- `mvn javafx:run`

## Checklist

1. Create Swiss (or RR) with 4 players, start, **Generate & Publish** round 1.
2. Dashboard → **Enter Results** opens the results dialog.
3. Set each board result via the Result column (`WHITE_WIN` / `BLACK_WIN` / `DRAW`).
4. **Save All** → round becomes `IN_PROGRESS`; values persist on reopen.
5. **Complete Round** → points and Elo update; round status `COMPLETED`.
6. Generate Pairings for round 2 is now available.
7. Knockout: draws are not offered; completing a round eliminates losers.
8. After round 2 is published, editing round 1 results is blocked.

## SRS coverage

- FR-RES-001..008 results entry / complete round / edit lock
- FR-RAT-001..006 Elo K=32, bye skips rating, per-game deltas
- BR-SCR-001..003 scoring including bye = 1 point
