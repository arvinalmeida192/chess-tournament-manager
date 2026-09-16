# Phase 9 — Leaderboards & Qualification manual checklist

## Prerequisites

- A multi-round tournament with at least one COMPLETED round (Phase 8)
- `mvn javafx:run`

## Checklist

1. Complete round 1 of a Swiss (or RR) tournament with 4+ players.
2. Dashboard → **Leaderboard** opens the standings dialog.
3. Header shows **Round R Leaderboard**; table columns Rank, Player, Rating, Points, W, D, L.
4. Round selector lists completed rounds; switching rounds recomputes historical points.
5. After all planned rounds are completed, **Apply Qualification** marks top Q as `QUALIFIED` and others `ELIMINATED` (Q=0 → all `NOT_APPLICABLE`).
6. **Finalize Tournament** sets status `COMPLETED`, applies qualification, and shows Start Rating / Final Rating / Qualification columns.
7. Finalize is blocked while any planned round is incomplete.
8. Tie-break order: points → head-to-head (exactly two tied) → rating → name.

## SRS coverage

- FR-LDB-001..005 standings and final board
- FR-QLF-001..004 qualification and finalize
- BR-TIE-001 / BR-TIE-002 tie-breaks
