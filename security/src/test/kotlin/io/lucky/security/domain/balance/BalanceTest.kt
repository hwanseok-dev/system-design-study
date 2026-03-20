package io.lucky.security.domain.balance

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class BalanceTest :
    DescribeSpec({

        fun createBalance(cash: Long = 1_000_000): Balance = Balance(userId = 1L, cashAmount = BigDecimal(cash))

        describe("lockCash") {
            context("가용 잔고가 충분할 때") {
                it("lockedAmount 증가, cashAmount 유지") {
                    val balance = createBalance(1_000_000)

                    balance.lockCash(BigDecimal(300_000))

                    balance.cashAmount shouldBe BigDecimal(1_000_000)
                    balance.lockedAmount shouldBe BigDecimal(300_000)
                    balance.availableCash() shouldBe BigDecimal(700_000)
                }
            }

            context("가용 잔고가 부족할 때") {
                it("예외 발생") {
                    val balance = createBalance(100_000)

                    shouldThrow<IllegalStateException> {
                        balance.lockCash(BigDecimal(200_000))
                    }
                }
            }

            context("이미 일부가 동결된 상태에서 가용분 초과 동결 시도") {
                it("예외 발생") {
                    val balance = createBalance(1_000_000)
                    balance.lockCash(BigDecimal(800_000))

                    shouldThrow<IllegalStateException> {
                        balance.lockCash(BigDecimal(300_000))
                    }
                }
            }
        }

        describe("unlockCash") {
            context("동결 금액이 충분할 때") {
                it("lockedAmount 감소") {
                    val balance = createBalance(1_000_000)
                    balance.lockCash(BigDecimal(500_000))

                    balance.unlockCash(BigDecimal(300_000))

                    balance.cashAmount shouldBe BigDecimal(1_000_000)
                    balance.lockedAmount shouldBe BigDecimal(200_000)
                    balance.availableCash() shouldBe BigDecimal(800_000)
                }
            }

            context("동결 금액보다 큰 금액 해제 시도") {
                it("예외 발생") {
                    val balance = createBalance(1_000_000)
                    balance.lockCash(BigDecimal(100_000))

                    shouldThrow<IllegalStateException> {
                        balance.unlockCash(BigDecimal(200_000))
                    }
                }
            }
        }

        describe("confirmBuyExecution") {
            context("동결 금액에서 체결 확정") {
                it("cashAmount, lockedAmount 모두 감소") {
                    val balance = createBalance(1_000_000)
                    balance.lockCash(BigDecimal(500_000))

                    balance.confirmBuyExecution(BigDecimal(200_000))

                    balance.cashAmount shouldBe BigDecimal(800_000)
                    balance.lockedAmount shouldBe BigDecimal(300_000)
                    balance.availableCash() shouldBe BigDecimal(500_000)
                }
            }
        }

        describe("addCash") {
            it("cashAmount 증가") {
                val balance = createBalance(500_000)

                balance.addCash(BigDecimal(100_000))

                balance.cashAmount shouldBe BigDecimal(600_000)
            }
        }
    })
