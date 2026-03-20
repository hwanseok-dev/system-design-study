package io.lucky.security.domain.order

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.RoundingMode

class OrderTest :
    DescribeSpec({

        fun createOrder(
            quantity: Int = 1000,
            price: BigDecimal = BigDecimal("50000"),
        ): Order =
            Order(
                userId = 1L,
                stockCode = "005930",
                orderType = OrderType.LIMIT,
                side = OrderSide.BUY,
                quantity = quantity,
                price = price,
            )

        fun Order.toSubmitted(): Order {
            validate()
            submit()
            return this
        }

        describe("applyExecution") {
            context("부분 체결") {
                it("filledQuantity가 증가하고 PARTIAL_FILLED로 전이") {
                    val order = createOrder(quantity = 1000).toSubmitted()

                    val status = order.applyExecution(300, BigDecimal("50000"))

                    status shouldBe OrderStatus.PARTIAL_FILLED
                    order.filledQuantity shouldBe 300
                    order.remainingQuantity() shouldBe 700
                }
            }

            context("전량 체결") {
                it("filledQuantity가 quantity에 도달하면 FILLED로 전이") {
                    val order = createOrder(quantity = 500).toSubmitted()

                    order.applyExecution(300, BigDecimal("50000"))
                    val status = order.applyExecution(200, BigDecimal("51000"))

                    status shouldBe OrderStatus.FILLED
                    order.filledQuantity shouldBe 500
                    order.remainingQuantity() shouldBe 0
                }
            }

            context("평균 체결 단가 계산") {
                it("여러 부분 체결의 가중 평균을 계산") {
                    val order = createOrder(quantity = 1000).toSubmitted()

                    order.applyExecution(300, BigDecimal("50000"))
                    order.applyExecution(200, BigDecimal("51000"))

                    // (300*50000 + 200*51000) / 500 = (15000000 + 10200000) / 500 = 50400
                    order.avgFilledPrice!!.setScale(0, RoundingMode.HALF_UP) shouldBe BigDecimal("50400")
                    order.filledQuantity shouldBe 500
                }
            }

            context("PENDING 상태에서 체결 시도") {
                it("예외 발생") {
                    val order = createOrder()

                    shouldThrow<IllegalStateException> {
                        order.applyExecution(100, BigDecimal("50000"))
                    }
                }
            }

            context("PARTIAL_FILLED에서 추가 체결") {
                it("PARTIAL_FILLED -> PARTIAL_FILLED 자기 전이 성공") {
                    val order = createOrder(quantity = 1000).toSubmitted()

                    order.applyExecution(200, BigDecimal("50000"))
                    val status = order.applyExecution(300, BigDecimal("50500"))

                    status shouldBe OrderStatus.PARTIAL_FILLED
                    order.filledQuantity shouldBe 500
                }
            }
        }

        describe("cancel") {
            context("SUBMITTED 상태에서 취소") {
                it("CANCELLED로 전이하고 미체결 수량 반환") {
                    val order = createOrder(quantity = 1000).toSubmitted()

                    val remaining = order.cancel()

                    order.status shouldBe OrderStatus.CANCELLED
                    remaining shouldBe 1000
                }
            }

            context("부분 체결 후 취소") {
                it("미체결 수량만 반환") {
                    val order = createOrder(quantity = 1000).toSubmitted()
                    order.applyExecution(400, BigDecimal("50000"))

                    val remaining = order.cancel()

                    order.status shouldBe OrderStatus.CANCELLED
                    remaining shouldBe 600
                    order.filledQuantity shouldBe 400
                }
            }

            context("PENDING 상태에서 취소 시도") {
                it("예외 발생") {
                    val order = createOrder()

                    shouldThrow<IllegalStateException> {
                        order.cancel()
                    }
                }
            }
        }

        describe("reject") {
            context("PENDING 상태에서 거부") {
                it("REJECTED로 전이") {
                    val order = createOrder()

                    order.reject()

                    order.status shouldBe OrderStatus.REJECTED
                }
            }

            context("SUBMITTED 상태에서 거부 시도") {
                it("예외 발생") {
                    val order = createOrder().toSubmitted()

                    shouldThrow<IllegalStateException> {
                        order.reject()
                    }
                }
            }
        }

        describe("settle") {
            context("FILLED 상태에서 정산") {
                it("SETTLED로 전이") {
                    val order = createOrder(quantity = 100).toSubmitted()
                    order.applyExecution(100, BigDecimal("50000"))

                    order.settle()

                    order.status shouldBe OrderStatus.SETTLED
                }
            }
        }
    })
