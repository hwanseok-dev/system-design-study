# Idempotent Consumer: Handling Duplicate Messages

## Why Duplicates Happen

At-least-once delivery (from the outbox + publisher confirm setup) guarantees a message reaches the broker at least once. However, it means the consumer may receive the same message more than once:

- The consumer processes the message but crashes before ACKing → broker redelivers
- The outbox poller sends the same message twice (crash between send and `published = true` update)
- Network partition causes the broker to redeliver an already-processed message

A well-designed consumer must produce the same outcome whether it processes a message once or many times.

## Two-Layer Deduplication Strategy

### Layer 1: Redis SET NX (Fast Path)

The first check happens in Redis using an atomic `SET key value NX EX ttl` operation embedded in a Lua script. The Lua script combines the dedup check with the business-critical counter increment to avoid a TOCTOU race.

```lua
-- check_dedup_and_increment.lua (orchestrator)
local failKey  = KEYS[1]
local countKey = KEYS[2]
local dedupKey = KEYS[3]
local dedupTtl = tonumber(ARGV[1])

if redis.call('EXISTS', failKey) == 1 then
    return -1  -- task already failed, skip
end

local isNew = redis.call('SET', dedupKey, '1', 'NX', 'EX', dedupTtl)
if not isNew then
    return -2  -- duplicate message, skip
end

return redis.call('INCRBY', countKey, 1)
```

Dedup key format: `{wf:$workflowId}:dedup:$taskId:$sequence` (orchestrator) or `{order:$orderId}:dedup:$exchangeExecId` (security).

Using a hash tag (`{wf:...}`, `{order:...}`) ensures that all keys for the same aggregate land on the same Redis cluster slot, making the Lua script's multi-key atomicity guarantee valid.

TTL is 24 hours — long enough to cover any realistic redelivery window but short enough to avoid unbounded key growth.

**Return values:**
| Value | Meaning | Action |
|---|---|---|
| `-1` | Aggregate already in terminal failure state | ACK and discard |
| `-2` | Duplicate message (dedup key already set) | ACK and discard |
| `n > 0` | New message; counter is now `n` | Continue processing |

### Layer 2: DB UNIQUE Constraint (Safety Net)

Redis is fast but not durable in all failure modes (e.g., Redis restart before persistence). A UNIQUE constraint in the database acts as the final safety net.

```sql
-- orchestrator
CREATE TABLE task_response (
    ...
    UNIQUE (workflow_id, task_id, sequence)
);

-- security
CREATE TABLE execution (
    ...
    exchange_exec_id VARCHAR(50) NOT NULL,
    UNIQUE (exchange_exec_id)
);
```

If a duplicate somehow passes the Redis check (e.g., Redis was flushed), the DB insert raises a unique constraint violation. The consumer catches this and ACKs the message — the state is already correct.

```kotlin
fun insertIgnoreDuplicate(...) {
    try {
        repository.insert(...)
    } catch (e: DataIntegrityViolationException) {
        // Already inserted by a previous delivery — safe to ignore
        logger.info { "action=SKIP_DUPLICATE_DB_INSERT ..." }
    }
}
```

## Manual ACK + Dead Letter Queue

Deduplication only matters if the consumer controls when it ACKs.

```kotlin
setAcknowledgeMode(AcknowledgeMode.MANUAL)
```

- **Successful processing** → `channel.basicAck(deliveryTag, false)`
- **Processing failure** → `channel.basicNack(deliveryTag, false, requeue = false)` → message goes to DLQ

Routing failed messages to a DLQ (rather than re-queuing indefinitely) prevents a poison pill from blocking all consumers.

## Batch Processing with Per-Message Dedup

Consumers batch messages by aggregate ID for efficiency. Dedup runs individually per message within the batch before any bulk operation.

```kotlin
val newExecs = group
    .map { (_, payload) -> payload }
    .filter { exec ->
        val dedupKey = "{order:$orderId}:dedup:${exec.exchangeExecId}"
        redisTemplate.opsForValue()
            .setIfAbsent(dedupKey, "1", Duration.ofSeconds(DEDUP_TTL_SECONDS)) == true
    }

if (newExecs.isEmpty()) {
    ackGroup(group, channel)  // all duplicates — ACK without DB work
    return
}

// batch insert only the non-duplicate subset
executionDbService.applyExecutionBatch(orderId, newExecs, totalFilled)
ackGroup(group, channel)
```

This avoids wasting a DB round-trip when every message in a batch is a duplicate.

## Summary

| Layer | Mechanism | Covers |
|---|---|---|
| Redis Lua script (atomic NX + increment) | Fast dedup + state guard in one round-trip | Normal redeliveries within TTL window |
| DB UNIQUE constraint | Persistent dedup | Redis eviction / restart edge cases |
| Manual ACK | Prevents loss on consumer crash | Ensures re-delivery if processing fails |
| Dead Letter Queue | Isolates poison pills | Prevents retry loops from blocking consumers |
