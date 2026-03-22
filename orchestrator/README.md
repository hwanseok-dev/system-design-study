# Orchestrator Module

A workflow orchestrator that executes tasks defined as a DAG (Directed Acyclic Graph).
Each task is dispatched to worker services via RabbitMQ. Workers report success or failure asynchronously.
The orchestrator tracks completion counts, manages state transitions, and triggers downstream tasks when dependencies are met.

## 1. Workflow DAG Examples

#### Linear

```mermaid
flowchart LR
    A[Task A] --> B[Task B] --> C[Task C]
```

#### Diamond

```mermaid
flowchart LR
    A[Task A] --> B[Task B]
    A --> C[Task C]
    B --> D[Task D]
    C --> D
```

Task D waits until both B and C succeed. If B or C fails, D is skipped.

## 2. Workflow State Machine

```mermaid
stateDiagram-v2
    [*] --> CREATED

    CREATED --> RUNNING

    RUNNING --> SUCCEEDED
    RUNNING --> FAILED

    SUCCEEDED --> [*]
    FAILED --> [*]
```

## 3. Task (Node) State Machine

```mermaid
stateDiagram-v2
    [*] --> CREATED

    CREATED --> WAITING
    WAITING --> RUNNING
    RUNNING --> SUCCEEDED
    RUNNING --> FAILED

    SUCCEEDED --> [*]
    FAILED --> [*]
```

- **CREATED** → **WAITING**: Workflow started, task registered in DAG
- **WAITING** → **RUNNING**: All parent tasks succeeded
- **RUNNING** → **SUCCEEDED**: `completedCount >= expectedCount`
- **RUNNING** → **FAILED**: Failure response received, propagates to downstream tasks

## 4. RabbitMQ Topology

> **Legend:**<br/>
> Yellow circle = Exchange<br/>
> Blue box = Queue<br/>
> Grey box = Consumer<br/>
> Arrow labels = routing key<br/>

#### task.exchange + response.exchange

```mermaid
flowchart LR
    classDef exchange fill:#f59e0b,stroke:#d97706,color:#000
    classDef queue fill:#3b82f6,stroke:#2563eb,color:#fff
    classDef consumer fill:#6b7280,stroke:#4b5563,color:#fff

    TE((task.exchange<br/>Direct)):::exchange
    RE((response.exchange<br/>Direct)):::exchange

    Q1[[queue.response.success]]:::queue
    Q2[[queue.response.failure]]:::queue

    SC[SuccessResponseConsumer]:::consumer
    FC[FailureResponseConsumer]:::consumer

    TE -->|task.execute| WORKER[Worker Services]:::consumer

    WORKER -->|response.success| RE
    WORKER -->|response.failure| RE

    RE -->|response.success| Q1
    RE -->|response.failure| Q2

    Q1 --> SC
    Q2 --> FC
```

#### Dead Letter Queues

```mermaid
flowchart LR
    classDef exchange fill:#ef4444,stroke:#dc2626,color:#fff
    classDef queue fill:#3b82f6,stroke:#2563eb,color:#fff
    classDef dlq fill:#7c3aed,stroke:#6d28d9,color:#fff

    Q1[[queue.response.success]]:::queue
    Q2[[queue.response.failure]]:::queue

    DLX((response.dlx)):::exchange

    DLQ1[[*.success.dlq]]:::dlq
    DLQ2[[*.failure.dlq]]:::dlq

    Q1 -.->|nack| DLX
    Q2 -.->|nack| DLX

    DLX --> DLQ1
    DLX --> DLQ2
```
