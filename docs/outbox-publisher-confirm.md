# Transactional Outbox + Publisher Confirm: At-Least-Once Publish Guarantee

## Problem

In a distributed system, writing to a database and publishing a message to a broker are two separate operations. A naive approach — publish after committing — risks losing messages if the broker is unavailable or the process crashes between the two steps.

## Solution: Transactional Outbox Pattern

Instead of publishing directly, the outbox pattern writes the message into the same database transaction as the domain change. A separate poller then reads unpublished messages and forwards them to the broker.

```
[Domain Service]
  BEGIN TRANSACTION
    UPDATE domain_table ...
    INSERT INTO outbox (exchange, routing_key, payload, published=false)
  COMMIT

[Outbox Poller] — every 1 s
  SELECT ... FROM outbox WHERE published = false LIMIT N
    FOR UPDATE SKIP LOCKED
  → rabbitTemplate.convertAndSend(...)
  → UPDATE outbox SET published = true
```

### Outbox Table Schema (security)

```sql
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
CREATE INDEX idx_outbox_unpublished ON outbox(published, created_at) WHERE published = false;
```

The partial index on `(published, created_at) WHERE published = false` keeps the polling query fast even as the table grows.

### Pessimistic Locking for Scale-Out Safety

When multiple poller instances run concurrently (e.g., during rolling deploys), the same row must not be processed twice.

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
@Query("SELECT o FROM OutboxMessage o WHERE o.published = false ORDER BY o.createdAt LIMIT :size")
fun findUnpublished(@Param("size") size: Int): List<OutboxMessage>
```

`lock.timeout = -2` maps to `SKIP LOCKED` on PostgreSQL: if another instance already holds the lock on a row, the query skips it rather than blocking. This prevents head-of-line blocking and avoids deadlocks.

## Publisher Confirms

Marking `published = true` before receiving broker acknowledgement would re-introduce the same risk the outbox was meant to solve. Publisher confirms close this gap.

```yaml
# application.yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
```

- **`correlated`** — each message gets a unique correlation ID; the broker returns an async ACK/NACK per message.
- **`publisher-returns: true`** — if a message is routed but no queue matches, the broker returns it rather than dropping it silently.

With `correlated` confirms, the `published` flag is set only after an ACK is received. If the broker returns a NACK, or the poller crashes before receiving the ACK, the row remains `published = false` and the next poll cycle retries.

## At-Least-Once Guarantee

The combination provides at-least-once delivery:

| Failure Scenario | Outcome |
|---|---|
| Broker unreachable during publish | Row stays `published = false`; retried on next poll |
| Broker NACK | Row stays `published = false`; retried on next poll |
| Poller crashes after send, before ACK | Row stays `published = false`; retried (may produce a duplicate) |
| DB commit fails (outbox insert fails) | Message is never inserted; no publish attempted |

The only scenario that produces a duplicate is a crash between a successful broker send and the subsequent `published = true` update. This is an expected trade-off of at-least-once semantics — the consumer side must handle duplicates.
