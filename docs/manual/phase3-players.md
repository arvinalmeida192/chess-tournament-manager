# Phase 3 — Players manual checklist

## Prerequisites

- PostgreSQL running (`ctms-postgres` or equivalent)
- `config/application.properties` configured
- `mvn javafx:run`

## Checklist

1. Open **Players → Manage Players**. Table loads (may be empty).
2. Click **Add**. Leave name blank → Save → expect validation alert.
3. Add player: name `Alice`, age `25`, country `USA`, rating blank → Save. Appears with rating **1500**.
4. Add seven more players with varied ratings, e.g.:

   | Name | Age | Country | Rating |
   |------|-----|---------|--------|
   | Bob | 30 | India | 1600 |
   | Carol | 22 | UK | 1450 |
   | Dave | | Germany | 1800 |
   | Eve | 40 | France | 1550 |
   | Frank | 19 | USA | 1400 |
   | Grace | 28 | Spain | 1700 |
   | Heidi | 35 | Japan | 1525 |

5. Select a player → **Edit** → change name/rating → Save → table refreshes.
6. Select a player → **Deactivate** → confirm → player disappears from active list.
7. Click **Refresh** → list reloads from DB.
8. Quit and relaunch app → open Players → previously created active players still listed (persistence).

## SRS coverage

- FR-PLR-001 create with default rating
- FR-PLR-002 unique ID shown in table
- FR-PLR-003 list registered players
- FR-PLR-004 edit demographics and global rating
- FR-PLR-005 deactivate (Should)
