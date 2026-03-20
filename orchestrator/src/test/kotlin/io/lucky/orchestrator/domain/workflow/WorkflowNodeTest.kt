package io.lucky.orchestrator.domain.workflow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.lucky.orchestrator.domain.task.Task

class WorkflowNodeTest :
    DescribeSpec({

        fun createNode(
            status: TaskStatus = TaskStatus.CREATED,
            expectedCount: Int = 10,
            completedCount: Int = 0,
        ): WorkflowNode {
            val workflow = Workflow(name = "test-workflow")
            val task = Task(name = "test-task", queueName = "queue.test")
            return WorkflowNode(
                workflow = workflow,
                task = task,
                status = status,
                expectedCount = expectedCount,
                completedCount = completedCount,
            )
        }

        describe("markWaiting") {
            context("CREATED 상태일 때") {
                it("WAITING으로 전이된다") {
                    val node = createNode()
                    node.markWaiting()
                    node.status shouldBe TaskStatus.WAITING
                }
            }
        }

        describe("markRunning") {
            context("WAITING 상태일 때") {
                it("RUNNING으로 전이된다") {
                    val node = createNode(status = TaskStatus.WAITING)
                    node.markRunning()
                    node.status shouldBe TaskStatus.RUNNING
                }
            }

            context("CREATED 상태일 때") {
                it("예외가 발생한다") {
                    val node = createNode()
                    shouldThrow<IllegalStateException> { node.markRunning() }
                }
            }
        }

        describe("markSucceeded") {
            context("RUNNING 상태일 때") {
                it("SUCCEEDED로 전이된다") {
                    val node = createNode(status = TaskStatus.RUNNING)
                    node.markSucceeded()
                    node.status shouldBe TaskStatus.SUCCEEDED
                }
            }
        }

        describe("markFailed") {
            context("RUNNING 상태일 때") {
                it("FAILED로 전이된다") {
                    val node = createNode(status = TaskStatus.RUNNING)
                    node.markFailed()
                    node.status shouldBe TaskStatus.FAILED
                }
            }

            context("WAITING 상태일 때") {
                it("예외가 발생한다") {
                    val node = createNode(status = TaskStatus.WAITING)
                    shouldThrow<IllegalStateException> { node.markFailed() }
                }
            }

            context("SUCCEEDED 상태일 때") {
                it("예외가 발생한다") {
                    val node = createNode(status = TaskStatus.SUCCEEDED)
                    shouldThrow<IllegalStateException> { node.markFailed() }
                }
            }
        }

        describe("isComplete") {
            context("completedCount가 expectedCount와 같을 때") {
                it("true를 반환한다") {
                    val node = createNode(expectedCount = 10, completedCount = 10)
                    node.isComplete() shouldBe true
                }
            }

            context("completedCount가 expectedCount보다 작을 때") {
                it("false를 반환한다") {
                    val node = createNode(expectedCount = 10, completedCount = 5)
                    node.isComplete() shouldBe false
                }
            }

            context("completedCount가 expectedCount를 초과할 때") {
                it("true를 반환한다") {
                    val node = createNode(expectedCount = 10, completedCount = 15)
                    node.isComplete() shouldBe true
                }
            }
        }
    })
