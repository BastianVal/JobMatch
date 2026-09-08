-- Public ATS boards are configured by the platform.  They are deliberately
-- independent from a member's search query: a member never submits an ATS URL.
CREATE TABLE ingestion.public_job_board (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    source_id bigint NOT NULL REFERENCES jobs.source(id),
    board_key varchar(200) NOT NULL,
    employer_name varchar(200) NOT NULL,
    country_code char(2),
    enabled boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (source_id, board_key)
);

CREATE INDEX public_job_board_enabled_idx
    ON ingestion.public_job_board(source_id, id) WHERE enabled;

-- These are verified, public boards.  Keep them disabled until the matching
-- connector has passed its contract and live-ingestion checks.  This protects
-- production from emitting simulated data or unintended outbound requests.
INSERT INTO ingestion.public_job_board(public_id, source_id, board_key, employer_name, country_code, enabled)
SELECT board.public_id, source.id, board.board_key, board.employer_name, board.country_code, false
FROM (VALUES
    ('22000000-0000-0000-0000-000000000001'::uuid, 'GREENHOUSE', 'c3iot', 'C3 AI', 'MX'),
    ('22000000-0000-0000-0000-000000000002'::uuid, 'GREENHOUSE', 'cookunity', 'CookUnity', 'MX'),
    ('22000000-0000-0000-0000-000000000003'::uuid, 'LEVER', 'bluelightconsulting', 'Bluelight Consulting', 'MX'),
    ('22000000-0000-0000-0000-000000000004'::uuid, 'LEVER', 'coupa', 'Coupa', 'MX'),
    ('22000000-0000-0000-0000-000000000005'::uuid, 'ASHBY', 'delinea', 'Delinea', 'MX'),
    ('22000000-0000-0000-0000-000000000006'::uuid, 'ASHBY', 'belvo', 'Belvo', 'MX')
) AS board(public_id, source_key, board_key, employer_name, country_code)
JOIN jobs.source source ON source.source_key = board.source_key
ON CONFLICT (source_id, board_key) DO NOTHING;
