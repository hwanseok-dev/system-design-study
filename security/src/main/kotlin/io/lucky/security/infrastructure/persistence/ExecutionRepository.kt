package io.lucky.security.infrastructure.persistence

import io.lucky.security.domain.execution.Execution
import org.springframework.data.jpa.repository.JpaRepository

interface ExecutionRepository : JpaRepository<Execution, Long> {
    fun findByOrderId(orderId: Long): List<Execution>
}
