# Redis → RDB Periodic Count Synchronization

## Problem

Redis holds the real-time count (filled quantity, completed count), but RDB is the system of record. If Redis crashes or keys expire, the real-time count is lost. RDB needs to stay reasonably up-to-date for:

- Admin dashboards and reporting
- Fallback when Redis is unavailable
- Audit and reconciliation

## Solution

A scheduled job reads Redis counts and writes them to RDB periodically. The write uses a monotonic-increase guard (`WHERE current_value < new_value`) to prevent stale Redis values from overwriting newer RDB values.

## How It Works

```
Every 5 seconds:
1. Query active entities (SUBMITTED, PARTIAL_FILLED)
2. For each entity, GET Redis count
3. UPDATE rdb SET count = redis_count WHERE id = ? AND count < redis_count
```

The `count < redis_count` condition makes it:
- **Idempotent**: Running sync twice with the same Redis value produces the same result
- **Safe on Redis failure**: If Redis returns 0 or stale value, RDB is not overwritten

## Comparison: orchestrator vs security

| | orchestrator (ORCH-012) | security (EXEC-013) |
| --- | --- | --- |
| **What is synced** | `completed_count` per workflow node | `filled_quantity` per order |
| **Redis key** | `{wf:{workflowId}}:task:{taskId}:count` | `{order:{orderId}}:filled_qty` |
| **RDB column** | `workflow_node.completed_count` | `stock_order.filled_quantity` |
| **Sync interval** | 5 seconds | 5 seconds |
| **Guard condition** | `completed_count < ?` | `filled_quantity < ?` |
| **Active filter** | RUNNING workflows | SUBMITTED / PARTIAL_FILLED orders |
| **Update method** | JDBC `UPDATE` | JDBC `UPDATE` |
