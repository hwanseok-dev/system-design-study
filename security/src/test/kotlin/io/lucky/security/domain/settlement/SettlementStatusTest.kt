package io.lucky.security.domain.settlement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SettlementStatusTest :
    DescribeSpec({

        describe("canTransitTo") {
            context("PENDING") {
                it("PROCESSING으로 전이 가능") {
                    SettlementStatus.PENDING.canTransitTo(SettlementStatus.PROCESSING) shouldBe true
                }
                it("COMPLETED로 직접 전이 불가") {
                    SettlementStatus.PENDING.canTransitTo(SettlementStatus.COMPLETED) shouldBe false
                }
            }

            context("PROCESSING") {
                it("COMPLETED로 전이 가능") {
                    SettlementStatus.PROCESSING.canTransitTo(SettlementStatus.COMPLETED) shouldBe true
                }
                it("FAILED로 전이 가능") {
                    SettlementStatus.PROCESSING.canTransitTo(SettlementStatus.FAILED) shouldBe true
                }
            }

            context("FAILED") {
                it("PROCESSING으로 재시도 전이 가능") {
                    SettlementStatus.FAILED.canTransitTo(SettlementStatus.PROCESSING) shouldBe true
                }
                it("COMPLETED로 직접 전이 불가") {
                    SettlementStatus.FAILED.canTransitTo(SettlementStatus.COMPLETED) shouldBe false
                }
            }

            context("COMPLETED") {
                it("어떤 상태로도 전이 불가") {
                    SettlementStatus.entries.forEach { target ->
                        SettlementStatus.COMPLETED.canTransitTo(target) shouldBe false
                    }
                }
            }
        }

        describe("transitTo") {
            context("허용된 전이") {
                it("PENDING -> PROCESSING 성공") {
                    SettlementStatus.PENDING.transitTo(SettlementStatus.PROCESSING) shouldBe SettlementStatus.PROCESSING
                }
                it("PROCESSING -> COMPLETED 성공") {
                    SettlementStatus.PROCESSING.transitTo(SettlementStatus.COMPLETED) shouldBe SettlementStatus.COMPLETED
                }
                it("FAILED -> PROCESSING 재시도 성공") {
                    SettlementStatus.FAILED.transitTo(SettlementStatus.PROCESSING) shouldBe SettlementStatus.PROCESSING
                }
            }

            context("허용되지 않은 전이") {
                it("PENDING -> COMPLETED 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        SettlementStatus.PENDING.transitTo(SettlementStatus.COMPLETED)
                    }
                }
                it("COMPLETED -> PROCESSING 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        SettlementStatus.COMPLETED.transitTo(SettlementStatus.PROCESSING)
                    }
                }
            }
        }
    })
