package io.lucky.security.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.EntityNotFoundException
import io.lucky.security.domain.LogAction
import io.lucky.security.domain.execution.Execution
import io.lucky.security.domain.order.Order
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.settlement.Settlement
import io.lucky.security.infrastructure.messaging.RabbitConfig
import io.lucky.security.infrastructure.outbox.AggregateType
import io.lucky.security.infrastructure.outbox.OutboxEventType
import io.lucky.security.infrastructure.outbox.OutboxMessage
import io.lucky.security.infrastructure.outbox.OutboxRepository
import io.lucky.security.infrastructure.persistence.ExecutionBatchRepository
import io.lucky.security.infrastructure.persistence.ExecutionRepository
import io.lucky.security.infrastructure.persistence.OrderRepository
import io.lucky.security.infrastructure.persistence.SettlementBatchRepository
import io.lucky.security.infrastructure.persistence.SettlementRepository
import jakarta.persistence.OptimisticLockException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

@Service
class ExecutionDbService(
    private val executionRepository: ExecutionRepository,
    private val orderRepository: OrderRepository,
    private val balanceService: BalanceService,
    private val outboxRepository: OutboxRepository,
    private val settlementRepository: SettlementRepository,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
    private val executionBatchRepository: ExecutionBatchRepository,
    private val settlementBatchRepository: SettlementBatchRepository,
) {
    companion object {
        private val FILLED_QTY_TTL = Duration.ofHours(24)
    }

    @Retryable(
        value = [OptimisticLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100),
    )
    @Transactional
    fun applyExecutionToDb(
        payload: ExecutionResultPayload,
        totalFilled: Long,
    ) {
        val side = OrderSide.valueOf(payload.side)
        val amount = payload.price * payload.quantity.toBigDecimal()
        val settlementDate =
            payload.executedAt
                .plus(2, ChronoUnit.DAYS)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toLocalDate()

        val cancelKey = "{order:${payload.orderId}}:cancelled"
        if (redisTemplate.hasKey(cancelKey)) {
            logger.info {
                "action=${LogAction.EXECUTION_SKIPPED_CANCELLED} orderId=${payload.orderId} " +
                    "exchangeExecId=${payload.exchangeExecId} phase=doubleCheck"
            }
            return
        }

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

        val order =
            orderRepository.findById(payload.orderId).orElseThrow {
                EntityNotFoundException("Order", payload.orderId)
            }
        order.applyExecution(payload.quantity, payload.price)
        applyBalance(order, side, payload.quantity, payload.price)

        checkFilledAndPublish(order, totalFilled)

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
                "totalFilled=$totalFilled orderQuantity=${order.quantity}"
        }
    }

    @Retryable(
        value = [OptimisticLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100),
    )
    @Transactional
    fun applyExecutionBatch(
        orderId: Long,
        executions: List<ExecutionResultPayload>,
        totalFilled: Long,
    ) {
        val cancelKey = "{order:$orderId}:cancelled"
        if (redisTemplate.hasKey(cancelKey)) {
            logger.info {
                "action=${LogAction.EXECUTION_SKIPPED_CANCELLED} orderId=$orderId phase=batchDoubleCheck"
            }
            return
        }

        executionBatchRepository.batchInsert(executions)

        val order =
            orderRepository.findById(orderId).orElseThrow {
                EntityNotFoundException("Order", orderId)
            }
        val side = OrderSide.valueOf(executions.first().side)

        for (exec in executions) {
            order.applyExecution(exec.quantity, exec.price)
            applyBalance(order, side, exec.quantity, exec.price)
        }

        settlementBatchRepository.batchInsert(executions, side.name)

        checkFilledAndPublish(order, totalFilled)

        logger.info {
            "action=${LogAction.APPLY_EXECUTION_BATCH} orderId=$orderId " +
                "batchSize=${executions.size} totalFilled=$totalFilled orderQuantity=${order.quantity}"
        }
    }

    private fun applyBalance(
        order: Order,
        side: OrderSide,
        quantity: Int,
        price: BigDecimal,
    ) {
        val amount = price * quantity.toBigDecimal()
        when (side) {
            OrderSide.BUY -> {
                balanceService.confirmBuyExecution(order.userId, order.id, order.stockCode, amount)
                balanceService.addStock(order.userId, order.id, order.stockCode, quantity, price)
                order.consumeLockedAmount(amount)
            }
            OrderSide.SELL -> {
                balanceService.confirmSellExecution(order.userId, order.id, order.stockCode, quantity)
                balanceService.addCash(order.userId, order.id, order.stockCode, amount)
            }
        }
    }

    private fun checkFilledAndPublish(
        order: Order,
        totalFilled: Long,
    ) {
        val filledKey = "{order:${order.id}}:filled_qty"
        if (totalFilled >= order.quantity.toLong()) {
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

            if (totalFilled == order.quantity.toLong()) {
                redisTemplate.expire(filledKey, FILLED_QTY_TTL)
            }
        }
    }
}
