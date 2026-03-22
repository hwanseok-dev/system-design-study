package io.lucky.security.infrastructure.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.LogAction
import io.lucky.security.domain.order.OrderStatus
import io.lucky.security.infrastructure.persistence.OrderRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class FilledQuantitySyncScheduler(
    private val orderRepository: OrderRepository,
    private val redisTemplate: StringRedisTemplate,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Scheduled(fixedDelay = 5000)
    fun sync() {
        val activeOrders =
            orderRepository.findByStatusIn(
                listOf(OrderStatus.SUBMITTED, OrderStatus.PARTIAL_FILLED),
            )

        for (order in activeOrders) {
            val filledKey = "{order:${order.id}}:filled_qty"
            val redisFilled = redisTemplate.opsForValue().get(filledKey)?.toInt() ?: continue

            jdbcTemplate.update(
                "UPDATE stock_order SET filled_quantity = ? WHERE id = ? AND filled_quantity < ?",
                redisFilled,
                order.id,
                redisFilled,
            )
        }

        if (activeOrders.isNotEmpty()) {
            logger.info {
                "action=${LogAction.SYNC_FILLED_QTY} orders=${activeOrders.size}"
            }
        }
    }
}
