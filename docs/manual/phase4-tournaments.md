# Phase 4 — Tournaments & Enrollment manual checklist

## Prerequisites

- Players exist (Phase 3): create at least 4–8 players
- `mvn javafx:run`

## Checklist

1. **Tournaments → Manage Tournaments** — empty or existing list loads.
2. **Create Swiss**: name `Swiss Cup`, type SWISS, rounds 5, qualifiers 2, first round RANDOM → created, dashboard opens.
3. **Create Round Robin**: name `RR Club`, type ROUND_ROBIN, rounds 3 (for 4 players), qualifiers 0.
4. **Create Knockout**: name `KO Night`, type KNOCKOUT, rounds 2 (for 4 players), qualifiers 1.
5. Open Swiss dashboard → **Manage Enrollment** → enroll 4 players → Close.
6. **Start Tournament** → status ACTIVE, round label shows Round 1 (PENDING_PAIRINGS).
7. Try **Start** on RR with only 2 enrolled → expect validation error (need ≥3).
8. After start, open enrollment → still open while round 1 is PENDING_PAIRINGS; enroll one more if desired.
9. Status filter: DRAFT / ACTIVE on tournament list.
10. Generate Pairings opens the pairings dialog (Phase 5 for Round Robin).

## SRS coverage

- FR-TNM-001..005 create, status, start mins, list, dashboard
- FR-TNP-001..004 enroll/unenroll before pairings
- BR-ENR-001 enrollment lock rules
