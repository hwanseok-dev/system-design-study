# Security Module

## 1. Order State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING

    PENDING --> VALIDATED
    PENDING --> REJECTED

    VALIDATED --> SUBMITTED
    VALIDATED --> REJECTED

    SUBMITTED --> PARTIAL_FILLED
    SUBMITTED --> FILLED
    SUBMITTED --> CANCELLED

    PARTIAL_FILLED --> PARTIAL_FILLED
    PARTIAL_FILLED --> FILLED
    PARTIAL_FILLED --> CANCELLED

    FILLED --> SETTLED

    SETTLED --> [*]
    REJECTED --> [*]
    CANCELLED --> [*]
```

## 2. RabbitMQ Topology

> **Legend:**<br/>
> Yellow circle = Exchange<br/>
> Blue box = Queue<br/> 
> Grey box = Consumer<br/>
> Arrow labels = routing key<br/> 
> Dotted arrows = simulator (local only)<br/>

#### order.exchange

```mermaid
flowchart LR
    classDef exchange fill:#f59e0b,stroke:#d97706,color:#000
    classDef queue fill:#3b82f6,stroke:#2563eb,color:#fff
    classDef consumer fill:#6b7280,stroke:#4b5563,color:#fff
    classDef simulator fill:#6b7280,stroke:#4b5563,color:#fff,stroke-dasharray: 5 5

    OE((order.exchange<br/>Direct)):::exchange

    Q1[[queue.order.validate]]:::queue
    Q2[[queue.order.validated]]:::queue
    Q3[[queue.order.rejected]]:::queue
    Q4[[queue.order.execute]]:::queue
    Q5[[queue.order.cancel]]:::queue
    Q6[[queue.order.cancel.confirmed]]:::queue

    VS[ValidationSimulator]:::simulator
    OVC["OrderValidationConsumer<br/>handleValidated"]:::consumer
    OVC2["OrderValidationConsumer<br/>handleRejected"]:::consumer
    EXSIM[ExecutionSimulator]:::simulator
    CS[CancelSimulator]:::simulator
    OCC[OrderCancelConsumer]:::consumer

    OE -->|order.validate| Q1
    OE -->|order.validated| Q2
    OE -->|order.rejected| Q3
    OE -->|order.execute| Q4
    OE -->|order.cancel| Q5
    OE -->|order.cancel.confirmed| Q6

    Q1 --> VS
    Q2 --> OVC
    Q3 --> OVC2
    Q4 --> EXSIM
    Q5 --> CS
    Q6 --> OCC

    VS -.->|order.validated| OE
    VS -.->|order.rejected| OE
    CS -.->|order.cancel.confirmed| OE
```

#### execution.exchange + notification.exchange

```mermaid
flowchart LR
    classDef exchange fill:#f59e0b,stroke:#d97706,color:#000
    classDef queue fill:#3b82f6,stroke:#2563eb,color:#fff
    classDef consumer fill:#6b7280,stroke:#4b5563,color:#fff
    classDef simulator fill:#6b7280,stroke:#4b5563,color:#fff,stroke-dasharray: 5 5
    classDef dropped fill:#ef4444,stroke:#dc2626,color:#fff,stroke-dasharray: 5 5

    EE((execution.exchange<br/>Direct)):::exchange
    NE((notification.exchange<br/>Topic)):::exchange

    Q7[[queue.execution.result]]:::queue

    EXSIM[ExecutionSimulator]:::simulator
    ERC[ExecutionResultConsumer]:::consumer

    EXSIM -.->|execution.result| EE
    EE -->|execution.result| Q7
    Q7 --> ERC

    NE -->|notify.order.filled| DROP1[/unimplemented/]:::dropped
    NE -->|notify.order.cancelled| DROP2[/unimplemented/]:::dropped
```

### Dead Letter Queues

Failed messages (nack, no requeue) are routed to dead letter exchanges for inspection.

```mermaid
flowchart LR
    classDef exchange fill:#ef4444,stroke:#dc2626,color:#fff
    classDef queue fill:#3b82f6,stroke:#2563eb,color:#fff
    classDef dlq fill:#7c3aed,stroke:#6d28d9,color:#fff

    Q1[[queue.order.validate]]:::queue
    Q2[[queue.order.validated]]:::queue
    Q3[[queue.order.rejected]]:::queue
    Q4[[queue.order.execute]]:::queue
    Q5[[queue.order.cancel]]:::queue
    Q6[[queue.order.cancel.confirmed]]:::queue
    Q7[[queue.execution.result]]:::queue

    ODLX((order.dlx)):::exchange
    EDLX((execution.dlx)):::exchange

    DLQ1[[*.validated.dlq]]:::dlq
    DLQ2[[*.rejected.dlq]]:::dlq
    DLQ3[[*.cancel.confirmed.dlq]]:::dlq
    DLQ4[[*.cancel.dlq]]:::dlq
    DLQ5[[*.execution.result.dlq]]:::dlq

    Q1 -.->|nack| ODLX
    Q2 -.->|nack| ODLX
    Q3 -.->|nack| ODLX
    Q4 -.->|nack| ODLX
    Q5 -.->|nack| ODLX
    Q6 -.->|nack| ODLX

    ODLX --> DLQ1
    ODLX --> DLQ2
    ODLX --> DLQ3
    ODLX --> DLQ4

    Q7 -.->|nack| EDLX
    EDLX --> DLQ5
```
