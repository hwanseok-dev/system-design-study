package io.lucky.security.infrastructure.persistence

import io.lucky.security.domain.order.Order
import io.lucky.security.domain.order.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByUserId(userId: Long): List<Order>

    fun findByStatusIn(statuses: List<OrderStatus>): List<Order>
}
