package io.lucky.orchestrator.domain.workflow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class WorkflowStatusTest :
    DescribeSpec({

        describe("canTransitTo") {
            context("CREATED") {
                it("RUNNING으로 전이 가능") {
                    WorkflowStatus.CREATED.canTransitTo(WorkflowStatus.RUNNING) shouldBe true
                }
                it("SUCCEEDED로 직접 전이 불가") {
                    WorkflowStatus.CREATED.canTransitTo(WorkflowStatus.SUCCEEDED) shouldBe false
                }
                it("FAILED로 직접 전이 불가") {
                    WorkflowStatus.CREATED.canTransitTo(WorkflowStatus.FAILED) shouldBe false
                }
            }

            context("RUNNING") {
                it("SUCCEEDED로 전이 가능") {
                    WorkflowStatus.RUNNING.canTransitTo(WorkflowStatus.SUCCEEDED) shouldBe true
                }
                it("FAILED로 전이 가능") {
                    WorkflowStatus.RUNNING.canTransitTo(WorkflowStatus.FAILED) shouldBe true
                }
            }

            context("SUCCEEDED") {
                it("어떤 상태로도 전이 불가") {
                    WorkflowStatus.entries.forEach { target ->
                        WorkflowStatus.SUCCEEDED.canTransitTo(target) shouldBe false
                    }
                }
            }

            context("FAILED") {
                it("어떤 상태로도 전이 불가") {
                    WorkflowStatus.entries.forEach { target ->
                        WorkflowStatus.FAILED.canTransitTo(target) shouldBe false
                    }
                }
            }
        }

        describe("transitTo") {
            context("허용된 전이") {
                it("CREATED -> RUNNING 성공") {
                    WorkflowStatus.CREATED.transitTo(WorkflowStatus.RUNNING) shouldBe WorkflowStatus.RUNNING
                }
                it("RUNNING -> SUCCEEDED 성공") {
                    WorkflowStatus.RUNNING.transitTo(WorkflowStatus.SUCCEEDED) shouldBe WorkflowStatus.SUCCEEDED
                }
                it("RUNNING -> FAILED 성공") {
                    WorkflowStatus.RUNNING.transitTo(WorkflowStatus.FAILED) shouldBe WorkflowStatus.FAILED
                }
            }

            context("허용되지 않은 전이") {
                it("CREATED -> SUCCEEDED 시 예외 발생") {
                    shouldThrow<IllegalStateException> {
                        WorkflowStatus.CREATED.transitTo(WorkflowStatus.SUCCEEDED)
                    }
                }
            }
        }
    })
