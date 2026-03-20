package io.lucky.orchestrator.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.api.WorkflowEdgeRequest
import io.lucky.orchestrator.api.WorkflowNodeRequest
import io.lucky.orchestrator.domain.EntityNotFoundException
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.domain.response.TaskResponse
import io.lucky.orchestrator.domain.workflow.Workflow
import io.lucky.orchestrator.domain.workflow.WorkflowNode
import io.lucky.orchestrator.infrastructure.messaging.TaskResponseMessage
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutePayload
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutionRequest
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutionRequestRepository
import io.lucky.orchestrator.infrastructure.persistence.TaskRepository
import io.lucky.orchestrator.infrastructure.persistence.TaskResponseRepository
import io.lucky.orchestrator.infrastructure.persistence.WorkflowRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val taskRepository: TaskRepository,
    private val taskExecutionRequestRepository: TaskExecutionRequestRepository,
    private val taskResponseRepository: TaskResponseRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun create(
        name: String,
        nodes: List<WorkflowNodeRequest>,
        edges: List<WorkflowEdgeRequest>,
    ): Workflow {
        val workflow = Workflow(name = name)

        val taskIds = nodes.map { it.taskId }
        val tasks = taskRepository.findAllById(taskIds).associateBy { it.id }

        nodes.forEach { node ->
            val task = tasks[node.taskId] ?: throw EntityNotFoundException("Task", node.taskId)
            workflow.addTask(task, task.expectedCount)
        }

        edges.forEach { edge ->
            val parent = tasks[edge.parentTaskId] ?: throw EntityNotFoundException("Task", edge.parentTaskId)
            val child = tasks[edge.childTaskId] ?: throw EntityNotFoundException("Task", edge.childTaskId)
            workflow.addEdge(parent, child)
        }

        val saved = workflowRepository.save(workflow)
        logger.info { "action=${LogAction.CREATE_WORKFLOW} workflowId=${saved.id} name=$name taskCount=${nodes.size}" }
        return saved
    }

    @Transactional
    fun start(workflowId: Long): List<WorkflowNode> {
        val workflow = getWorkflow(workflowId)
        val readyNodes = workflow.start()

        saveTaskExecutionRequests(workflowId, readyNodes)

        logger.info {
            "action=${LogAction.START_WORKFLOW} workflowId=$workflowId readyNodes=${readyNodes.map { it.task.name }}"
        }
        return readyNodes
    }

    @Transactional(readOnly = true)
    fun findById(workflowId: Long): Workflow = getWorkflow(workflowId)

    fun saveTaskExecutionRequests(
        workflowId: Long,
        nodes: List<WorkflowNode>,
    ) {
        nodes.forEach { node ->
            val payload =
                TaskExecutePayload(
                    workflowId = workflowId,
                    taskId = node.task.id,
                    taskName = node.task.name,
                    queueName = node.task.queueName,
                    expectedCount = node.expectedCount,
                )
            taskExecutionRequestRepository.save(
                TaskExecutionRequest(
                    workflowId = workflowId,
                    taskId = node.task.id,
                    payload = objectMapper.writeValueAsString(payload),
                ),
            )
        }
    }

    @Transactional
    fun handleSuccessResponse(msg: TaskResponseMessage) {
        taskResponseRepository.save(
            TaskResponse(
                workflowId = msg.workflowId,
                taskId = msg.taskId,
                sequence = msg.sequence,
                payload = msg.payload,
            ),
        )

        val isComplete = incrementAndCheckCompletion(msg.workflowId, msg.taskId)
        if (isComplete) {
            val workflow = getWorkflow(msg.workflowId)
            val nextNodes = workflow.completeTask(msg.taskId)
            saveTaskExecutionRequests(msg.workflowId, nextNodes)
            logger.info {
                "action=${LogAction.COMPLETE_TASK} workflowId=${msg.workflowId} taskId=${msg.taskId} nextTasks=${nextNodes.size}"
            }
        }
    }

    @Transactional
    fun handleFailureResponse(msg: TaskResponseMessage) {
        taskResponseRepository.save(
            TaskResponse(
                workflowId = msg.workflowId,
                taskId = msg.taskId,
                sequence = msg.sequence,
                payload = msg.payload,
                status = "FAILED",
            ),
        )

        val workflow = getWorkflow(msg.workflowId)
        workflow.failTask(msg.taskId)
        logger.info {
            "action=${LogAction.FAIL_TASK} workflowId=${msg.workflowId} taskId=${msg.taskId}"
        }
    }

    fun incrementAndCheckCompletion(
        workflowId: Long,
        taskId: Long,
    ): Boolean {
        val workflow = getWorkflow(workflowId)
        val node = workflow.findNode(taskId)
        node.completedCount++
        return node.isComplete()
    }

    private fun getWorkflow(workflowId: Long): Workflow =
        workflowRepository
            .findByIdWithDetails(workflowId)
            .orElseThrow { EntityNotFoundException("Workflow", workflowId) }
}
