# System Design Study

## Projects

### [orchestrator](orchestrator/)

Async messaging, Redis, and RDB to guarantee message processing reliability and state consistency in a distributed environment.

### [security](security/)

Extending to a more complex state machine with a domain that is conceptually familiar — stock order execution, balance management, and settlement.

### bff

TBD

## Study Notes

- [Redis Atomic Counting](docs/redis-atomic-counting.md) — Solving lost updates and duplicate completion in concurrent consumers with Redis INCRBY
- [Lua Script Race Condition](docs/lua-script-race-condition.md) — Atomically resolving cancel/execution races and message deduplication with Redis Lua scripts
- [Batch Consumer + JDBC Batch](docs/batch-consumer-jdbc-batch.md) — Reducing DB round-trips with RabbitMQ batch consumption, groupBy, and JDBC batch insert
- [Redis → RDB Count Sync](docs/redis-rdb-count-sync.md) — Periodic idempotent synchronization of Redis counts to RDB with monotonic-increase guard
- [Distributed Lock](docs/distributed-lock.md) — Serializing concurrent order creation per user with Redisson RLock to prevent balance over-deduction
- [Consistent Hash Exchange](docs/consistent-hash-exchange.md) — Routing same-entity messages to the same queue for FIFO ordering and conflict elimination
