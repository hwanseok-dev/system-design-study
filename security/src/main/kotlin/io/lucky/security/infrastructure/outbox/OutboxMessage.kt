package io.lucky.security.infrastructure.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "outbox")
class OutboxMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "aggregate_type", nullable = false)
    val aggregateType: String,
    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: Long,
    @Column(name = "event_type", nullable = false)
    val eventType: String,
    @Column(nullable = false)
    val exchange: String,
    @Column(name = "routing_key", nullable = false)
    val routingKey: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val payload: String,
    @Column(nullable = false)
    var published: Boolean = false,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
