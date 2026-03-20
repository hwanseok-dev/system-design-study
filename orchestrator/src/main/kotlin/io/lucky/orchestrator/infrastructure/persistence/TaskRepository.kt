package io.lucky.orchestrator.infrastructure.persistence

import io.lucky.orchestrator.domain.task.Task
import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository : JpaRepository<Task, Long>
