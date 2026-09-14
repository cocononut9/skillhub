CREATE TABLE demand (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    scenario VARCHAR(4000) NOT NULL,
    expected_result VARCHAR(2000) NOT NULL,
    category VARCHAR(80) NOT NULL DEFAULT '',
    frequency VARCHAR(200) NOT NULL DEFAULT '',
    current_time_cost VARCHAR(200) NOT NULL DEFAULT '',
    usage_scope VARCHAR(500) NOT NULL DEFAULT '',
    author_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_demand_created ON demand(hidden, created_at DESC, id DESC);
CREATE INDEX idx_demand_author ON demand(author_id);

CREATE TABLE demand_support (
    id BIGSERIAL PRIMARY KEY,
    demand_id BIGINT NOT NULL REFERENCES demand(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(demand_id, user_id)
);

CREATE TABLE demand_supplement (
    id BIGSERIAL PRIMARY KEY,
    demand_id BIGINT NOT NULL REFERENCES demand(id),
    author_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    content VARCHAR(4000) NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_demand_supplement_list ON demand_supplement(demand_id, created_at, id);
