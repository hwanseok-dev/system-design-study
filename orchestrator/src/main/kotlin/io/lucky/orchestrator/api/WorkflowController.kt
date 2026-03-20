package io.lucky.orchestrator.api

import io.lucky.orchestrator.application.WorkflowService
import io.lucky.orchestrator.domain.workflow.Workflow
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/workflows")
class WorkflowController(
    private val workflowService: WorkflowService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateWorkflowRequest,
    ): WorkflowResponse {
        val workflow = workflowService.create(request.name, request.tasks, request.edges)
        return workflow.toResponse()
    }

    @PostMapping("/{id}/start")
    fun start(
        @PathVariable id: Long,
    ): StartWorkflowResponse {
        val readyNodes = workflowService.start(id)
        val workflow = workflowService.findById(id)
        return StartWorkflowResponse(
            workflowId = workflow.id,
            status = workflow.status.name,
            runningTasks = readyNodes.map { it.task.name },
        )
    }

    @GetMapping("/{id}/progress")
    fun getProgress(
        @PathVariable id: Long,
    ): WorkflowProgressResponse = workflowService.getProgress(id)

    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: Long,
    ): WorkflowResponse {
        val workflow = workflowService.findById(id)
        return workflow.toResponse()
    }

    private fun Workflow.toResponse() =
        WorkflowResponse(
            id = id,
            name = name,
            status = status.name,
            tasks =
                nodes.map {
                    WorkflowTaskResponse(
                        taskId = it.task.id,
                        taskName = it.task.name,
                        status = it.status.name,
                        expectedCount = it.expectedCount,
                        completedCount = it.completedCount,
                    )
                },
        )
}
