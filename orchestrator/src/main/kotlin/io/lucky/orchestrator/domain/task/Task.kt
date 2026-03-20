package io.lucky.orchestrator.domain.task

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "task")
class Task(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, unique = true)
    val name: String,
    @Column(name = "queue_name", nullable = false)
    val queueName: String,
    @Column(name = "expected_count", nullable = false)
    val expectedCount: Int = 1,
)
