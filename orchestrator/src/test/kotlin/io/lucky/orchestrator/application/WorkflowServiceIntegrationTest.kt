package io.lucky.orchestrator.application

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.lucky.orchestrator.api.WorkflowEdgeRequest
import io.lucky.orchestrator.api.WorkflowNodeRequest
import io.lucky.orchestrator.domain.task.Task
import io.lucky.orchestrator.domain.workflow.TaskStatus
import io.lucky.orchestrator.domain.workflow.WorkflowStatus
import io.lucky.orchestrator.infrastructure.messaging.TaskResponseMessage
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutionRequestRepository
import io.lucky.orchestrator.infrastructure.persistence.TaskRepository
import io.lucky.orchestrator.infrastructure.persistence.TaskResponseRepository
import io.lucky.orchestrator.infrastructure.persistence.WorkflowRepository
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class WorkflowServiceIntegrationTest(
    private val workflowService: WorkflowService,
    private val taskExecutionService: TaskExecutionService,
    private val taskRepository: TaskRepository,
    private val workflowRepository: WorkflowRepository,
    private val taskExecutionRequestRepository: TaskExecutionRequestRepository,
    private val taskResponseRepository: TaskResponseRepository,
    private val redisTemplate: StringRedisTemplate,
) : DescribeSpec({

        beforeEach {
            redisTemplate.connectionFactory
                ?.connection
                ?.serverCommands()
                ?.flushDb()
            taskResponseRepository.deleteAll()
            taskExecutionRequestRepository.deleteAll()
            workflowRepository.deleteAll()
            taskRepository.deleteAll()
        }

        describe("create") {
            context("3개 task로 순차 DAG를 생성할 때") {
                it("workflow와 node가 저장된다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))
                    val c = taskRepository.save(Task(name = "C", queueName = "queue.c"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                    WorkflowNodeRequest(c.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                    WorkflowEdgeRequest(b.id, c.id),
                                ),
                        )

                    workflow.nodes shouldHaveSize 3
                    workflow.edges shouldHaveSize 2
                    workflow.status shouldBe WorkflowStatus.CREATED
                }
            }
        }

        describe("start") {
            context("순차 DAG (A -> B -> C) 시작 시") {
                it("root node(A)만 RUNNING이 된다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))
                    val c = taskRepository.save(Task(name = "C", queueName = "queue.c"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                    WorkflowNodeRequest(c.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                    WorkflowEdgeRequest(b.id, c.id),
                                ),
                        )

                    val readyNodes = workflowService.start(workflow.id)
                    readyNodes shouldHaveSize 1
                    readyNodes[0].task.name shouldBe "A"

                    val loaded = workflowService.findById(workflow.id)
                    loaded.status shouldBe WorkflowStatus.RUNNING
                    loaded.findNode(a.id).status shouldBe TaskStatus.RUNNING
                    loaded.findNode(b.id).status shouldBe TaskStatus.WAITING
                    loaded.findNode(c.id).status shouldBe TaskStatus.WAITING
                }
            }

            context("시작 시 task_execution_request가 저장될 때") {
                it("root node에 대한 실행 요청이 저장된다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                ),
                        )

                    workflowService.start(workflow.id)

                    val requests = taskExecutionRequestRepository.findAll()
                    requests shouldHaveSize 1
                    requests[0].workflowId shouldBe workflow.id
                    requests[0].taskId shouldBe a.id
                    requests[0].published shouldBe false
                }
            }
        }

        describe("handleSuccessResponse") {
            context("순차 DAG (A -> B) 에서 A의 응답을 수신할 때") {
                it("task_response가 저장되고 completedCount가 증가한다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                ),
                        )
                    workflowService.start(workflow.id)

                    workflowService.handleSuccessResponse(
                        TaskResponseMessage(
                            workflowId = workflow.id,
                            taskId = a.id,
                            sequence = 1,
                            payload = """{"result":"ok"}""",
                        ),
                    )

                    val responses = taskResponseRepository.findAll()
                    responses shouldHaveSize 1
                    responses[0].workflowId shouldBe workflow.id
                    responses[0].taskId shouldBe a.id

                    val loaded = workflowService.findById(workflow.id)
                    loaded.findNode(a.id).status shouldBe TaskStatus.SUCCEEDED
                    loaded.findNode(b.id).status shouldBe TaskStatus.RUNNING
                }
            }

            context("순차 DAG (A -> B) 에서 모든 task가 완료될 때") {
                it("workflow가 SUCCEEDED 상태가 된다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                ),
                        )
                    workflowService.start(workflow.id)

                    workflowService.handleSuccessResponse(
                        TaskResponseMessage(workflowId = workflow.id, taskId = a.id, sequence = 1),
                    )
                    workflowService.handleSuccessResponse(
                        TaskResponseMessage(workflowId = workflow.id, taskId = b.id, sequence = 1),
                    )

                    val loaded = workflowService.findById(workflow.id)
                    loaded.status shouldBe WorkflowStatus.SUCCEEDED
                    loaded.findNode(a.id).status shouldBe TaskStatus.SUCCEEDED
                    loaded.findNode(b.id).status shouldBe TaskStatus.SUCCEEDED
                }
            }

            context("다음 task 진행 시") {
                it("task_execution_request가 저장된다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                ),
                        )
                    workflowService.start(workflow.id)

                    // clear start requests
                    taskExecutionRequestRepository.deleteAll()

                    workflowService.handleSuccessResponse(
                        TaskResponseMessage(workflowId = workflow.id, taskId = a.id, sequence = 1),
                    )

                    val requests = taskExecutionRequestRepository.findAll()
                    requests shouldHaveSize 1
                    requests[0].taskId shouldBe b.id
                }
            }
        }

        describe("completeTask") {
            context("이미 SUCCEEDED인 task에 다시 completeTask를 호출할 때") {
                it("멱등하게 동작하고 예외가 발생하지 않는다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                ),
                        )
                    workflowService.start(workflow.id)

                    taskExecutionService.completeTask(workflow.id, a.id)

                    shouldNotThrowAny {
                        taskExecutionService.completeTask(workflow.id, a.id)
                    }

                    val loaded = workflowService.findById(workflow.id)
                    loaded.findNode(a.id).status shouldBe TaskStatus.SUCCEEDED
                    loaded.findNode(b.id).status shouldBe TaskStatus.RUNNING
                }
            }
        }

        describe("handleFailureResponse") {
            context("순차 DAG (A -> B) 에서 A가 실패할 때") {
                it("task A와 workflow가 FAILED 상태가 되고 downstream B는 WAITING을 유지한다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                ),
                        )
                    workflowService.start(workflow.id)

                    workflowService.handleFailureResponse(
                        TaskResponseMessage(
                            workflowId = workflow.id,
                            taskId = a.id,
                            sequence = 1,
                            payload = """{"error":"timeout"}""",
                        ),
                    )

                    val responses = taskResponseRepository.findAll()
                    responses shouldHaveSize 1
                    responses[0].status shouldBe "FAILED"

                    val loaded = workflowService.findById(workflow.id)
                    loaded.status shouldBe WorkflowStatus.FAILED
                    loaded.findNode(a.id).status shouldBe TaskStatus.FAILED
                    loaded.findNode(b.id).status shouldBe TaskStatus.WAITING
                }
            }

            context("이미 실패한 task에 다시 실패 응답이 올 때") {
                it("멱등하게 처리된다") {
                    val a = taskRepository.save(Task(name = "A", queueName = "queue.a"))
                    val b = taskRepository.save(Task(name = "B", queueName = "queue.b"))

                    val workflow =
                        workflowService.create(
                            name = "test-workflow",
                            nodes =
                                listOf(
                                    WorkflowNodeRequest(a.id),
                                    WorkflowNodeRequest(b.id),
                                ),
                            edges =
                                listOf(
                                    WorkflowEdgeRequest(a.id, b.id),
                                ),
                        )
                    workflowService.start(workflow.id)

                    workflowService.handleFailureResponse(
                        TaskResponseMessage(workflowId = workflow.id, taskId = a.id, sequence = 1),
                    )
                    workflowService.handleFailureResponse(
                        TaskResponseMessage(workflowId = workflow.id, taskId = a.id, sequence = 2),
                    )

                    val loaded = workflowService.findById(workflow.id)
                    loaded.status shouldBe WorkflowStatus.FAILED
                    loaded.findNode(a.id).status shouldBe TaskStatus.FAILED
                }
            }
        }
    })
