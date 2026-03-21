package io.lucky.security.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.EntityNotFoundException
import io.lucky.security.domain.LogAction
import io.lucky.security.domain.execution.Execution
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.order.OrderStatus
import io.lucky.security.domain.settlement.Settlement
import io.lucky.security.infrastructure.messaging.RabbitConfig
import io.lucky.security.infrastructure.outbox.AggregateType
import io.lucky.security.infrastructure.outbox.OutboxEventType
import io.lucky.security.infrastructure.outbox.OutboxMessage
import io.lucky.security.infrastructure.outbox.OutboxRepository
import io.lucky.security.infrastructure.persistence.ExecutionRepository
import io.lucky.security.infrastructure.persistence.OrderRepository
import io.lucky.security.infrastructure.persistence.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

@Service
class ExecutionService(
    private val executionRepository: ExecutionRepository,
    private val orderRepository: OrderRepository,
    private val balanceService: BalanceService,
    private val outboxRepository: OutboxRepository,
    private val settlementRepository: SettlementRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun applyExecution(payload: ExecutionResultPayload) {
        val side = OrderSide.valueOf(payload.side)
        val amount = payload.price * payload.quantity.toBigDecimal()
        val settlementDate =
            payload.executedAt
                .plus(2, ChronoUnit.DAYS)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toLocalDate()

        // 1. Save execution
        val execution =
            executionRepository.save(
                Execution(
                    orderId = payload.orderId,
                    userId = payload.userId,
                    stockCode = payload.stockCode,
                    side = side,
                    quantity = payload.quantity,
                    price = payload.price,
                    amount = amount,
                    exchangeExecId = payload.exchangeExecId,
                    executedAt = payload.executedAt,
                    settlementDate = settlementDate,
                ),
            )
        execution.apply()

        // 2. Update order status
        val order =
            orderRepository.findById(payload.orderId).orElseThrow {
                EntityNotFoundException("Order", payload.orderId)
            }
        val newStatus = order.applyExecution(payload.quantity, payload.price)

        // 3. Update balance
        when (side) {
            OrderSide.BUY -> {
                balanceService.confirmBuyExecution(order.userId, execution.id, order.stockCode, amount)
                balanceService.addStock(order.userId, execution.id, order.stockCode, payload.quantity, payload.price)
            }
            OrderSide.SELL -> {
                balanceService.confirmSellExecution(order.userId, execution.id, order.stockCode, payload.quantity)
                balanceService.addCash(order.userId, execution.id, order.stockCode, amount)
            }
        }

        // 4. Publish ORDER_FILLED outbox if fully filled
        if (newStatus == OrderStatus.FILLED) {
            outboxRepository.save(
                OutboxMessage(
                    aggregateType = AggregateType.ORDER,
                    aggregateId = order.id,
                    eventType = OutboxEventType.ORDER_FILLED,
                    exchange = RabbitConfig.NOTIFICATION_EXCHANGE,
                    routingKey = RabbitConfig.RK_NOTIFY_ORDER_FILLED,
                    payload =
                        objectMapper.writeValueAsString(
                            OrderFilledPayload(
                                orderId = order.id,
                                userId = order.userId,
                                stockCode = order.stockCode,
                                side = order.side.name,
                                filledQuantity = order.filledQuantity,
                                avgFilledPrice = order.avgFilledPrice!!,
                            ),
                        ),
                ),
            )
        }

        // 5. Create settlement record
        settlementRepository.save(
            Settlement(
                executionId = execution.id,
                orderId = order.id,
                userId = order.userId,
                stockCode = order.stockCode,
                side = side,
                quantity = payload.quantity,
                amount = amount,
                settlementDate = settlementDate,
            ),
        )

        logger.info {
            "action=${LogAction.APPLY_EXECUTION} executionId=${execution.id} orderId=${payload.orderId} " +
                "userId=${payload.userId} side=$side quantity=${payload.quantity} price=${payload.price} " +
                "orderStatus=$newStatus"
        }
    }
}
