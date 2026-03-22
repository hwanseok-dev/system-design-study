CREATE TABLE order_read_model (
    order_id         BIGINT PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    stock_code       VARCHAR(20)   NOT NULL,
    stock_name       VARCHAR(100),
    side             VARCHAR(4)    NOT NULL,
    order_type       VARCHAR(10)   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    quantity         INT           NOT NULL,
    filled_quantity  INT           NOT NULL DEFAULT 0,
    price            DECIMAL(15,2),
    avg_filled_price DECIMAL(15,2),
    locked_amount    DECIMAL(18,2) NOT NULL DEFAULT 0,
    event_version    BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL
);

CREATE INDEX idx_orm_user_status ON order_read_model(user_id, status);
CREATE INDEX idx_orm_user_created ON order_read_model(user_id, created_at DESC);

CREATE TABLE execution_read_model (
    execution_id     BIGINT PRIMARY KEY,
    order_id         BIGINT        NOT NULL,
    user_id          BIGINT        NOT NULL,
    stock_code       VARCHAR(20)   NOT NULL,
    stock_name       VARCHAR(100),
    side             VARCHAR(4)    NOT NULL,
    quantity         INT           NOT NULL,
    price            DECIMAL(15,2) NOT NULL,
    amount           DECIMAL(18,2) NOT NULL,
    executed_at      TIMESTAMP     NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_erm_user_executed ON execution_read_model(user_id, executed_at DESC);
CREATE INDEX idx_erm_order ON execution_read_model(order_id);

CREATE TABLE portfolio_snapshot (
    user_id          BIGINT        NOT NULL,
    stock_code       VARCHAR(20)   NOT NULL,
    stock_name       VARCHAR(100),
    quantity         INT           NOT NULL DEFAULT 0,
    locked_quantity  INT           NOT NULL DEFAULT 0,
    avg_buy_price    DECIMAL(15,2) NOT NULL DEFAULT 0,
    cash_amount      DECIMAL(18,2),
    locked_cash      DECIMAL(18,2),
    updated_at       TIMESTAMP     NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, stock_code)
);
