package io.lucky.orchestrator.api

import io.lucky.orchestrator.domain.EntityNotFoundException
import io.lucky.orchestrator.domain.task.Task
import io.lucky.orchestrator.infrastructure.persistence.TaskRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/tasks")
class TaskController(
    private val taskRepository: TaskRepository,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateTaskRequest,
    ): TaskResponse {
        val task =
            taskRepository.save(
                Task(
                    name = request.name,
                    queueName = request.queueName,
                    expectedCount = request.expectedCount,
                ),
            )
        return task.toResponse()
    }

    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: Long,
    ): TaskResponse {
        val task =
            taskRepository
                .findById(id)
                .orElseThrow { EntityNotFoundException("Task", id) }
        return task.toResponse()
    }

    @GetMapping
    fun findAll(): List<TaskResponse> = taskRepository.findAll().map { it.toResponse() }

    private fun Task.toResponse() =
        TaskResponse(
            id = id,
            name = name,
            queueName = queueName,
            expectedCount = expectedCount,
        )
}
