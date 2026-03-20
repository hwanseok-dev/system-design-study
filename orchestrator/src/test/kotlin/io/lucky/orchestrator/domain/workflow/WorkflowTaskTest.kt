package io.lucky.orchestrator.domain.workflow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.lucky.orchestrator.domain.task.Task

class WorkflowTaskTest :
    DescribeSpec({

        fun createWorkflowTask(
            status: TaskStatus = TaskStatus.CREATED,
            expectedCount: Int = 10,
            completedCount: Int = 0,
        ): WorkflowTask {
            val workflow = Workflow(name = "test-workflow")
            val task = Task(name = "test-task", queueName = "queue.test")
            return WorkflowTask(
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
                    val wt = createWorkflowTask()
                    wt.markWaiting()
                    wt.status shouldBe TaskStatus.WAITING
                }
            }
        }

        describe("markRunning") {
            context("WAITING 상태일 때") {
                it("RUNNING으로 전이된다") {
                    val wt = createWorkflowTask(status = TaskStatus.WAITING)
                    wt.markRunning()
                    wt.status shouldBe TaskStatus.RUNNING
                }
            }

            context("CREATED 상태일 때") {
                it("예외가 발생한다") {
                    val wt = createWorkflowTask()
                    shouldThrow<IllegalStateException> { wt.markRunning() }
                }
            }
        }

        describe("markSucceeded") {
            context("RUNNING 상태일 때") {
                it("SUCCEEDED로 전이된다") {
                    val wt = createWorkflowTask(status = TaskStatus.RUNNING)
                    wt.markSucceeded()
                    wt.status shouldBe TaskStatus.SUCCEEDED
                }
            }
        }

        describe("markFailed") {
            context("RUNNING 상태일 때") {
                it("FAILED로 전이된다") {
                    val wt = createWorkflowTask(status = TaskStatus.RUNNING)
                    wt.markFailed()
                    wt.status shouldBe TaskStatus.FAILED
                }
            }

            context("WAITING 상태일 때 - parent 실패 전파") {
                it("FAILED로 전이된다") {
                    val wt = createWorkflowTask(status = TaskStatus.WAITING)
                    wt.markFailed()
                    wt.status shouldBe TaskStatus.FAILED
                }
            }

            context("SUCCEEDED 상태일 때") {
                it("예외가 발생한다") {
                    val wt = createWorkflowTask(status = TaskStatus.SUCCEEDED)
                    shouldThrow<IllegalStateException> { wt.markFailed() }
                }
            }
        }

        describe("isComplete") {
            context("completedCount가 expectedCount와 같을 때") {
                it("true를 반환한다") {
                    val wt = createWorkflowTask(expectedCount = 10, completedCount = 10)
                    wt.isComplete() shouldBe true
                }
            }

            context("completedCount가 expectedCount보다 작을 때") {
                it("false를 반환한다") {
                    val wt = createWorkflowTask(expectedCount = 10, completedCount = 5)
                    wt.isComplete() shouldBe false
                }
            }

            context("completedCount가 expectedCount를 초과할 때") {
                it("true를 반환한다") {
                    val wt = createWorkflowTask(expectedCount = 10, completedCount = 15)
                    wt.isComplete() shouldBe true
                }
            }
        }
    })
