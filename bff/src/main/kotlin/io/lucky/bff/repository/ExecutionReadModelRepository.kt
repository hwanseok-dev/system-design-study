package io.lucky.bff.repository

import io.lucky.bff.domain.ExecutionReadModel
import org.springframework.data.jpa.repository.JpaRepository

interface ExecutionReadModelRepository : JpaRepository<ExecutionReadModel, Long> {
    fun findByOrderId(orderId: Long): List<ExecutionReadModel>
}
