package io.lucky.orchestrator.infrastructure.persistence

import io.lucky.orchestrator.domain.workflow.Workflow
import org.springframework.data.jpa.repository.JpaRepository

interface WorkflowRepository : JpaRepository<Workflow, Long>
