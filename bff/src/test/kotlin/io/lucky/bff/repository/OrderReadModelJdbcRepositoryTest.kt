package io.lucky.bff.repository

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.lucky.bff.domain.OrderReadModel
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
class OrderReadModelJdbcRepositoryTest(
    private val jdbcRepository: OrderReadModelJdbcRepository,
    private val jpaRepository: OrderReadModelRepository,
) : DescribeSpec({

        beforeEach {
            jpaRepository.deleteAll()
        }

        describe("upsert") {
            context("event_version이 기존보다 작은 경우") {
                it("무시된다") {
                    val base = orderReadModel(orderId = 1L, status = "SUBMITTED", eventVersion = 5L)
                    jdbcRepository.upsert(base)

                    val stale = orderReadModel(orderId = 1L, status = "FILLED", eventVersion = 3L)
                    jdbcRepository.upsert(stale)

                    val result = jpaRepository.findByOrderId(1L).shouldNotBeNull()
                    result.status shouldBe "SUBMITTED"
                    result.eventVersion shouldBe 5L
                }
            }

            context("event_version이 기존보다 큰 경우") {
                it("갱신된다") {
                    val base = orderReadModel(orderId = 2L, status = "SUBMITTED", eventVersion = 5L)
                    jdbcRepository.upsert(base)

                    val newer =
                        orderReadModel(
                            orderId = 2L,
                            status = "FILLED",
                            eventVersion = 7L,
                            filledQuantity = 10,
                        )
                    jdbcRepository.upsert(newer)

                    val result = jpaRepository.findByOrderId(2L).shouldNotBeNull()
                    result.status shouldBe "FILLED"
                    result.eventVersion shouldBe 7L
                    result.filledQuantity shouldBe 10
                }
            }
        }
    })

private fun orderReadModel(
    orderId: Long,
    status: String,
    eventVersion: Long,
    filledQuantity: Int = 0,
) = OrderReadModel(
    orderId = orderId,
    userId = 100L,
    stockCode = "005930",
    side = "BUY",
    orderType = "LIMIT",
    status = status,
    quantity = 10,
    filledQuantity = filledQuantity,
    price = BigDecimal("50000"),
    lockedAmount = BigDecimal("500000"),
    eventVersion = eventVersion,
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
)
