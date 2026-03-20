package io.lucky.security.domain.execution

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ExecutionStatusTest :
    DescribeSpec({

        describe("canTransitTo") {
            context("RECEIVED") {
                it("APPLIED로 전이 가능") {
                    ExecutionStatus.RECEIVED.canTransitTo(ExecutionStatus.APPLIED) shouldBe true
                }
                it("SETTLED로 직접 전이 불가") {
                    ExecutionStatus.RECEIVED.canTransitTo(ExecutionStatus.SETTLED) shouldBe false
                }
            }

            context("APPLIED") {
                it("SETTLED로 전이 가능") {
                    ExecutionStatus.APPLIED.canTransitTo(ExecutionStatus.SETTLED) shouldBe true
                }
                it("FAILED로 전이 가능") {
                    ExecutionStatus.APPLIED.canTransitTo(ExecutionStatus.FAILED) shouldBe true
                }
            }

            context("종단 상태") {
                listOf(ExecutionStatus.SETTLED, ExecutionStatus.FAILED).forEach { terminal ->
                    it("$terminal 에서 어떤 상태로도 전이 불가") {
                        ExecutionStatus.entries.forEach { target ->
                            terminal.canTransitTo(target) shouldBe false
                        }
                    }
                }
            }
        }

        describe("transitTo") {
            context("허용된 전이") {
                it("RECEIVED -> APPLIED 성공") {
                    ExecutionStatus.RECEIVED.transitTo(ExecutionStatus.APPLIED) shouldBe ExecutionStatus.APPLIED
                }
                it("APPLIED -> SETTLED 성공") {
                    ExecutionStatus.APPLIED.transitTo(ExecutionStatus.SETTLED) shouldBe ExecutionStatus.SETTLED
                }
            }

            context("허용되지 않은 전이") {
                it("RECEIVED -> SETTLED 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        ExecutionStatus.RECEIVED.transitTo(ExecutionStatus.SETTLED)
                    }
                }
            }
        }
    })
