package io.lucky.orchestrator.application

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.infrastructure.messaging.TaskResponseMessage
import io.lucky.orchestrator.infrastructure.persistence.TaskResponseRepository
import io.lucky.orchestrator.infrastructure.redis.RedisScriptConfig
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TaskResponseHandler(
    private val workflowService: WorkflowService,
    private val taskResponseRepository: TaskResponseRepository,
    private val redisTemplate: StringRedisTemplate,
    private val checkDedupAndIncrementScript: DefaultRedisScript<Long>,
    private val checkAndFailScript: DefaultRedisScript<Long>,
) {
    fun handleSuccessResponse(msg: TaskResponseMessage) {
        val failKey = "{wf:${msg.workflowId}}:task:${msg.taskId}:failed"
        val countKey = "{wf:${msg.workflowId}}:task:${msg.taskId}:count"
        val dedupKey = "{wf:${msg.workflowId}}:dedup:${msg.taskId}:${msg.sequence}"

        val count =
            redisTemplate.execute(
                checkDedupAndIncrementScript,
                listOf(failKey, countKey, dedupKey),
                RedisScriptConfig.DEDUP_TTL_SECONDS.toString(),
            )

        if (count == -1L) {
            logger.info {
                "action=${LogAction.SKIP_SUCCESS_ALREADY_FAILED} workflowId=${msg.workflowId} taskId=${msg.taskId} sequence=${msg.sequence}"
            }
            return
        }
        if (count == -2L) {
            logger.info {
                "action=${LogAction.SKIP_DUPLICATE_MESSAGE} workflowId=${msg.workflowId} taskId=${msg.taskId} sequence=${msg.sequence}"
            }
            return
        }

        taskResponseRepository.insertIgnoreDuplicate(
            workflowId = msg.workflowId,
            taskId = msg.taskId,
            sequence = msg.sequence,
            payload = msg.payload,
            status = "RECEIVED",
        )

        val expectedCount = workflowService.getExpectedCount(msg.workflowId, msg.taskId)
        if (count >= expectedCount.toLong()) {
            workflowService.completeTask(msg.workflowId, msg.taskId)
        }
    }

    fun handleFailureResponse(msg: TaskResponseMessage) {
        val failKey = "{wf:${msg.workflowId}}:task:${msg.taskId}:failed"
        val isFirst =
            redisTemplate.execute(
                checkAndFailScript,
                listOf(failKey),
                RedisScriptConfig.FAIL_KEY_TTL_SECONDS.toString(),
            )

        if (isFirst != 1L) {
            logger.info {
                "action=${LogAction.SKIP_FAILURE_ALREADY_FAILED} workflowId=${msg.workflowId} taskId=${msg.taskId} sequence=${msg.sequence}"
            }
            return
        }

        taskResponseRepository.insertIgnoreDuplicate(
            workflowId = msg.workflowId,
            taskId = msg.taskId,
            sequence = msg.sequence,
            payload = msg.payload,
            status = "FAILED",
        )

        workflowService.failTask(msg.workflowId, msg.taskId)
    }
}
