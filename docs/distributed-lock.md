# Distributed Lock for Concurrent Order Serialization

## Problem

When the same user submits two orders simultaneously, both read the available balance at the same time and both pass the check — even though only one should succeed:

```
User balance: 1,000,000

Order A: check balance (1,000,000 >= 750,000) → pass → lock 750,000
Order B: check balance (1,000,000 >= 750,000) → pass → lock 750,000
Result: 1,500,000 locked with only 1,000,000 available
```

Redis Lua scripts are atomic per command, but the gap between Redis check and DB commit allows interleaving.

## Solution

A distributed lock (Redisson RLock) serializes order creation per user. Only one thread can create an order for a given user at a time.

```
Order A: acquire lock → check balance → lock funds → commit → release lock
Order B: wait for lock → acquire lock → check balance (sees A's lock) → reject
```

## Why Not Use DB Locks Alone

- `SELECT FOR UPDATE` on the balance row works but holds a DB connection for the entire operation
- High-concurrency users (e.g., algorithmic trading) would create connection pool exhaustion
- Redis lock is lighter: only a key SET with TTL, no DB connection held during wait

## Lock Design

```
Key:     lock:{user:{userId}}:balance
Wait:    5 seconds (tryLock timeout)
Lease:   30 seconds (auto-released if holder crashes)
Scope:   per user, covers order creation only
```

Redisson's watchdog extends the lease automatically if the holder is still alive.

## What Gets Locked and What Doesn't

| Scenario | Lock Strategy | Reason |
| --- | --- | --- |
| Same user concurrent orders | Redis distributed lock | Prevent double balance deduction |
| Same order concurrent executions | Redis INCRBY + Lua | Count consistency |
| Same user concurrent executions | DB optimistic lock | Low conflict probability, retry is enough |
| Settlement batch | SELECT FOR UPDATE SKIP LOCKED | Server-partitioned processing |

## Comparison: orchestrator vs security

| | orchestrator (ORCH-014) | security (EXEC-017) |
| --- | --- | --- |
| **Lock scope** | Per workflow | Per user |
| **Lock key** | `lock:{wf:{workflowId}}` | `lock:{user:{userId}}:balance` |
| **What is serialized** | Workflow state transitions | Order creation (balance check + lock) |
| **Why needed** | Concurrent completeTask on same workflow | Concurrent orders depleting same balance |
| **Lock library** | Redisson RLock | Redisson RLock |
| **tryLock timeout** | 5s / 30s | 5s / 30s |
| **Applied to execution?** | N/A | No — optimistic lock + retry is sufficient |
