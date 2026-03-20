CREATE TABLE task (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL UNIQUE,
    queue_name      VARCHAR(255) NOT NULL,
    expected_count  INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE workflow (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE workflow_task (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT       NOT NULL REFERENCES workflow(id),
    task_id         BIGINT       NOT NULL REFERENCES task(id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    expected_count  INT          NOT NULL DEFAULT 0,
    completed_count INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (workflow_id, task_id)
);

CREATE TABLE workflow_task_edge (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT NOT NULL REFERENCES workflow(id),
    parent_task_id  BIGINT NOT NULL REFERENCES task(id),
    child_task_id   BIGINT NOT NULL REFERENCES task(id),
    UNIQUE (workflow_id, parent_task_id, child_task_id)
);

CREATE TABLE task_response (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT       NOT NULL REFERENCES workflow(id),
    task_id         BIGINT       NOT NULL REFERENCES task(id),
    sequence        INT          NOT NULL,
    payload         JSONB,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (workflow_id, task_id, sequence)
);

CREATE TABLE task_execution_request (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT       NOT NULL REFERENCES workflow(id),
    task_id         BIGINT       NOT NULL REFERENCES task(id),
    payload         JSONB        NOT NULL,
    published       BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    DEFAULT now(),
    published_at    TIMESTAMP
);

CREATE INDEX idx_task_response_workflow_task ON task_response(workflow_id, task_id);
CREATE INDEX idx_ter_unpublished ON task_execution_request(published, created_at) WHERE published = false;
CREATE INDEX idx_workflow_status ON workflow(status) WHERE status IN ('RUNNING', 'WAITING');
