package io.lucky.security.application

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.lucky.security.domain.balance.Balance
import io.lucky.security.domain.balance.JournalType
import io.lucky.security.domain.balance.StockHolding
import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.order.OrderStatus
import io.lucky.security.domain.order.OrderType
import io.lucky.security.infrastructure.messaging.RabbitConfig
import io.lucky.security.infrastructure.outbox.AggregateType
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderValidationTest(
    private val orderService: OrderService,
    private val balanceRepository: BalanceRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val orderRepository: OrderRepository,
    private val journalRepository: BalanceJournalRepository,
    private val outboxRepository: OutboxRepository,
    private val executionRepository: ExecutionRepository,
    private val settlementRepository: SettlementRepository,
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

        describe("onValidated") {
            context("매수 주문이 검증 통과한 경우") {
                it("주문이 VALIDATED 상태가 되고 거래소 전송 outbox가 생성된다") {
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

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.VALIDATED

                    val outboxMessages = outboxRepository.findAll()
                    val executeOutbox = outboxMessages.filter { it.eventType == OutboxEventType.ORDER_EXECUTE }
                    executeOutbox.size shouldBe 1
                    executeOutbox[0].aggregateType shouldBe AggregateType.ORDER
                    executeOutbox[0].aggregateId shouldBe order.id
                    executeOutbox[0].exchange shouldBe RabbitConfig.ORDER_EXCHANGE
                    executeOutbox[0].routingKey shouldBe RabbitConfig.RK_ORDER_EXECUTE
                    executeOutbox[0].published shouldBe false
                }
            }

            context("매도 주문이 검증 통과한 경우") {
                it("주문이 VALIDATED 상태가 되고 거래소 전송 outbox가 생성된다") {
                    balanceRepository.save(Balance(userId = 2L, cashAmount = BigDecimal.ZERO))
                    stockHoldingRepository.save(
                        StockHolding(userId = 2L, stockCode = "005930", quantity = 100, avgBuyPrice = BigDecimal("50000")),
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

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.VALIDATED
                }
            }
        }

        describe("onRejected") {
            context("매수 주문이 검증 실패한 경우") {
                it("주문이 REJECTED 상태가 되고 잔고가 복원된다") {
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

                    orderService.onRejected(order.id, "Compliance check failed")

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.REJECTED

                    val balance = balanceRepository.findByUserId(1L)!!
                    balance.lockedAmount shouldBeEqualComparingTo BigDecimal.ZERO
                    balance.availableCash() shouldBeEqualComparingTo BigDecimal("1000000")

                    val journals = journalRepository.findAll()
                    val unlockJournal = journals.filter { it.journalType == JournalType.ORDER_UNLOCK }
                    unlockJournal.size shouldBe 1
                    unlockJournal[0].cashDelta shouldBeEqualComparingTo BigDecimal("500000")
                }
            }

            context("매도 주문이 검증 실패한 경우") {
                it("주문이 REJECTED 상태가 되고 주식이 복원된다") {
                    balanceRepository.save(Balance(userId = 2L, cashAmount = BigDecimal.ZERO))
                    stockHoldingRepository.save(
                        StockHolding(userId = 2L, stockCode = "005930", quantity = 100, avgBuyPrice = BigDecimal("50000")),
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

                    orderService.onRejected(order.id, "Stock suspended")

                    val updated = orderRepository.findById(order.id).get()
                    updated.status shouldBe OrderStatus.REJECTED

                    val holding = stockHoldingRepository.findByUserIdAndStockCode(2L, "005930")!!
                    holding.lockedQuantity shouldBe 0
                    holding.availableQuantity() shouldBe 100

                    val journals = journalRepository.findAll()
                    val unlockJournal = journals.filter { it.journalType == JournalType.STOCK_UNLOCK }
                    unlockJournal.size shouldBe 1
                    unlockJournal[0].quantityDelta shouldBe 30
                }
            }
        }
    })
