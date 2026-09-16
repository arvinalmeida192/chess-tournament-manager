# Phase 6 — Knockout Pairings manual checklist

## Prerequisites

- Players exist (Phase 3): at least 6–8 with varied ratings
- `mvn javafx:run`

## Checklist

1. Create **Knockout** tournament for **8 players**, rounds **3**, qualifiers **1**.
2. Enroll 8 players (different ratings), **Start**.
3. Dashboard → **Generate Pairings** opens the **Knockout Bracket** dialog.
4. **Generate & Publish** → 4 boards, all `PENDING`, no byes.
5. Create KO for **6 players**, rounds **3**; publish round 1 → 2 real games + 2 `BYE` rows (lowest-rated get byes).
6. Bracket table groups by round; Winner column shows placeholder until Phase 8.
7. Round Robin still uses the pairings dialog; Swiss generate remains disabled (Phase 7).

## SRS coverage

- FR-KO-001..004 bracket depth / byes / display
- FR-PAIR-003, FR-PAIR-011 (advancement pairing; elimination hook via KnockoutAdvancementService)
- BR-KO-001 lowest-rated byes
