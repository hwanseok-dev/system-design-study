package io.lucky.bff.repository

import io.lucky.bff.domain.OrderReadModel
import org.springframework.data.jpa.repository.JpaRepository

interface OrderReadModelRepository : JpaRepository<OrderReadModel, Long> {
    fun findByUserIdAndStatusIn(
        userId: Long,
        statuses: List<String>,
    ): List<OrderReadModel>

    fun findByOrderId(orderId: Long): OrderReadModel?
}
