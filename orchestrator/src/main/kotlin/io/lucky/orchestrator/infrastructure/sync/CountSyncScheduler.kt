package io.lucky.orchestrator.infrastructure.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.domain.workflow.WorkflowStatus
import io.lucky.orchestrator.infrastructure.persistence.WorkflowRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CountSyncScheduler(
    private val workflowRepository: WorkflowRepository,
    private val redisTemplate: StringRedisTemplate,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Scheduled(fixedDelay = 5000)
    fun sync() {
        val activeWorkflows = workflowRepository.findByStatus(WorkflowStatus.RUNNING)

        for (workflow in activeWorkflows) {
            for (node in workflow.nodes) {
                val countKey = "{wf:${workflow.id}}:task:${node.task.id}:count"
                val redisCount = redisTemplate.opsForValue().get(countKey)?.toInt() ?: continue

                jdbcTemplate.update(
                    "UPDATE workflow_node SET completed_count = ? WHERE id = ? AND completed_count < ?",
                    redisCount,
                    node.id,
                    redisCount,
                )
            }
        }

        if (activeWorkflows.isNotEmpty()) {
            val totalNodes = activeWorkflows.sumOf { it.nodes.size }
            logger.info {
                "action=${LogAction.SYNC_COUNT} workflows=${activeWorkflows.size} nodes=$totalNodes"
            }
        }
    }
}
