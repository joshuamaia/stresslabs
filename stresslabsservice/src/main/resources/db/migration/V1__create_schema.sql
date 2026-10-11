CREATE TABLE product (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120)   NOT NULL,
    category    VARCHAR(60)    NOT NULL,
    price       NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    stock       INTEGER        NOT NULL CHECK (stock >= 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_product_category ON product (category);

CREATE TABLE rate_limit_configuration (
    id                    BIGINT PRIMARY KEY,
    enabled               BOOLEAN     NOT NULL,
    capacity              BIGINT      NOT NULL CHECK (capacity > 0),
    refill_tokens         BIGINT      NOT NULL CHECK (refill_tokens > 0),
    refill_period_seconds BIGINT      NOT NULL CHECK (refill_period_seconds > 0),
    client_strategy       VARCHAR(20) NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE TABLE test_execution (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(120)  NOT NULL,
    scenario           VARCHAR(20)   NOT NULL,
    target_key         VARCHAR(40)   NOT NULL,
    endpoint           VARCHAR(255)  NOT NULL,
    http_method        VARCHAR(10)   NOT NULL,
    total_requests     INTEGER       NOT NULL,
    concurrency        INTEGER       NOT NULL,
    target_rps         INTEGER       NOT NULL,
    duration_seconds   INTEGER       NOT NULL,
    timeout_ms         INTEGER       NOT NULL,
    rate_limit_enabled BOOLEAN       NOT NULL,
    rate_limit_summary VARCHAR(120),
    request_body       TEXT,
    status             VARCHAR(20)   NOT NULL,
    failure_reason     VARCHAR(500),
    created_at         TIMESTAMPTZ   NOT NULL,
    started_at         TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ
);
CREATE INDEX ix_test_execution_created_at ON test_execution (created_at DESC);
-- Garante no banco que apenas uma execução esteja ativa por vez
CREATE UNIQUE INDEX ux_test_execution_single_active ON test_execution ((true))
    WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE test_result (
    id                  BIGSERIAL PRIMARY KEY,
    execution_id        UUID             NOT NULL UNIQUE REFERENCES test_execution (id) ON DELETE CASCADE,
    total_requests      BIGINT           NOT NULL,
    completed_requests  BIGINT           NOT NULL,
    success_count       BIGINT           NOT NULL,
    error_count         BIGINT           NOT NULL,
    blocked_count       BIGINT           NOT NULL,
    http_error_count    BIGINT           NOT NULL,
    connection_failures BIGINT           NOT NULL,
    timeout_count       BIGINT           NOT NULL,
    status_2xx          BIGINT           NOT NULL,
    status_3xx          BIGINT           NOT NULL,
    status_4xx          BIGINT           NOT NULL,
    status_5xx          BIGINT           NOT NULL,
    avg_rps             DOUBLE PRECISION NOT NULL,
    avg_latency_ms      DOUBLE PRECISION NOT NULL,
    p95_latency_ms      DOUBLE PRECISION NOT NULL,
    p99_latency_ms      DOUBLE PRECISION NOT NULL,
    min_latency_ms      DOUBLE PRECISION NOT NULL,
    max_latency_ms      DOUBLE PRECISION NOT NULL,
    max_concurrency     INTEGER          NOT NULL,
    duration_ms         BIGINT           NOT NULL
);

CREATE TABLE metric_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    execution_id        UUID             NOT NULL REFERENCES test_execution (id) ON DELETE CASCADE,
    captured_at         TIMESTAMPTZ      NOT NULL,
    elapsed_ms          BIGINT           NOT NULL,
    rps                 DOUBLE PRECISION NOT NULL,
    target_rps          DOUBLE PRECISION NOT NULL,
    concurrency         INTEGER          NOT NULL,
    max_concurrency     INTEGER          NOT NULL,
    avg_latency_ms      DOUBLE PRECISION NOT NULL,
    p95_latency_ms      DOUBLE PRECISION NOT NULL,
    p99_latency_ms      DOUBLE PRECISION NOT NULL,
    sent                BIGINT           NOT NULL,
    completed           BIGINT           NOT NULL,
    success             BIGINT           NOT NULL,
    errors              BIGINT           NOT NULL,
    blocked             BIGINT           NOT NULL,
    timeouts            BIGINT           NOT NULL,
    connection_failures BIGINT           NOT NULL,
    status_2xx          BIGINT           NOT NULL,
    status_4xx          BIGINT           NOT NULL,
    status_5xx          BIGINT           NOT NULL,
    progress            DOUBLE PRECISION NOT NULL
);
CREATE INDEX ix_metric_snapshot_execution ON metric_snapshot (execution_id, elapsed_ms);
