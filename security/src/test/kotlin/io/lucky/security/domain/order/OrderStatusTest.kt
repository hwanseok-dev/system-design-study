package io.lucky.security.domain.order

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OrderStatusTest :
    DescribeSpec({

        describe("canTransitTo") {
            context("PENDING") {
                it("VALIDATED로 전이 가능") {
                    OrderStatus.PENDING.canTransitTo(OrderStatus.VALIDATED) shouldBe true
                }
                it("REJECTED로 전이 가능") {
                    OrderStatus.PENDING.canTransitTo(OrderStatus.REJECTED) shouldBe true
                }
                it("SUBMITTED로 직접 전이 불가") {
                    OrderStatus.PENDING.canTransitTo(OrderStatus.SUBMITTED) shouldBe false
                }
            }

            context("VALIDATED") {
                it("SUBMITTED로 전이 가능") {
                    OrderStatus.VALIDATED.canTransitTo(OrderStatus.SUBMITTED) shouldBe true
                }
                it("REJECTED로 전이 가능") {
                    OrderStatus.VALIDATED.canTransitTo(OrderStatus.REJECTED) shouldBe true
                }
            }

            context("SUBMITTED") {
                it("PARTIAL_FILLED로 전이 가능") {
                    OrderStatus.SUBMITTED.canTransitTo(OrderStatus.PARTIAL_FILLED) shouldBe true
                }
                it("FILLED로 전이 가능") {
                    OrderStatus.SUBMITTED.canTransitTo(OrderStatus.FILLED) shouldBe true
                }
                it("CANCELLED로 전이 가능") {
                    OrderStatus.SUBMITTED.canTransitTo(OrderStatus.CANCELLED) shouldBe true
                }
            }

            context("PARTIAL_FILLED") {
                it("PARTIAL_FILLED로 자기 전이 가능") {
                    OrderStatus.PARTIAL_FILLED.canTransitTo(OrderStatus.PARTIAL_FILLED) shouldBe true
                }
                it("FILLED로 전이 가능") {
                    OrderStatus.PARTIAL_FILLED.canTransitTo(OrderStatus.FILLED) shouldBe true
                }
                it("CANCELLED로 전이 가능 (부분 취소)") {
                    OrderStatus.PARTIAL_FILLED.canTransitTo(OrderStatus.CANCELLED) shouldBe true
                }
            }

            context("FILLED") {
                it("SETTLED로 전이 가능") {
                    OrderStatus.FILLED.canTransitTo(OrderStatus.SETTLED) shouldBe true
                }
                it("CANCELLED로 전이 불가") {
                    OrderStatus.FILLED.canTransitTo(OrderStatus.CANCELLED) shouldBe false
                }
            }

            context("종단 상태") {
                listOf(OrderStatus.SETTLED, OrderStatus.REJECTED, OrderStatus.CANCELLED).forEach { terminal ->
                    it("$terminal 에서 어떤 상태로도 전이 불가") {
                        OrderStatus.entries.forEach { target ->
                            terminal.canTransitTo(target) shouldBe false
                        }
                    }
                }
            }
        }

        describe("transitTo") {
            context("허용된 전이") {
                it("PENDING -> VALIDATED 성공") {
                    OrderStatus.PENDING.transitTo(OrderStatus.VALIDATED) shouldBe OrderStatus.VALIDATED
                }
                it("VALIDATED -> SUBMITTED 성공") {
                    OrderStatus.VALIDATED.transitTo(OrderStatus.SUBMITTED) shouldBe OrderStatus.SUBMITTED
                }
                it("SUBMITTED -> PARTIAL_FILLED 성공") {
                    OrderStatus.SUBMITTED.transitTo(OrderStatus.PARTIAL_FILLED) shouldBe OrderStatus.PARTIAL_FILLED
                }
                it("PARTIAL_FILLED -> FILLED 성공") {
                    OrderStatus.PARTIAL_FILLED.transitTo(OrderStatus.FILLED) shouldBe OrderStatus.FILLED
                }
                it("FILLED -> SETTLED 성공") {
                    OrderStatus.FILLED.transitTo(OrderStatus.SETTLED) shouldBe OrderStatus.SETTLED
                }
            }

            context("허용되지 않은 전이") {
                it("PENDING -> SUBMITTED 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        OrderStatus.PENDING.transitTo(OrderStatus.SUBMITTED)
                    }
                }
                it("FILLED -> CANCELLED 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        OrderStatus.FILLED.transitTo(OrderStatus.CANCELLED)
                    }
                }
                it("SETTLED -> FILLED 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        OrderStatus.SETTLED.transitTo(OrderStatus.FILLED)
                    }
                }
            }
        }
    })
