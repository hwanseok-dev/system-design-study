package io.lucky.security.infrastructure.sync

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.lucky.security.application.OrderService
import io.lucky.security.domain.balance.Balance
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.order.OrderType
import io.lucky.security.infrastructure.outbox.OutboxRepository
import io.lucky.security.infrastructure.persistence.BalanceJournalRepository
import io.lucky.security.infrastructure.persistence.BalanceRepository
import io.lucky.security.infrastructure.persistence.ExecutionRepository
import io.lucky.security.infrastructure.persistence.OrderRepository
import io.lucky.security.infrastructure.persistence.SettlementRepository
import io.lucky.security.infrastructure.persistence.StockHoldingRepository
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FilledQuantitySyncSchedulerTest(
    private val scheduler: FilledQuantitySyncScheduler,
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val balanceRepository: BalanceRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val executionRepository: ExecutionRepository,
    private val settlementRepository: SettlementRepository,
    private val journalRepository: BalanceJournalRepository,
    private val outboxRepository: OutboxRepository,
    private val redisTemplate: StringRedisTemplate,
) : DescribeSpec({

        beforeEach {
            outboxRepository.deleteAll()
            settlementRepository.deleteAll()
            executionRepository.deleteAll()
            journalRepository.deleteAll()
            orderRepository.deleteAll()
            stockHoldingRepository.deleteAll()
            balanceRepository.deleteAll()
            redisTemplate.execute { connection ->
                connection.serverCommands().flushDb()
            }
        }

        describe("sync") {
            context("Redis에 filledQuantity가 있는 경우") {
                it("RDB의 filled_quantity가 Redis 값으로 업데이트된다") {
                    balanceRepository.save(Balance(userId = 1L, cashAmount = BigDecimal("10000000")))
                    val order =
                        orderService.create(
                            userId = 1L,
                            stockCode = "005930",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.BUY,
                            quantity = 100,
                            price = BigDecimal("50000"),
                        )
                    orderService.onValidated(order.id)
                    orderService.submitOrder(order.id)

                    val filledKey = "{order:${order.id}}:filled_qty"
                    redisTemplate.opsForValue().set(filledKey, "40")

                    scheduler.sync()

                    val updated = orderRepository.findById(order.id).get()
                    updated.filledQuantity shouldBe 40
                }
            }

            context("RDB 값이 Redis보다 큰 경우") {
                it("RDB 값이 덮어쓰이지 않는다 (단조 증가 보장)") {
                    balanceRepository.save(Balance(userId = 1L, cashAmount = BigDecimal("10000000")))
                    val order =
                        orderService.create(
                            userId = 1L,
                            stockCode = "005930",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.BUY,
                            quantity = 100,
                            price = BigDecimal("50000"),
                        )
                    orderService.onValidated(order.id)
                    orderService.submitOrder(order.id)

                    val filledKey = "{order:${order.id}}:filled_qty"
                    redisTemplate.opsForValue().set(filledKey, "10")

                    scheduler.sync()

                    val afterFirst = orderRepository.findById(order.id).get()
                    afterFirst.filledQuantity shouldBe 10

                    // Redis value drops (e.g. Redis restart)
                    redisTemplate.opsForValue().set(filledKey, "5")

                    scheduler.sync()

                    val afterSecond = orderRepository.findById(order.id).get()
                    afterSecond.filledQuantity shouldBe 10
                }
            }

            context("sync를 두 번 실행해도") {
                it("결과가 동일하다 (멱등)") {
                    balanceRepository.save(Balance(userId = 1L, cashAmount = BigDecimal("10000000")))
                    val order =
                        orderService.create(
                            userId = 1L,
                            stockCode = "005930",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.BUY,
                            quantity = 100,
                            price = BigDecimal("50000"),
                        )
                    orderService.onValidated(order.id)
                    orderService.submitOrder(order.id)

                    val filledKey = "{order:${order.id}}:filled_qty"
                    redisTemplate.opsForValue().set(filledKey, "50")

                    scheduler.sync()
                    scheduler.sync()

                    val updated = orderRepository.findById(order.id).get()
                    updated.filledQuantity shouldBe 50
                }
            }
        }
    })
