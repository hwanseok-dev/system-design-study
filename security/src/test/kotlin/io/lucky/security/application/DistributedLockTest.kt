package io.lucky.security.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.lucky.security.domain.OrderCreationException
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
import org.redisson.api.RedissonClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
class DistributedLockTest(
    private val orderService: OrderService,
    private val balanceRepository: BalanceRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val orderRepository: OrderRepository,
    private val executionRepository: ExecutionRepository,
    private val settlementRepository: SettlementRepository,
    private val journalRepository: BalanceJournalRepository,
    private val outboxRepository: OutboxRepository,
    private val redisTemplate: StringRedisTemplate,
    private val redissonClient: RedissonClient,
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

        describe("create with distributed lock") {
            context("동일 사용자가 동시에 주문을 생성하는 경우") {
                it("잔고를 초과하는 주문은 거부된다") {
                    balanceRepository.save(Balance(userId = 1L, cashAmount = BigDecimal("1000000")))

                    val executor = Executors.newFixedThreadPool(2)
                    val latch = CountDownLatch(2)
                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    repeat(2) {
                        executor.submit {
                            try {
                                orderService.create(
                                    userId = 1L,
                                    stockCode = "005930",
                                    orderType = OrderType.LIMIT,
                                    side = OrderSide.BUY,
                                    quantity = 15,
                                    price = BigDecimal("50000"),
                                )
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                failCount.incrementAndGet()
                            } finally {
                                latch.countDown()
                            }
                        }
                    }

                    latch.await(10, TimeUnit.SECONDS)
                    executor.shutdown()

                    // 15 * 50000 = 750000 per order, total cash = 1000000
                    // First order succeeds (750000 locked), second fails (250000 < 750000)
                    successCount.get() shouldBe 1
                    failCount.get() shouldBe 1

                    val balance = balanceRepository.findByUserId(1L)!!
                    balance.lockedAmount shouldBeEqualComparingTo BigDecimal("750000")
                    balance.availableCash() shouldBeEqualComparingTo BigDecimal("250000")
                }
            }

            context("락 획득에 실패한 경우") {
                it("OrderCreationException이 발생한다") {
                    balanceRepository.save(Balance(userId = 1L, cashAmount = BigDecimal("1000000")))

                    // Pre-acquire the lock from a different thread (RLock is reentrant per thread)
                    val lockKey = "lock:{user:1}:balance"
                    val externalLock = redissonClient.getLock(lockKey)
                    val lockLatch = CountDownLatch(1)
                    val unlockLatch = CountDownLatch(1)

                    val lockThread =
                        Thread {
                            externalLock.lock(30, TimeUnit.SECONDS)
                            lockLatch.countDown()
                            unlockLatch.await()
                            externalLock.unlock()
                        }
                    lockThread.start()
                    lockLatch.await()

                    try {
                        shouldThrow<OrderCreationException> {
                            orderService.create(
                                userId = 1L,
                                stockCode = "005930",
                                orderType = OrderType.LIMIT,
                                side = OrderSide.BUY,
                                quantity = 1,
                                price = BigDecimal("50000"),
                            )
                        }
                    } finally {
                        unlockLatch.countDown()
                        lockThread.join()
                    }
                }
            }
        }
    })
