package io.lucky.orchestrator.domain.workflow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class TaskStatusTest :
    DescribeSpec({

        describe("canTransitTo") {
            context("CREATED") {
                it("WAITING으로 전이 가능") {
                    TaskStatus.CREATED.canTransitTo(TaskStatus.WAITING) shouldBe true
                }
                it("RUNNING으로 직접 전이 불가") {
                    TaskStatus.CREATED.canTransitTo(TaskStatus.RUNNING) shouldBe false
                }
            }

            context("WAITING") {
                it("RUNNING으로 전이 가능") {
                    TaskStatus.WAITING.canTransitTo(TaskStatus.RUNNING) shouldBe true
                }
                it("FAILED로 전이 가능 - parent 실패 전파") {
                    TaskStatus.WAITING.canTransitTo(TaskStatus.FAILED) shouldBe true
                }
            }

            context("RUNNING") {
                it("SUCCEEDED로 전이 가능") {
                    TaskStatus.RUNNING.canTransitTo(TaskStatus.SUCCEEDED) shouldBe true
                }
                it("FAILED로 전이 가능") {
                    TaskStatus.RUNNING.canTransitTo(TaskStatus.FAILED) shouldBe true
                }
            }

            context("SUCCEEDED") {
                it("어떤 상태로도 전이 불가") {
                    TaskStatus.entries.forEach { target ->
                        TaskStatus.SUCCEEDED.canTransitTo(target) shouldBe false
                    }
                }
            }

            context("FAILED") {
                it("어떤 상태로도 전이 불가") {
                    TaskStatus.entries.forEach { target ->
                        TaskStatus.FAILED.canTransitTo(target) shouldBe false
                    }
                }
            }
        }

        describe("transitTo") {
            context("허용된 전이") {
                it("CREATED -> WAITING 성공") {
                    TaskStatus.CREATED.transitTo(TaskStatus.WAITING) shouldBe TaskStatus.WAITING
                }
                it("WAITING -> RUNNING 성공") {
                    TaskStatus.WAITING.transitTo(TaskStatus.RUNNING) shouldBe TaskStatus.RUNNING
                }
                it("RUNNING -> SUCCEEDED 성공") {
                    TaskStatus.RUNNING.transitTo(TaskStatus.SUCCEEDED) shouldBe TaskStatus.SUCCEEDED
                }
            }

            context("허용되지 않은 전이") {
                it("CREATED -> RUNNING 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        TaskStatus.CREATED.transitTo(TaskStatus.RUNNING)
                    }
                }
                it("SUCCEEDED -> FAILED 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        TaskStatus.SUCCEEDED.transitTo(TaskStatus.FAILED)
                    }
                }
            }
        }
    })
