package io.lucky.security.domain.balance

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.RoundingMode

class StockHoldingTest :
    DescribeSpec({

        fun createHolding(
            quantity: Int = 100,
            avgBuyPrice: Long = 50_000,
        ): StockHolding =
            StockHolding(
                userId = 1L,
                stockCode = "005930",
                quantity = quantity,
                avgBuyPrice = BigDecimal(avgBuyPrice),
            )

        describe("lockQuantity") {
            context("가용 수량이 충분할 때") {
                it("lockedQuantity 증가") {
                    val holding = createHolding(quantity = 100)

                    holding.lockQuantity(30)

                    holding.lockedQuantity shouldBe 30
                    holding.availableQuantity() shouldBe 70
                }
            }

            context("가용 수량이 부족할 때") {
                it("예외 발생") {
                    val holding = createHolding(quantity = 50)

                    shouldThrow<IllegalStateException> {
                        holding.lockQuantity(60)
                    }
                }
            }

            context("이미 일부가 동결된 상태에서 추가 동결") {
                it("가용 수량 기준으로 검증") {
                    val holding = createHolding(quantity = 100)
                    holding.lockQuantity(70)

                    shouldThrow<IllegalStateException> {
                        holding.lockQuantity(40)
                    }
                }
            }
        }

        describe("unlockQuantity") {
            context("동결 수량이 충분할 때") {
                it("lockedQuantity 감소") {
                    val holding = createHolding(quantity = 100)
                    holding.lockQuantity(50)

                    holding.unlockQuantity(30)

                    holding.lockedQuantity shouldBe 20
                    holding.availableQuantity() shouldBe 80
                }
            }

            context("동결 수량보다 큰 수량 해제 시도") {
                it("예외 발생") {
                    val holding = createHolding(quantity = 100)
                    holding.lockQuantity(20)

                    shouldThrow<IllegalStateException> {
                        holding.unlockQuantity(30)
                    }
                }
            }
        }

        describe("confirmSellExecution") {
            context("동결 수량에서 매도 체결 확정") {
                it("quantity, lockedQuantity 모두 감소") {
                    val holding = createHolding(quantity = 100)
                    holding.lockQuantity(50)

                    holding.confirmSellExecution(30)

                    holding.quantity shouldBe 70
                    holding.lockedQuantity shouldBe 20
                }
            }
        }

        describe("addQuantity") {
            context("기존 보유분에 매수 추가") {
                it("가중 평균 매수 단가 재계산") {
                    val holding = createHolding(quantity = 100, avgBuyPrice = 50_000)

                    holding.addQuantity(50, BigDecimal(52_000))

                    // (100*50000 + 50*52000) / 150 = (5000000 + 2600000) / 150 = 50666.67
                    holding.quantity shouldBe 150
                    holding.avgBuyPrice.setScale(0, RoundingMode.HALF_UP) shouldBe BigDecimal("50667")
                }
            }

            context("보유분이 0일 때 첫 매수") {
                it("매수 단가가 그대로 설정") {
                    val holding = StockHolding(userId = 1L, stockCode = "005930")

                    holding.addQuantity(100, BigDecimal(48_000))

                    holding.quantity shouldBe 100
                    holding.avgBuyPrice.setScale(0, RoundingMode.HALF_UP) shouldBe BigDecimal("48000")
                }
            }
        }
    })
