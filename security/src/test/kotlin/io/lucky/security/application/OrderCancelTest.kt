package io.lucky.security.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.lucky.security.domain.balance.Balance
import io.lucky.security.domain.balance.JournalType
import io.lucky.security.domain.balance.StockHolding
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.order.OrderStatus
import io.lucky.security.domain.order.OrderType
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
class OrderCancelTest(
    private val orderService: OrderService,
    private val executionService: ExecutionService,
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
            settlementRepository.deleteAll()
            executionRepository.deleteAll()
            journalRepository.deleteAll()
            orderRepository.deleteAll()
            stockHoldingRepository.deleteAll()
            balanceRepository.deleteAll()
        }

        describe("requestCancel") {
            context("SUBMITTED 상태의 주문인 경우") {
                it("취소 요청 outbox가 생성된다") {
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

                    orderService.requestCancel(order.id)

                    val cancelOutbox =
                        outboxRepository.findAll().filter { it.eventType == OutboxEventType.ORDER_CANCEL }
                    cancelOutbox.size shouldBe 1
                    cancelOutbox[0].aggregateId shouldBe order.id
                }
            }

            context("취소 불가 상태인 경우") {
                it("예외가 발생한다") {
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

                    shouldThrow<IllegalStateException> {
                        orderService.requestCancel(order.id)
                    }
                }
            }
        }

        describe("onCancelConfirmed") {
            context("매수 주문이 미체결 상태에서 취소된 경우") {
                it("주문 CANCELLED, 전액 잔고 복원, 알림 outbox 생성") {
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

                    orderService.onCancelConfirmed(order.id)

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.CANCELLED

                    val balance = balanceRepository.findByUserId(1L)!!
                    balance.lockedAmount shouldBeEqualComparingTo BigDecimal.ZERO
                    balance.availableCash() shouldBeEqualComparingTo BigDecimal("1000000")

                    val unlockJournals =
                        journalRepository.findAll().filter { it.journalType == JournalType.ORDER_UNLOCK }
                    unlockJournals.size shouldBe 1
                    unlockJournals[0].cashDelta shouldBeEqualComparingTo BigDecimal("500000")

                    val cancelledOutbox =
                        outboxRepository.findAll().filter { it.eventType == OutboxEventType.ORDER_CANCELLED }
                    cancelledOutbox.size shouldBe 1
                }
            }

            context("매도 주문이 미체결 상태에서 취소된 경우") {
                it("주문 CANCELLED, 주식 복원") {
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

                    orderService.onCancelConfirmed(order.id)

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.CANCELLED

                    val holding = stockHoldingRepository.findByUserIdAndStockCode(2L, "005930")!!
                    holding.lockedQuantity shouldBe 0
                    holding.availableQuantity() shouldBe 100

                    val unlockJournals =
                        journalRepository.findAll().filter { it.journalType == JournalType.STOCK_UNLOCK }
                    unlockJournals.size shouldBe 1
                    unlockJournals[0].quantityDelta shouldBe 30
                }
            }

            context("매수 주문이 부분 체결 후 취소된 경우") {
                it("미체결 수량만큼만 잔고 복원") {
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

                    // Partial execution: 4 shares at 49000
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

                    val partialOrder = orderRepository.findById(order.id).get()
                    partialOrder.status shouldBe OrderStatus.PARTIAL_FILLED
                    partialOrder.filledQuantity shouldBe 4

                    // Cancel remaining 6 shares
                    orderService.onCancelConfirmed(order.id)

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.CANCELLED

                    // Locked amount should be restored for remaining 6 * 50000 = 300000
                    val balance = balanceRepository.findByUserId(1L)!!
                    balance.lockedAmount shouldBeEqualComparingTo BigDecimal.ZERO
                    // Total cash: 1000000 - 4*49000 (executed) = 804000
                    balance.cashAmount shouldBeEqualComparingTo BigDecimal("804000")

                    val holding = stockHoldingRepository.findByUserIdAndStockCode(1L, "005930")!!
                    holding.quantity shouldBe 4
                }
            }

            context("매수 주문이 주문가와 다른 가격에 부분 체결 후 취소된 경우") {
                it("체결 가격 차이를 반영한 정확한 잔여 lockedAmount가 복원된다") {
                    // Limit 50000 * 10 = 500000 locked
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

                    // Filled 4 shares at 49000 (cheaper than limit)
                    // consumed lock = 4 * 49000 = 196000, remaining lock = 500000 - 196000 = 304000
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

                    val partialOrder = orderRepository.findById(order.id).get()
                    // remaining lock = 304000, not 6 * 50000 = 300000
                    partialOrder.lockedAmount shouldBeEqualComparingTo BigDecimal("304000")

                    orderService.onCancelConfirmed(order.id)

                    // 304000 unlocked (not 300000)
                    val balance = balanceRepository.findByUserId(1L)!!
                    balance.lockedAmount shouldBeEqualComparingTo BigDecimal.ZERO
                    // cash = 1000000 - 196000 (executed) = 804000
                    balance.cashAmount shouldBeEqualComparingTo BigDecimal("804000")
                    balance.availableCash() shouldBeEqualComparingTo BigDecimal("804000")
                }
            }

            context("매도 주문이 부분 체결 후 취소된 경우") {
                it("미체결 수량만큼만 주식 복원") {
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

                    // Partial execution: 10 shares
                    executionService.applyExecution(
                        ExecutionResultPayload(
                            orderId = order.id,
                            userId = 2L,
                            stockCode = "005930",
                            side = "SELL",
                            quantity = 10,
                            price = BigDecimal("55000"),
                            exchangeExecId = UUID.randomUUID().toString(),
                            executedAt = Instant.now(),
                        ),
                    )

                    val partialOrder = orderRepository.findById(order.id).get()
                    partialOrder.status shouldBe OrderStatus.PARTIAL_FILLED

                    // Cancel remaining 20 shares
                    orderService.onCancelConfirmed(order.id)

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.CANCELLED

                    val holding = stockHoldingRepository.findByUserIdAndStockCode(2L, "005930")!!
                    // 100 - 10 (sold) = 90, locked restored for 20
                    holding.quantity shouldBe 90
                    holding.lockedQuantity shouldBe 0
                    holding.availableQuantity() shouldBe 90

                    // Cash increased from sell execution
                    val balance = balanceRepository.findByUserId(2L)!!
                    balance.cashAmount shouldBeEqualComparingTo BigDecimal("550000")
                }
            }
        }
    })
