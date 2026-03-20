CREATE TABLE stock_order (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    stock_code       VARCHAR(20)  NOT NULL,
    order_type       VARCHAR(10)  NOT NULL,
    side             VARCHAR(4)   NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    quantity         INT          NOT NULL,
    filled_quantity  INT          NOT NULL DEFAULT 0,
    price            DECIMAL(15,2),
    avg_filled_price DECIMAL(15,2),
    locked_amount    DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_quantity CHECK (quantity > 0),
    CONSTRAINT chk_filled CHECK (filled_quantity >= 0 AND filled_quantity <= quantity)
);

CREATE TABLE execution (
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT       NOT NULL REFERENCES stock_order(id),
    user_id          BIGINT       NOT NULL,
    stock_code       VARCHAR(20)  NOT NULL,
    side             VARCHAR(4)   NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    quantity         INT          NOT NULL,
    price            DECIMAL(15,2) NOT NULL,
    amount           DECIMAL(18,2) NOT NULL,
    exchange_exec_id VARCHAR(50)  NOT NULL,
    executed_at      TIMESTAMP    NOT NULL,
    settlement_date  DATE         NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (exchange_exec_id)
);

CREATE TABLE balance (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE,
    cash_amount   DECIMAL(18,2) NOT NULL DEFAULT 0,
    locked_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_cash CHECK (cash_amount >= 0),
    CONSTRAINT chk_locked CHECK (locked_amount >= 0)
);

CREATE TABLE stock_holding (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    stock_code      VARCHAR(20)  NOT NULL,
    quantity        INT          NOT NULL DEFAULT 0,
    locked_quantity INT          NOT NULL DEFAULT 0,
    avg_buy_price   DECIMAL(15,2) NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (user_id, stock_code),
    CONSTRAINT chk_qty CHECK (quantity >= 0),
    CONSTRAINT chk_locked_qty CHECK (locked_quantity >= 0 AND locked_quantity <= quantity)
);

CREATE TABLE balance_journal (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    journal_type   VARCHAR(30)  NOT NULL,
    reference_type VARCHAR(20)  NOT NULL,
    reference_id   BIGINT       NOT NULL,
    stock_code     VARCHAR(20),
    cash_delta     DECIMAL(18,2) NOT NULL DEFAULT 0,
    quantity_delta INT          NOT NULL DEFAULT 0,
    description    VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE settlement (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT       NOT NULL REFERENCES execution(id),
    order_id        BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    stock_code      VARCHAR(20)  NOT NULL,
    side            VARCHAR(4)   NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    quantity        INT          NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,
    settlement_date DATE         NOT NULL,
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (execution_id)
);

CREATE TABLE outbox (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(30)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    exchange       VARCHAR(50)  NOT NULL,
    routing_key    VARCHAR(50)  NOT NULL,
    payload        JSONB        NOT NULL,
    published      BOOLEAN      DEFAULT FALSE,
    created_at     TIMESTAMP    DEFAULT now()
);

-- Indexes
CREATE INDEX idx_order_user_status ON stock_order(user_id, status);
CREATE INDEX idx_order_active ON stock_order(status) WHERE status IN ('PENDING', 'VALIDATED', 'SUBMITTED', 'PARTIAL_FILLED');
CREATE INDEX idx_execution_order ON execution(order_id);
CREATE INDEX idx_execution_settlement ON execution(settlement_date, status) WHERE status IN ('RECEIVED', 'APPLIED');
CREATE INDEX idx_settlement_pending ON settlement(settlement_date, status) WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_journal_user ON balance_journal(user_id, created_at);
CREATE INDEX idx_journal_ref ON balance_journal(reference_type, reference_id);
CREATE INDEX idx_outbox_unpublished ON outbox(published, created_at) WHERE published = false;
