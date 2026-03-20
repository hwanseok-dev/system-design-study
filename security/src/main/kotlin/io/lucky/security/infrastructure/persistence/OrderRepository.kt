package io.lucky.security.infrastructure.persistence

import io.lucky.security.domain.order.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByUserId(userId: Long): List<Order>
}
