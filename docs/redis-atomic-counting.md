# Redis Atomic Counting for Concurrent Consumer Safety

## Problem

When multiple consumers process messages for the same entity concurrently, three problems arise:

1. **Lost Update** — Two consumers read the same count, both increment by 1, but only one increment is persisted
2. **Duplicate Completion** — Multiple consumers see the count reach the target simultaneously, triggering completion logic more than once
3. **Cascading Side Effects** — Lost updates cause incorrect downstream state (wrong balance, duplicate notifications, etc.)

## Solution: Redis INCRBY

Redis `INCRBY` is atomic — it increments and returns the new value in a single operation. Only the consumer whose return value matches the target triggers completion. This eliminates both lost updates and duplicate completion without distributed locks.

## How It Works

```
Consumer A: INCRBY filled_qty 10 → returns 60  (not target, skip)
Consumer B: INCRBY filled_qty 20 → returns 80  (not target, skip)
Consumer C: INCRBY filled_qty 20 → returns 100 (= target, trigger completion)
```

Only Consumer C triggers completion. No race condition possible because INCRBY is atomic.

## Optimistic Lock Retry

Even with Redis handling the count, the DB entity still has `@Version` for optimistic locking. Concurrent updates to the same entity may throw `OptimisticLockException`. `@Retryable` handles this:

```
Attempt 1: OptimisticLockException (version conflict)
Attempt 2: Re-read entity with latest version → success
```

## Comparison: orchestrator vs security

| | orchestrator (ORCH-008) | security (EXEC-010) |
| --- | --- | --- |
| **What is counted** | Task response count per workflow node | Filled quantity per order |
| **Redis key** | `{wf:{workflowId}}:task:{taskId}:count` | `{order:{orderId}}:filled_qty` |
| **INCRBY unit** | +1 per response (or batch size) | +executedQuantity per fill |
| **Completion condition** | `count == expectedCount` → completeTask | `totalFilled >= order.quantity` → ORDER_FILLED |
| **Side effect on completion** | Trigger next tasks in DAG | Publish ORDER_FILLED notification |
| **Optimistic lock target** | Workflow entity (`@Version`) | Order entity (`@Version`) |
| **Consumer concurrency** | 10–50 (success), 3–10 (failure) | 10–50 (execution result) |
| **Hash tag purpose** | Same slot for count + fail flag (Lua script in ORCH-009) | Same slot for filled_qty + cancel flag (Lua script in EXEC-011) |
| **Key TTL** | 24h, set on first increment (`count == 1`) | 24h, set on first increment (`totalFilled == quantity`) |

## Why Hash Tags

Redis Cluster distributes keys across slots by hashing the key name. Lua scripts that operate on multiple keys require all keys in the same slot. Hash tags (`{...}`) force Redis to hash only the tagged portion:

- `{wf:42}:task:7:count` and `{wf:42}:task:7:fail` → same slot
- `{order:123}:filled_qty` and `{order:123}:cancel` → same slot

This is a prerequisite for ORCH-009 / EXEC-011 where Lua scripts atomically check fail/cancel flags and increment counts in a single operation.
