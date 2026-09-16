-- Demo seed for CTMS (SRS §13.2 / Phase 10)
-- 8 players + one COMPLETED Swiss showcase (3 rounds, top-4 qualification).
-- Idempotent: skips if tournament "Demo Swiss Showcase" already exists.
--
-- Apply after Flyway migrate, e.g.:
--   psql "$CTMS_DB_URL" -U ctms -d chess_tournament -f src/main/resources/db/seed/demo.sql
-- Or with Docker:
--   docker exec -i ctms-postgres psql -U ctms -d chess_tournament < src/main/resources/db/seed/demo.sql

DO $$
DECLARE
    v_tournament_id BIGINT;
    v_p1 BIGINT; v_p2 BIGINT; v_p3 BIGINT; v_p4 BIGINT;
    v_p5 BIGINT; v_p6 BIGINT; v_p7 BIGINT; v_p8 BIGINT;
    v_tp1 BIGINT; v_tp2 BIGINT; v_tp3 BIGINT; v_tp4 BIGINT;
    v_tp5 BIGINT; v_tp6 BIGINT; v_tp7 BIGINT; v_tp8 BIGINT;
    v_r1 BIGINT; v_r2 BIGINT; v_r3 BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM tournament WHERE name = 'Demo Swiss Showcase') THEN
        RAISE NOTICE 'Demo Swiss Showcase already present — skipping seed';
        RETURN;
    END IF;

    INSERT INTO player (name, age, country, global_rating, active)
    VALUES ('Alice Chen', 28, 'USA', 2100, TRUE) RETURNING id INTO v_p1;
    INSERT INTO player (name, age, country, global_rating, active)
    VALUES ('Boris Petrov', 34, 'RUS', 2050, TRUE) RETURNING id INTO v_p2;
    INSERT INTO player (name, age, country, global_rating, active)
    VALUES ('Clara Santos', 22, 'BRA', 1980, TRUE) RETURNING id INTO v_p3;
    INSERT INTO player (name, age, country, global_rating, active)
    VALUES ('Diego Rossi', 31, 'ITA', 1920, TRUE) RETURNING id INTO v_p4;
    INSERT INTO player (name, age, country, global_rating, active)
    VALUES ('Elena Novak', 26, 'CZE', 1850, TRUE) RETURNING id INTO v_p5;
    INSERT INTO player (name, age, country, global_rating, active)
    VALUES ('Farid Hassan', 29, 'EGY', 1780, TRUE) RETURNING id INTO v_p6;
    INSERT INTO player (name, age, country, global_rating, active)
    VALUES ('Grace Kim', 24, 'KOR', 1700, TRUE) RETURNING id INTO v_p7;
    INSERT INTO player (name, age, country, global_rating, active)
    VALUES ('Hiro Tanaka', 27, 'JPN', 1620, TRUE) RETURNING id INTO v_p8;

    INSERT INTO tournament (name, type, rounds_planned, qualifiers_count,
                            swiss_first_round_method, status, started_at, completed_at)
    VALUES ('Demo Swiss Showcase', 'SWISS', 3, 4, 'RATING_SPLIT', 'COMPLETED',
            NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day')
    RETURNING id INTO v_tournament_id;

    -- Final standings from games below:
    -- Alice 3, Clara 3, Boris 2, Diego 1.5, Elena 1, Grace 1, Hiro 0.5, Farid 0
    INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                   points, wins, draws, losses, games_played,
                                   qualification_status, color_balance)
    VALUES (v_tournament_id, v_p1, 2100, 2124, 3.0, 3, 0, 0, 3, 'QUALIFIED', 1)
    RETURNING id INTO v_tp1;
    INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                   points, wins, draws, losses, games_played,
                                   qualification_status, color_balance)
    VALUES (v_tournament_id, v_p2, 2050, 2062, 2.0, 2, 0, 1, 3, 'QUALIFIED', -1)
    RETURNING id INTO v_tp2;
    INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                   points, wins, draws, losses, games_played,
                                   qualification_status, color_balance)
    VALUES (v_tournament_id, v_p3, 1980, 1996, 3.0, 3, 0, 0, 3, 'QUALIFIED', 1)
    RETURNING id INTO v_tp3;
    INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                   points, wins, draws, losses, games_played,
                                   qualification_status, color_balance)
    VALUES (v_tournament_id, v_p4, 1920, 1924, 1.5, 1, 1, 1, 3, 'QUALIFIED', -1)
    RETURNING id INTO v_tp4;
    INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                   points, wins, draws, losses, games_played,
                                   qualification_status, color_balance)
    VALUES (v_tournament_id, v_p5, 1850, 1854, 1.0, 1, 0, 2, 3, 'ELIMINATED', 1)
    RETURNING id INTO v_tp5;
    INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                   points, wins, draws, losses, games_played,
                                   qualification_status, color_balance)
    VALUES (v_tournament_id, v_p6, 1780, 1768, 0.0, 0, 0, 3, 3, 'ELIMINATED', -1)
    RETURNING id INTO v_tp6;
    INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                   points, wins, draws, losses, games_played,
                                   qualification_status, color_balance)
    VALUES (v_tournament_id, v_p7, 1700, 1692, 1.0, 1, 0, 2, 3, 'ELIMINATED', 1)
    RETURNING id INTO v_tp7;
    INSERT INTO tournament_player (tournament_id, player_id, start_rating, current_rating,
                                   points, wins, draws, losses, games_played,
                                   qualification_status, color_balance)
    VALUES (v_tournament_id, v_p8, 1620, 1600, 0.5, 0, 1, 2, 3, 'ELIMINATED', -1)
    RETURNING id INTO v_tp8;

    INSERT INTO round (tournament_id, round_number, status, paired_at, completed_at)
    VALUES (v_tournament_id, 1, 'COMPLETED', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '3 hours')
    RETURNING id INTO v_r1;
    INSERT INTO round (tournament_id, round_number, status, paired_at, completed_at)
    VALUES (v_tournament_id, 2, 'COMPLETED', NOW() - INTERVAL '1 day' - INTERVAL '6 hours',
            NOW() - INTERVAL '1 day' - INTERVAL '3 hours')
    RETURNING id INTO v_r2;
    INSERT INTO round (tournament_id, round_number, status, paired_at, completed_at)
    VALUES (v_tournament_id, 3, 'COMPLETED', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day' + INTERVAL '2 hours')
    RETURNING id INTO v_r3;

    -- Round 1 (rating split style): 1v5, 2v6, 3v7, 4v8 — all higher seeds win
    INSERT INTO game (round_id, board_number, white_tournament_player_id, black_tournament_player_id,
                      result, white_score, black_score, rematch, white_rating_delta, black_rating_delta)
    VALUES
        (v_r1, 1, v_tp1, v_tp5, 'WHITE_WIN', 1.0, 0.0, FALSE, 8, -8),
        (v_r1, 2, v_tp2, v_tp6, 'WHITE_WIN', 1.0, 0.0, FALSE, 8, -8),
        (v_r1, 3, v_tp3, v_tp7, 'WHITE_WIN', 1.0, 0.0, FALSE, 8, -8),
        (v_r1, 4, v_tp4, v_tp8, 'WHITE_WIN', 1.0, 0.0, FALSE, 8, -8);

    -- Round 2: winners vs winners / losers vs losers (illustrative)
    INSERT INTO game (round_id, board_number, white_tournament_player_id, black_tournament_player_id,
                      result, white_score, black_score, rematch, white_rating_delta, black_rating_delta)
    VALUES
        (v_r2, 1, v_tp5, v_tp1, 'BLACK_WIN', 0.0, 1.0, FALSE, -8, 8),
        (v_r2, 2, v_tp6, v_tp2, 'BLACK_WIN', 0.0, 1.0, FALSE, -8, 8),
        (v_r2, 3, v_tp7, v_tp3, 'BLACK_WIN', 0.0, 1.0, FALSE, -8, 8),
        (v_r2, 4, v_tp8, v_tp4, 'DRAW', 0.5, 0.5, FALSE, 4, -4);

    -- Round 3
    INSERT INTO game (round_id, board_number, white_tournament_player_id, black_tournament_player_id,
                      result, white_score, black_score, rematch, white_rating_delta, black_rating_delta)
    VALUES
        (v_r3, 1, v_tp1, v_tp2, 'WHITE_WIN', 1.0, 0.0, FALSE, 8, -8),
        (v_r3, 2, v_tp3, v_tp4, 'WHITE_WIN', 1.0, 0.0, FALSE, 8, -8),
        (v_r3, 3, v_tp5, v_tp6, 'WHITE_WIN', 1.0, 0.0, FALSE, 8, -8),
        (v_r3, 4, v_tp7, v_tp8, 'WHITE_WIN', 1.0, 0.0, FALSE, 8, -8);

    -- Align global ratings with final current ratings for demo players
    UPDATE player SET global_rating = 2124, updated_at = NOW() WHERE id = v_p1;
    UPDATE player SET global_rating = 2062, updated_at = NOW() WHERE id = v_p2;
    UPDATE player SET global_rating = 1996, updated_at = NOW() WHERE id = v_p3;
    UPDATE player SET global_rating = 1924, updated_at = NOW() WHERE id = v_p4;
    UPDATE player SET global_rating = 1854, updated_at = NOW() WHERE id = v_p5;
    UPDATE player SET global_rating = 1768, updated_at = NOW() WHERE id = v_p6;
    UPDATE player SET global_rating = 1692, updated_at = NOW() WHERE id = v_p7;
    UPDATE player SET global_rating = 1600, updated_at = NOW() WHERE id = v_p8;

    RAISE NOTICE 'Seeded Demo Swiss Showcase (tournament_id=%)', v_tournament_id;
END $$;
