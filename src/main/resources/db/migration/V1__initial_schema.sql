CREATE TABLE player (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    age             INTEGER CHECK (age IS NULL OR age >= 0),
    country         VARCHAR(100),
    global_rating   INTEGER NOT NULL DEFAULT 1500,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tournament (
    id                        BIGSERIAL PRIMARY KEY,
    name                      VARCHAR(200) NOT NULL,
    type                      VARCHAR(32) NOT NULL CHECK (type IN ('ROUND_ROBIN','KNOCKOUT','SWISS')),
    rounds_planned            INTEGER NOT NULL CHECK (rounds_planned >= 1),
    qualifiers_count          INTEGER NOT NULL CHECK (qualifiers_count >= 0),
    swiss_first_round_method  VARCHAR(32) DEFAULT 'RANDOM'
        CHECK (swiss_first_round_method IN ('RANDOM','RATING_SPLIT')),
    status                    VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ACTIVE','COMPLETED','CANCELLED')),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at                TIMESTAMPTZ,
    completed_at              TIMESTAMPTZ
);

CREATE TABLE tournament_player (
    id                    BIGSERIAL PRIMARY KEY,
    tournament_id         BIGINT NOT NULL REFERENCES tournament(id),
    player_id             BIGINT NOT NULL REFERENCES player(id),
    start_rating          INTEGER NOT NULL,
    current_rating        INTEGER NOT NULL,
    points                NUMERIC(5,1) NOT NULL DEFAULT 0,
    wins                  INTEGER NOT NULL DEFAULT 0,
    draws                 INTEGER NOT NULL DEFAULT 0,
    losses                INTEGER NOT NULL DEFAULT 0,
    games_played          INTEGER NOT NULL DEFAULT 0,
    qualification_status  VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (qualification_status IN ('PENDING','QUALIFIED','ELIMINATED','NOT_APPLICABLE')),
    color_balance         INTEGER NOT NULL DEFAULT 0,
    UNIQUE (tournament_id, player_id)
);

CREATE TABLE round (
    id              BIGSERIAL PRIMARY KEY,
    tournament_id   BIGINT NOT NULL REFERENCES tournament(id),
    round_number    INTEGER NOT NULL CHECK (round_number >= 1),
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAIRINGS'
        CHECK (status IN ('PENDING_PAIRINGS','PAIRINGS_PUBLISHED','IN_PROGRESS','COMPLETED')),
    paired_at       TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    UNIQUE (tournament_id, round_number)
);

CREATE TABLE game (
    id                          BIGSERIAL PRIMARY KEY,
    round_id                    BIGINT NOT NULL REFERENCES round(id) ON DELETE CASCADE,
    board_number                INTEGER NOT NULL,
    white_tournament_player_id  BIGINT REFERENCES tournament_player(id),
    black_tournament_player_id  BIGINT REFERENCES tournament_player(id),
    result                      VARCHAR(32) CHECK (result IN ('WHITE_WIN','BLACK_WIN','DRAW','BYE','PENDING')),
    white_score                 NUMERIC(3,1),
    black_score                 NUMERIC(3,1),
    rematch                     BOOLEAN NOT NULL DEFAULT FALSE,
    white_rating_delta          INTEGER,
    black_rating_delta          INTEGER,
    UNIQUE (round_id, board_number)
);

CREATE INDEX idx_tp_tournament ON tournament_player(tournament_id);
CREATE INDEX idx_round_tournament ON round(tournament_id);
CREATE INDEX idx_game_round ON game(round_id);
