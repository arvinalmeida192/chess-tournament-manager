# Phase 10 — E2E UI & release hardening manual notes

## What landed

- Shared `app.css` + `UiStyles` on main scene and dialogs
- Main toolbar + Settings → Test Database Connection
- Dashboard: Finalize / Cancel; enablement from `TournamentViewModel` (UI-001)
- Tournament list Cancel for DRAFT/ACTIVE
- Demo seed `src/main/resources/db/seed/demo.sql`
- `docs/manual/E2E_CHECKLIST.md` (SRS §13.1)

## Quick host path

1. `mvn javafx:run`
2. Players → add (or load demo seed)
3. Tournaments → Create → Enroll → Start → Pairings → Results → Leaderboard → Finalize

## Demo seed

```bash
docker exec -i ctms-postgres psql -U ctms -d chess_tournament \
  < src/main/resources/db/seed/demo.sql
```
