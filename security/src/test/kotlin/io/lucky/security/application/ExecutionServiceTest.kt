package io.lucky.security.application

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.lucky.security.domain.balance.Balance
import io.lucky.security.domain.balance.JournalType
import io.lucky.security.domain.balance.StockHolding
import io.lucky.security.domain.execution.ExecutionStatus
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.order.OrderStatus
import io.lucky.security.domain.order.OrderType
import io.lucky.security.domain.settlement.SettlementStatus
import io.lucky.security.infrastructure.outbox.OutboxEventType
import io.lucky.security.infrastructure.outbox.OutboxRepository
import io.lucky.security.infrastructure.persistence.BalanceJournalRepository
import io.lucky.security.infrastructure.persistence.BalanceRepository
import io.lucky.security.infrastructure.persistence.ExecutionRepository
import io.lucky.security.infrastructure.persistence.OrderRepository
import io.lucky.security.infrastructure.persistence.SettlementRepository
import io.lucky.security.infrastructure.persistence.StockHoldingRepository
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExecutionServiceTest(
    private val executionService: ExecutionService,
    private val orderService: OrderService,
    private val balanceRepository: BalanceRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val orderRepository: OrderRepository,
    private val executionRepository: ExecutionRepository,
    private val settlementRepository: SettlementRepository,
    private val journalRepository: BalanceJournalRepository,
    private val outboxRepository: OutboxRepository,
) : DescribeSpec({

        beforeEach {
            outboxRepository.deleteAll()
            journalRepository.deleteAll()
            settlementRepository.deleteAll()
            executionRepository.deleteAll()
            orderRepository.deleteAll()
            stockHoldingRepository.deleteAll()
            balanceRepository.deleteAll()
        }

        describe("applyExecution") {
            context("매수 주문이 전량 체결된 경우") {
                it("execution 저장, 주문 FILLED, 잔고 변경, settlement 생성, outbox 생성") {
                    balanceRepository.save(Balance(userId = 1L, cashAmount = BigDecimal("1000000")))
                    val order =
                        orderService.create(
                            userId = 1L,
                            stockCode = "005930",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.BUY,
                            quantity = 10,
                            price = BigDecimal("50000"),
                        )
                    orderService.onValidated(order.id)
                    orderService.submitOrder(order.id)

                    executionService.applyExecution(
                        ExecutionResultPayload(
                            orderId = order.id,
                            userId = 1L,
                            stockCode = "005930",
                            side = "BUY",
                            quantity = 10,
                            price = BigDecimal("50000"),
                            exchangeExecId = UUID.randomUUID().toString(),
                            executedAt = Instant.now(),
                        ),
                    )

                    // Execution saved and APPLIED
                    val executions = executionRepository.findByOrderId(order.id)
                    executions.size shouldBe 1
                    executions[0].status shouldBe ExecutionStatus.APPLIED
                    executions[0].quantity shouldBe 10
                    executions[0].price shouldBeEqualComparingTo BigDecimal("50000")

                    // Order FILLED
                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.FILLED
                    updated.filledQuantity shouldBe 10
                    updated.avgFilledPrice.shouldNotBeNull()
                    updated.avgFilledPrice!! shouldBeEqualComparingTo BigDecimal("50000")

                    // Balance: lockedAmount decreased, cashAmount decreased
                    val balance = balanceRepository.findByUserId(1L)!!
                    balance.cashAmount shouldBeEqualComparingTo BigDecimal("500000")
                    balance.lockedAmount shouldBeEqualComparingTo BigDecimal.ZERO

                    // StockHolding created
                    val holding = stockHoldingRepository.findByUserIdAndStockCode(1L, "005930")!!
                    holding.quantity shouldBe 10
                    holding.avgBuyPrice shouldBeEqualComparingTo BigDecimal("50000")

                    // Settlement created
                    val settlements = settlementRepository.findAll()
                    settlements.size shouldBe 1
                    settlements[0].status shouldBe SettlementStatus.PENDING
                    settlements[0].quantity shouldBe 10
                    settlements[0].amount shouldBeEqualComparingTo BigDecimal("500000")

                    // ORDER_FILLED outbox created
                    val filledOutbox = outboxRepository.findAll().filter { it.eventType == OutboxEventType.ORDER_FILLED }
                    filledOutbox.size shouldBe 1
                    filledOutbox[0].aggregateId shouldBe order.id
                }
            }

            context("매수 주문이 부분 체결된 경우") {
                it("주문 PARTIAL_FILLED, filledQuantity 일부만 증가, ORDER_FILLED outbox 미생성") {
                    balanceRepository.save(Balance(userId = 1L, cashAmount = BigDecimal("1000000")))
                    val order =
                        orderService.create(
                            userId = 1L,
                            stockCode = "005930",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.BUY,
                            quantity = 10,
                            price = BigDecimal("50000"),
                        )
                    orderService.onValidated(order.id)
                    orderService.submitOrder(order.id)

                    executionService.applyExecution(
                        ExecutionResultPayload(
                            orderId = order.id,
                            userId = 1L,
                            stockCode = "005930",
                            side = "BUY",
                            quantity = 5,
                            price = BigDecimal("50000"),
                            exchangeExecId = UUID.randomUUID().toString(),
                            executedAt = Instant.now(),
                        ),
                    )

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.PARTIAL_FILLED
                    updated.filledQuantity shouldBe 5

                    val balance = balanceRepository.findByUserId(1L)!!
                    balance.lockedAmount shouldBeEqualComparingTo BigDecimal("250000")

                    val filledOutbox = outboxRepository.findAll().filter { it.eventType == OutboxEventType.ORDER_FILLED }
                    filledOutbox.size shouldBe 0
                }
            }

            context("매도 주문이 전량 체결된 경우") {
                it("execution 저장, 주문 FILLED, stockHolding 감소, 현금 증가, settlement 생성") {
                    balanceRepository.save(Balance(userId = 2L, cashAmount = BigDecimal.ZERO))
                    stockHoldingRepository.save(
                        StockHolding(
                            userId = 2L,
                            stockCode = "005930",
                            quantity = 100,
                            avgBuyPrice = BigDecimal("50000"),
                        ),
                    )
                    val order =
                        orderService.create(
                            userId = 2L,
                            stockCode = "005930",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.SELL,
                            quantity = 30,
                            price = BigDecimal("55000"),
                        )
                    orderService.onValidated(order.id)
                    orderService.submitOrder(order.id)

                    executionService.applyExecution(
                        ExecutionResultPayload(
                            orderId = order.id,
                            userId = 2L,
                            stockCode = "005930",
                            side = "SELL",
                            quantity = 30,
                            price = BigDecimal("55000"),
                            exchangeExecId = UUID.randomUUID().toString(),
                            executedAt = Instant.now(),
                        ),
                    )

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.FILLED
                    updated.filledQuantity shouldBe 30
                    updated.avgFilledPrice.shouldNotBeNull()
                    updated.avgFilledPrice!! shouldBeEqualComparingTo BigDecimal("55000")

                    // StockHolding: quantity decreased, lockedQuantity decreased
                    val holding = stockHoldingRepository.findByUserIdAndStockCode(2L, "005930")!!
                    holding.quantity shouldBe 70
                    holding.lockedQuantity shouldBe 0

                    // Cash increased
                    val balance = balanceRepository.findByUserId(2L)!!
                    balance.cashAmount shouldBeEqualComparingTo BigDecimal("1650000")

                    // Settlement
                    val settlements = settlementRepository.findAll()
                    settlements.size shouldBe 1
                    settlements[0].side shouldBe OrderSide.SELL
                    settlements[0].amount shouldBeEqualComparingTo BigDecimal("1650000")

                    // Journals
                    val sellJournals = journalRepository.findAll().filter { it.journalType == JournalType.SELL_EXECUTION }
                    sellJournals.size shouldBe 2
                }
            }

            context("매수 주문이 2번에 걸쳐 전량 체결된 경우") {
                it("avgFilledPrice가 가중평균으로 계산되고 두번째 체결 후 FILLED") {
                    balanceRepository.save(Balance(userId = 1L, cashAmount = BigDecimal("1000000")))
                    val order =
                        orderService.create(
                            userId = 1L,
                            stockCode = "005930",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.BUY,
                            quantity = 10,
                            price = BigDecimal("50000"),
                        )
                    orderService.onValidated(order.id)
                    orderService.submitOrder(order.id)

                    // First partial execution at 49000
                    executionService.applyExecution(
                        ExecutionResultPayload(
                            orderId = order.id,
                            userId = 1L,
                            stockCode = "005930",
                            side = "BUY",
                            quantity = 4,
                            price = BigDecimal("49000"),
                            exchangeExecId = UUID.randomUUID().toString(),
                            executedAt = Instant.now(),
                        ),
                    )

                    val partial = orderRepository.findById(order.id).get()
                    partial.status shouldBe OrderStatus.PARTIAL_FILLED
                    partial.filledQuantity shouldBe 4

                    // Second execution at 48000
                    executionService.applyExecution(
                        ExecutionResultPayload(
                            orderId = order.id,
                            userId = 1L,
                            stockCode = "005930",
                            side = "BUY",
                            quantity = 6,
                            price = BigDecimal("48000"),
                            exchangeExecId = UUID.randomUUID().toString(),
                            executedAt = Instant.now(),
                        ),
                    )

                    val filled = orderRepository.findById(order.id).get()
                    filled.status shouldBe OrderStatus.FILLED
                    filled.filledQuantity shouldBe 10
                    // avgFilledPrice = (4*49000 + 6*48000) / 10 = (196000+288000)/10 = 48400
                    filled.avgFilledPrice.shouldNotBeNull()
                    filled.avgFilledPrice!! shouldBeEqualComparingTo BigDecimal("48400")

                    val executions = executionRepository.findByOrderId(order.id)
                    executions.size shouldBe 2

                    val settlements = settlementRepository.findAll()
                    settlements.size shouldBe 2
                }
            }
        }
    })
