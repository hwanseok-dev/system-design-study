package io.lucky.orchestrator.infrastructure.persistence

import io.lucky.orchestrator.domain.response.TaskResponse
import org.springframework.data.jpa.repository.JpaRepository

interface TaskResponseRepository : JpaRepository<TaskResponse, Long>
