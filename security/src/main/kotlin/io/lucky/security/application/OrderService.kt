package io.lucky.security.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.EntityNotFoundException
import io.lucky.security.domain.LogAction
import io.lucky.security.domain.order.Order
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.order.OrderStatus
import io.lucky.security.domain.order.OrderType
import io.lucky.security.infrastructure.messaging.RabbitConfig
import io.lucky.security.infrastructure.outbox.AggregateType
import io.lucky.security.infrastructure.outbox.OutboxEventType
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
                order.initLockedAmount()
                val saved = orderRepository.save(order)
                balanceService.lockCache(order.userId, saved.id, order.stockCode, order.lockedAmount)
            }
            OrderSide.SELL -> {
                orderRepository.save(order)
                balanceService.lockStock(order.userId, order.id, order.stockCode, order.quantity)
            }
        }

        logger.info {
            "action=${LogAction.CREATE_ORDER} orderId=${order.id} userId=$userId " +
                "stockCode=$stockCode side=$side quantity=$quantity price=$price"
        }

        outboxRepository.save(
            OutboxMessage(
                aggregateType = AggregateType.ORDER,
                aggregateId = order.id,
                eventType = OutboxEventType.ORDER_VALIDATE,
                exchange = RabbitConfig.ORDER_EXCHANGE,
                routingKey = RabbitConfig.RK_ORDER_VALIDATE,
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

    @Transactional
    fun onValidated(orderId: Long) {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                EntityNotFoundException("Order", orderId)
            }
        order.validate()

        outboxRepository.save(
            OutboxMessage(
                aggregateType = AggregateType.ORDER,
                aggregateId = orderId,
                eventType = OutboxEventType.ORDER_EXECUTE,
                exchange = RabbitConfig.ORDER_EXCHANGE,
                routingKey = RabbitConfig.RK_ORDER_EXECUTE,
                payload =
                    objectMapper.writeValueAsString(
                        OrderExecutePayload(
                            orderId = order.id,
                            userId = order.userId,
                            stockCode = order.stockCode,
                            side = order.side.name,
                            orderType = order.orderType.name,
                            quantity = order.quantity,
                            price = order.price,
                        ),
                    ),
            ),
        )

        logger.info {
            "action=${LogAction.ORDER_VALIDATED} orderId=$orderId userId=${order.userId} status=${order.status}"
        }
    }

    @Transactional
    fun onRejected(
        orderId: Long,
        reason: String,
    ) {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                EntityNotFoundException("Order", orderId)
            }
        order.reject()

        when (order.side) {
            OrderSide.BUY -> balanceService.unlockCash(order.userId, order.id, order.stockCode, order.lockedAmount)
            OrderSide.SELL -> balanceService.unlockStock(order.userId, order.id, order.stockCode, order.quantity)
        }

        logger.info {
            "action=${LogAction.ORDER_REJECTED} orderId=$orderId userId=${order.userId} reason=$reason"
        }
    }

    @Transactional
    fun submitOrder(orderId: Long) {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                EntityNotFoundException("Order", orderId)
            }
        order.submit()
        logger.info { "action=${LogAction.SUBMIT_ORDER} orderId=$orderId userId=${order.userId}" }
    }

    @Transactional
    fun requestCancel(orderId: Long): Order {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                EntityNotFoundException("Order", orderId)
            }
        check(order.status in setOf(OrderStatus.SUBMITTED, OrderStatus.PARTIAL_FILLED)) {
            "Cannot cancel order in status ${order.status}"
        }

        outboxRepository.save(
            OutboxMessage(
                aggregateType = AggregateType.ORDER,
                aggregateId = orderId,
                eventType = OutboxEventType.ORDER_CANCEL,
                exchange = RabbitConfig.ORDER_EXCHANGE,
                routingKey = RabbitConfig.RK_ORDER_CANCEL,
                payload =
                    objectMapper.writeValueAsString(
                        OrderCancelPayload(
                            orderId = order.id,
                            userId = order.userId,
                            stockCode = order.stockCode,
                            side = order.side.name,
                        ),
                    ),
            ),
        )

        logger.info {
            "action=${LogAction.REQUEST_CANCEL} orderId=$orderId userId=${order.userId}"
        }

        return order
    }

    @Transactional
    fun onCancelConfirmed(orderId: Long) {
        val order =
            orderRepository.findById(orderId).orElseThrow {
                EntityNotFoundException("Order", orderId)
            }
        val remainingQty = order.cancel()

        when (order.side) {
            OrderSide.BUY -> {
                val remainingAmount = calculateRemainingLockAmount(order, remainingQty)
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
                                    calculateRemainingLockAmount(order, remainingQty)
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

    @Transactional(readOnly = true)
    fun findById(orderId: Long): Order =
        orderRepository.findById(orderId).orElseThrow {
            EntityNotFoundException("Order", orderId)
        }

    @Transactional(readOnly = true)
    fun findByUserId(userId: Long): List<Order> = orderRepository.findByUserId(userId)

    // Returns actual remaining lock, not price * remainingQty.
    // Execution price can differ from order price, so lockedAmount is tracked per execution.
    private fun calculateRemainingLockAmount(
        order: Order,
        remainingQty: Int,
    ): BigDecimal = order.lockedAmount
}
