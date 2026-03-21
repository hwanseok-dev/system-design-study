package io.lucky.security.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.lucky.security.domain.balance.Balance
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
import io.lucky.security.infrastructure.persistence.OrderRepository
import io.lucky.security.infrastructure.persistence.StockHoldingRepository
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceTest(
    private val orderService: OrderService,
    private val balanceRepository: BalanceRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val orderRepository: OrderRepository,
    private val journalRepository: BalanceJournalRepository,
    private val outboxRepository: OutboxRepository,
) : DescribeSpec({

        beforeEach {
            outboxRepository.deleteAll()
            journalRepository.deleteAll()
            orderRepository.deleteAll()
            stockHoldingRepository.deleteAll()
            balanceRepository.deleteAll()
        }

        describe("create") {
            context("매수 주문 생성") {
                it("잔고가 동결되고 journal이 기록된다") {
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

                    order.id shouldNotBe 0L
                    order.status shouldBe OrderStatus.PENDING
                    order.lockedAmount shouldBeEqualComparingTo BigDecimal("500000")

                    val updated = balanceRepository.findByUserId(1L)!!
                    updated.cashAmount shouldBeEqualComparingTo BigDecimal("1000000")
                    updated.lockedAmount shouldBeEqualComparingTo BigDecimal("500000")
                    updated.availableCash() shouldBeEqualComparingTo BigDecimal("500000")

                    val journals = journalRepository.findAll()
                    journals.size shouldBe 1
                    journals[0].journalType.name shouldBe "ORDER_LOCK"
                    journals[0].cashDelta shouldBeEqualComparingTo BigDecimal("-500000")

                    val outbox = outboxRepository.findAll()
                    outbox.size shouldBe 1
                    outbox[0].aggregateType shouldBe AggregateType.ORDER
                    outbox[0].aggregateId shouldBe order.id
                    outbox[0].eventType shouldBe OutboxEventType.ORDER_VALIDATE
                    outbox[0].exchange shouldBe RabbitConfig.ORDER_EXCHANGE
                    outbox[0].routingKey shouldBe RabbitConfig.RK_ORDER_VALIDATE
                    outbox[0].published shouldBe false
                    outbox[0].payload shouldContain order.id.toString()
                }
            }

            context("매도 주문 생성") {
                it("주식이 동결되고 journal이 기록된다") {
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

                    order.status shouldBe OrderStatus.PENDING

                    val holding = stockHoldingRepository.findByUserIdAndStockCode(2L, "005930")!!
                    holding.quantity shouldBe 100
                    holding.lockedQuantity shouldBe 30
                    holding.availableQuantity() shouldBe 70

                    val journals = journalRepository.findAll()
                    journals.size shouldBe 1
                    journals[0].journalType.name shouldBe "STOCK_LOCK"
                    journals[0].quantityDelta shouldBe -30

                    val outbox = outboxRepository.findAll()
                    outbox.size shouldBe 1
                    outbox[0].aggregateType shouldBe AggregateType.ORDER
                    outbox[0].aggregateId shouldBe order.id
                    outbox[0].eventType shouldBe OutboxEventType.ORDER_VALIDATE
                    outbox[0].published shouldBe false
                }
            }

            context("잔고 부족 시") {
                it("예외 발생하고 주문이 생성되지 않는다") {
                    balanceRepository.save(Balance(userId = 3L, cashAmount = BigDecimal("100000")))

                    shouldThrow<IllegalStateException> {
                        orderService.create(
                            userId = 3L,
                            stockCode = "005930",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.BUY,
                            quantity = 10,
                            price = BigDecimal("50000"),
                        )
                    }

                    orderRepository.findByUserId(3L).size shouldBe 0
                    val balance = balanceRepository.findByUserId(3L)!!
                    balance.cashAmount shouldBeEqualComparingTo BigDecimal("100000")
                    balance.lockedAmount shouldBeEqualComparingTo BigDecimal.ZERO

                    outboxRepository.findAll().size shouldBe 0
                }
            }
        }
    })
