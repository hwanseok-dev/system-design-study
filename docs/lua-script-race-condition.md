# Lua Script for Cancel/Execution Race Condition + Deduplication

## Problem

### 1. Cancel vs Execution Race

With separate Redis commands (EXISTS + INCRBY), a cancel can slip between them:

```
Execution consumer: EXISTS cancelKey → 0 (not cancelled)
Cancel consumer:    SET cancelKey → 1, restore balance
Execution consumer: INCRBY filledKey → processes execution on a cancelled order
```

The order is cancelled but a late execution still modifies balance — double-counting.

### 2. Duplicate Execution

RabbitMQ may redeliver the same message. Without dedup, the same execution is processed twice:

```
Consumer A: INCRBY filledKey 10 → 60
Consumer A: (retry) INCRBY filledKey 10 → 70  (should still be 60)
```

filledQuantity is inflated, balance is wrong.

## Solution: Lua Script

A Lua script executes atomically on Redis — no other command can interleave. One script checks cancel flag, dedup key, and increments count in a single atomic operation.

## How It Works

### Execution Path (check_dedup_and_increment_fill.lua)

```
Input: cancelKey, filledKey, dedupKey, increment, dedupTtl

1. EXISTS cancelKey?     → return -1 (order cancelled, skip)
2. SET dedupKey NX EX?   → if exists, return -2 (duplicate, skip)
3. INCRBY filledKey      → return new total (proceed with execution)
```

All 3 steps are atomic. No cancel or duplicate can slip through.

### Cancel Path (cancel_order.lua)

```
Input: cancelKey, filledKey, ttl

1. SET cancelKey NX EX?  → if exists, return -1 (already cancelled)
2. GET filledKey          → return current filled quantity
```

The cancel flag is set atomically. Any subsequent execution sees the flag and stops.

## 3-Layer Defense

```
Layer 1: Lua Script     — cancel + dedup + increment atomic (blocks most races)
Layer 2: DB double-check — re-check cancel flag before DB commit (gap defense)
Layer 3: Optimistic Lock — @Version conflict retry (final safety net)
```

## Comparison: orchestrator vs security

| | orchestrator (ORCH-009/010) | security (EXEC-011) |
| --- | --- | --- |
| **Race condition** | Success vs failure consumer | Execution vs cancel consumer |
| **Flag key** | `{wf:{id}}:task:{taskId}:fail` | `{order:{id}}:cancelled` |
| **Count key** | `{wf:{id}}:task:{taskId}:count` | `{order:{id}}:filled_qty` |
| **Dedup key** | `{wf:{id}}:dedup:{taskId}:{seq}` | `{order:{id}}:dedup:{exchangeExecId}` |
| **Script: check+increment** | `check_dedup_and_increment.lua` | `check_dedup_and_increment_fill.lua` |
| **Script: set flag** | `check_and_fail.lua` | `cancel_order.lua` |
| **Flag script returns** | 1 (first) or 0 (duplicate) | filled quantity or -1 (duplicate) |
| **Why return differs** | Only needs "am I first?" | Needs filled qty for remaining balance calc |
| **DB double-check** | Check fail flag before completeTask | Check cancel flag before DB write |
| **Key TTL** | 24h | 24h |

## Hash Tag: Keeping Keys on the Same Redis Cluster Node

Lua scripts run on a single Redis node, so all keys they touch must be on the same node. Redis Cluster assigns keys to nodes by hashing the key name. Hash tags (`{...}`) override this — Redis only hashes the part inside the braces:

- `{order:123}:cancelled`, `{order:123}:filled_qty`, `{order:123}:dedup:abc` → all hash to slot of `order:123`

Without hash tags, keys would scatter across slots and the Lua script would fail with `CROSSSLOT` error.
