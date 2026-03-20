package io.lucky.security.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.EntityNotFoundException
import io.lucky.security.domain.LogAction
import io.lucky.security.domain.order.Order
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.order.OrderType
import io.lucky.security.infrastructure.messaging.RabbitConfig
import io.lucky.security.infrastructure.outbox.OutboxMessage
import io.lucky.security.infrastructure.outbox.OutboxRepository
import io.lucky.security.infrastructure.persistence.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val balanceService: BalanceService,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun create(
        userId: Long,
        stockCode: String,
        orderType: OrderType,
        side: OrderSide,
        quantity: Int,
        price: BigDecimal?,
    ): Order {
        val order =
            Order(
                userId = userId,
                stockCode = stockCode,
                orderType = orderType,
                side = side,
                quantity = quantity,
                price = price,
            )

        when (order.side) {
            OrderSide.BUY -> {
                val lockAmount = calculateLockAmount(order)
                order.lockedAmount = lockAmount
                val saved = orderRepository.save(order)
                balanceService.lockForBuy(order.userId, saved.id, order.stockCode, lockAmount)
            }
            OrderSide.SELL -> {
                orderRepository.save(order)
                balanceService.lockForSell(order.userId, order.id, order.stockCode, order.quantity)
            }
        }

        logger.info {
            "action=${LogAction.CREATE_ORDER} orderId=${order.id} userId=$userId " +
                "stockCode=$stockCode side=$side quantity=$quantity price=$price"
        }

        outboxRepository.save(
            OutboxMessage(
                aggregateType = "ORDER",
                aggregateId = order.id,
                eventType = "ORDER_VALIDATE",
                exchange = RabbitConfig.ORDER_EXCHANGE,
                routingKey = "order.validate",
                payload =
                    objectMapper.writeValueAsString(
                        OrderValidatePayload(
                            orderId = order.id,
                            userId = order.userId,
                            stockCode = order.stockCode,
                            side = order.side.name,
                            quantity = order.quantity,
                            price = order.price,
                        ),
                    ),
            ),
        )

        return order
    }

    @Transactional(readOnly = true)
    fun findById(orderId: Long): Order =
        orderRepository.findById(orderId).orElseThrow {
            EntityNotFoundException("Order", orderId)
        }

    @Transactional(readOnly = true)
    fun findByUserId(userId: Long): List<Order> = orderRepository.findByUserId(userId)

    private fun calculateLockAmount(order: Order): BigDecimal =
        when (order.orderType) {
            OrderType.LIMIT -> order.price!! * order.quantity.toBigDecimal()
            OrderType.MARKET -> throw UnsupportedOperationException("Market order lock TBD")
        }
}
