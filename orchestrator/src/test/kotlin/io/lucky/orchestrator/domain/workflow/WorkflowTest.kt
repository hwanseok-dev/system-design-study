package io.lucky.orchestrator.domain.workflow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.lucky.orchestrator.domain.task.Task

class WorkflowTest :
    DescribeSpec({

        fun createTasks(): Triple<Task, Task, Task> {
            val a = Task(id = 1, name = "A", queueName = "queue.a")
            val b = Task(id = 2, name = "B", queueName = "queue.b")
            val c = Task(id = 3, name = "C", queueName = "queue.c")
            return Triple(a, b, c)
        }

        fun linearWorkflow(): Pair<Workflow, Triple<Task, Task, Task>> {
            val (a, b, c) = createTasks()
            val wf = Workflow(name = "linear")
            wf.addTask(a, 1)
            wf.addTask(b, 1)
            wf.addTask(c, 1)
            wf.addEdge(a, b)
            wf.addEdge(b, c)
            return wf to Triple(a, b, c)
        }

        fun diamondWorkflow(): Pair<Workflow, Triple<Task, Task, Task>> {
            val (a, b, c) = createTasks()
            val wf = Workflow(name = "diamond")
            wf.addTask(a, 1)
            wf.addTask(b, 1)
            wf.addTask(c, 1)
            wf.addEdge(a, b)
            wf.addEdge(a, c)
            wf.addEdge(b, c)
            return wf to Triple(a, b, c)
        }

        describe("addTask") {
            context("task를 추가할 때") {
                it("WorkflowTask가 생성된다") {
                    val wf = Workflow(name = "test")
                    val task = Task(id = 1, name = "A", queueName = "queue.a")
                    val wt = wf.addTask(task, 100)
                    wf.nodes shouldHaveSize 1
                    wt.expectedCount shouldBe 100
                }
            }

            context("동일 task를 중복 추가할 때") {
                it("예외가 발생한다") {
                    val wf = Workflow(name = "test")
                    val task = Task(id = 1, name = "A", queueName = "queue.a")
                    wf.addTask(task, 1)
                    shouldThrow<IllegalStateException> { wf.addTask(task, 1) }
                }
            }
        }

        describe("addEdge") {
            context("유효한 edge를 추가할 때") {
                it("edge가 추가된다") {
                    val (a, b, _) = createTasks()
                    val wf = Workflow(name = "test")
                    wf.addTask(a, 1)
                    wf.addTask(b, 1)
                    wf.addEdge(a, b)
                    wf.edges shouldHaveSize 1
                }
            }

            context("cycle이 발생하는 edge를 추가할 때") {
                it("예외가 발생하고 edge가 롤백된다") {
                    val (a, b, c) = createTasks()
                    val wf = Workflow(name = "test")
                    wf.addTask(a, 1)
                    wf.addTask(b, 1)
                    wf.addTask(c, 1)
                    wf.addEdge(a, b)
                    wf.addEdge(b, c)
                    shouldThrow<IllegalStateException> { wf.addEdge(c, a) }
                    wf.edges shouldHaveSize 2
                }
            }
        }

        describe("start") {
            context("순차 DAG (A -> B -> C)") {
                it("root task만 RUNNING이 된다") {
                    val (wf, tasks) = linearWorkflow()
                    val running = wf.start()
                    running shouldHaveSize 1
                    running[0].task.name shouldBe "A"
                    wf.status shouldBe WorkflowStatus.RUNNING
                    wf.findNode(tasks.first.id).status shouldBe TaskStatus.RUNNING
                    wf.findNode(tasks.second.id).status shouldBe TaskStatus.WAITING
                    wf.findNode(tasks.third.id).status shouldBe TaskStatus.WAITING
                }
            }

            context("다이아몬드 DAG (A -> B, A -> C, B -> C)") {
                it("root task(A)만 RUNNING이 된다") {
                    val (wf, _) = diamondWorkflow()
                    val running = wf.start()
                    running shouldHaveSize 1
                    running[0].task.name shouldBe "A"
                }
            }
        }

        describe("completeTask") {
            context("순차 DAG에서 A 완료 시") {
                it("B가 RUNNING이 된다") {
                    val (wf, tasks) = linearWorkflow()
                    wf.start()
                    val next = wf.completeTask(tasks.first.id)
                    next shouldHaveSize 1
                    next[0].task.name shouldBe "B"
                    wf.findNode(tasks.first.id).status shouldBe TaskStatus.SUCCEEDED
                    wf.findNode(tasks.second.id).status shouldBe TaskStatus.RUNNING
                }
            }

            context("순차 DAG에서 모든 task 완료 시") {
                it("workflow가 SUCCEEDED가 된다") {
                    val (wf, tasks) = linearWorkflow()
                    wf.start()
                    wf.completeTask(tasks.first.id)
                    wf.completeTask(tasks.second.id)
                    wf.completeTask(tasks.third.id)
                    wf.status shouldBe WorkflowStatus.SUCCEEDED
                }
            }

            context("다이아몬드 DAG에서 A 완료 시") {
                it("B만 RUNNING이 된다 (C는 B도 parent이므로 WAITING 유지)") {
                    val (wf, tasks) = diamondWorkflow()
                    wf.start()
                    val next = wf.completeTask(tasks.first.id)
                    next shouldHaveSize 1
                    next[0].task.name shouldBe "B"
                    wf.findNode(tasks.third.id).status shouldBe TaskStatus.WAITING
                }
            }

            context("다이아몬드 DAG에서 A, B 모두 완료 시") {
                it("C가 RUNNING이 된다") {
                    val (wf, tasks) = diamondWorkflow()
                    wf.start()
                    wf.completeTask(tasks.first.id)
                    val next = wf.completeTask(tasks.second.id)
                    next shouldHaveSize 1
                    next[0].task.name shouldBe "C"
                }
            }

            context("이미 FAILED인 task에 completeTask 호출 시") {
                it("빈 리스트를 반환한다 (멱등)") {
                    val (wf, tasks) = linearWorkflow()
                    wf.start()
                    wf.failTask(tasks.first.id)
                    val result = wf.completeTask(tasks.first.id)
                    result.shouldBeEmpty()
                }
            }
        }

        describe("failTask") {
            context("RUNNING 상태의 task 실패 시") {
                it("task와 workflow가 FAILED가 된다") {
                    val (wf, tasks) = linearWorkflow()
                    wf.start()
                    wf.completeTask(tasks.first.id)
                    wf.failTask(tasks.second.id)
                    wf.findNode(tasks.second.id).status shouldBe TaskStatus.FAILED
                    wf.status shouldBe WorkflowStatus.FAILED
                }
            }

            context("downstream task 실패 전파") {
                it("WAITING 상태의 downstream task는 WAITING을 유지한다") {
                    val (wf, tasks) = linearWorkflow()
                    wf.start()
                    wf.failTask(tasks.first.id)
                    wf.findNode(tasks.second.id).status shouldBe TaskStatus.WAITING
                    wf.findNode(tasks.third.id).status shouldBe TaskStatus.WAITING
                }
            }

            context("이미 SUCCEEDED인 task가 있을 때 실패 전파") {
                it("SUCCEEDED task는 FAILED로 변경되지 않는다") {
                    val (wf, tasks) = linearWorkflow()
                    wf.start()
                    wf.completeTask(tasks.first.id)
                    wf.completeTask(tasks.second.id)
                    // C is now RUNNING, A and B are SUCCEEDED
                    wf.failTask(tasks.third.id)
                    wf.findNode(tasks.first.id).status shouldBe TaskStatus.SUCCEEDED
                    wf.findNode(tasks.second.id).status shouldBe TaskStatus.SUCCEEDED
                    wf.findNode(tasks.third.id).status shouldBe TaskStatus.FAILED
                }
            }

            context("이미 FAILED인 task에 failTask 호출 시") {
                it("멱등하게 동작한다") {
                    val (wf, tasks) = linearWorkflow()
                    wf.start()
                    wf.failTask(tasks.first.id)
                    wf.failTask(tasks.first.id)
                    wf.findNode(tasks.first.id).status shouldBe TaskStatus.FAILED
                }
            }
        }

        describe("calculateProgress") {
            context("일부 task의 응답이 완료되었을 때") {
                it("진행률을 계산한다") {
                    val wf = Workflow(name = "test")
                    val a = Task(id = 1, name = "A", queueName = "queue.a")
                    val b = Task(id = 2, name = "B", queueName = "queue.b")
                    val wtA = wf.addTask(a, 100)
                    wf.addTask(b, 200)
                    wtA.completedCount = 50
                    wf.calculateProgress() shouldBe 50.0 / 300.0
                }
            }

            context("모든 task가 완료되었을 때") {
                it("1.0을 반환한다") {
                    val wf = Workflow(name = "test")
                    val a = Task(id = 1, name = "A", queueName = "queue.a")
                    val wt = wf.addTask(a, 10)
                    wt.completedCount = 10
                    wf.calculateProgress() shouldBe 1.0
                }
            }

            context("task가 없을 때") {
                it("0.0을 반환한다") {
                    val wf = Workflow(name = "test")
                    wf.calculateProgress() shouldBe 0.0
                }
            }
        }
    })
