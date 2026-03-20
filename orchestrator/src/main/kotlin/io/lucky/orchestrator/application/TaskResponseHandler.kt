package io.lucky.orchestrator.application

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.domain.response.TaskResponse
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
    private val checkAndIncrementScript: DefaultRedisScript<Long>,
    private val checkAndFailScript: DefaultRedisScript<Long>,
) {
    fun handleSuccessResponse(msg: TaskResponseMessage) {
        val failKey = "{wf:${msg.workflowId}}:task:${msg.taskId}:failed"
        val countKey = "{wf:${msg.workflowId}}:task:${msg.taskId}:count"

        val count = redisTemplate.execute(checkAndIncrementScript, listOf(failKey, countKey))
        if (count == -1L) {
            logger.info {
                "action=${LogAction.SKIP_SUCCESS_ALREADY_FAILED} workflowId=${msg.workflowId} taskId=${msg.taskId} sequence=${msg.sequence}"
            }
            return
        }

        taskResponseRepository.save(
            TaskResponse(
                workflowId = msg.workflowId,
                taskId = msg.taskId,
                sequence = msg.sequence,
                payload = msg.payload,
            ),
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

        taskResponseRepository.save(
            TaskResponse(
                workflowId = msg.workflowId,
                taskId = msg.taskId,
                sequence = msg.sequence,
                payload = msg.payload,
                status = "FAILED",
            ),
        )

        workflowService.failTask(msg.workflowId, msg.taskId)
    }
}
