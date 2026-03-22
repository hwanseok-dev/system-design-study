# Batch Consumer + JDBC Batch Insert

## Problem

Processing messages one-by-one creates a DB round-trip per message. At 1M executions/day, single-message processing becomes a bottleneck:

```
Message 1: parse → Redis → DB insert → DB update → DB insert → commit  (5 round-trips)
Message 2: parse → Redis → DB insert → DB update → DB insert → commit  (5 round-trips)
...
```

100 messages = 500 DB round-trips.

## Solution

1. **Batch consume**: RabbitMQ delivers up to N messages at once
2. **Group by entity**: Messages for the same order are processed together
3. **JDBC batch insert**: Multiple rows in a single SQL round-trip
4. **Per-group ACK/NACK**: One group failing doesn't affect others

## How It Works

```
1. Receive batch of 100 messages
2. Group by orderId: {order1: [exec1, exec2], order2: [exec3], ...}
3. For each group:
   a. Dedup each message individually (Redis SET NX)
   b. Lua script: cancel check + INCRBY total quantity
   c. JDBC batch INSERT executions (ON CONFLICT DO NOTHING)
   d. Update order state (loop applyExecution per fill)
   e. Update balance (aggregated amounts)
   f. JDBC batch INSERT settlements
   g. ACK all messages in group
4. If group fails → NACK group, continue other groups
```

100 messages = ~10 groups × 5 DB round-trips = 50 round-trips (10x reduction).

## Why Dedup Moves Out of Lua

In single-message mode, one Lua script handles cancel + dedup + increment atomically.

In batch mode, each message has a different `exchangeExecId`, so dedup keys differ per message. A single Lua call can't handle N different dedup keys efficiently. Instead:

- **Dedup**: `SET NX` per message (individual Redis calls, before Lua)
- **Lua**: cancel check + INCRBY with aggregated quantity (one call per group)

## Comparison: orchestrator vs security

| | orchestrator (ORCH-011) | security (EXEC-012) |
| --- | --- | --- |
| **Group key** | (workflowId, taskId) | orderId |
| **Batch size** | 100 | 100 |
| **Prefetch** | 50 | 50 |
| **Concurrency** | 10–50 | 10–50 |
| **JDBC batch target** | task_response | execution, settlement |
| **Conflict handling** | ON CONFLICT (dedup_key) DO NOTHING | ON CONFLICT (exchange_exec_id) DO NOTHING |
| **Balance update** | N/A (no balance in orchestrator) | Aggregated buy/sell per group |
| **Lua script** | check_fail_and_increment (cancel + increment) | check_and_increment_fill (cancel + increment) |
| **Dedup strategy** | SET NX per message before Lua | SET NX per message before Lua |
