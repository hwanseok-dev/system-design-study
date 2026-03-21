package io.lucky.security.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.EntityNotFoundException
import io.lucky.security.domain.LogAction
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.infrastructure.messaging.RabbitConfig
import io.lucky.security.infrastructure.outbox.AggregateType
import io.lucky.security.infrastructure.outbox.OutboxEventType
import io.lucky.security.infrastructure.outbox.OutboxMessage
import io.lucky.security.infrastructure.outbox.OutboxRepository
import io.lucky.security.infrastructure.persistence.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class OrderCancelDbService(
    private val orderRepository: OrderRepository,
    private val balanceService: BalanceService,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun applyCancelToDb(orderId: Long) {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                EntityNotFoundException("Order", orderId)
            }
        val remainingQty = order.cancel()

        when (order.side) {
            OrderSide.BUY -> {
                val remainingAmount = order.lockedAmount
                balanceService.unlockCash(order.userId, order.id, order.stockCode, remainingAmount)
            }
            OrderSide.SELL -> {
                balanceService.unlockStock(order.userId, order.id, order.stockCode, remainingQty)
            }
        }

        outboxRepository.save(
            OutboxMessage(
                aggregateType = AggregateType.ORDER,
                aggregateId = orderId,
                eventType = OutboxEventType.ORDER_CANCELLED,
                exchange = RabbitConfig.NOTIFICATION_EXCHANGE,
                routingKey = RabbitConfig.RK_NOTIFY_ORDER_CANCELLED,
                payload =
                    objectMapper.writeValueAsString(
                        OrderCancelledPayload(
                            orderId = order.id,
                            userId = order.userId,
                            stockCode = order.stockCode,
                            side = order.side.name,
                            cancelledQuantity = remainingQty,
                            restoredAmount =
                                if (order.side == OrderSide.BUY) {
                                    order.lockedAmount
                                } else {
                                    null
                                },
                        ),
                    ),
            ),
        )

        logger.info {
            "action=${LogAction.ORDER_CANCELLED} orderId=$orderId userId=${order.userId} " +
                "remainingQty=$remainingQty"
        }
    }
}
