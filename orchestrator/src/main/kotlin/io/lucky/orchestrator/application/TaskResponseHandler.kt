package io.lucky.orchestrator.application

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.infrastructure.messaging.TaskResponseMessage
import io.lucky.orchestrator.infrastructure.persistence.TaskResponseBatchRepository
import io.lucky.orchestrator.infrastructure.persistence.TaskResponseRepository
import io.lucky.orchestrator.infrastructure.redis.RedisScriptConfig
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class TaskResponseHandler(
    private val workflowService: WorkflowService,
    private val taskResponseRepository: TaskResponseRepository,
    private val taskResponseBatchRepository: TaskResponseBatchRepository,
    private val redisTemplate: StringRedisTemplate,
    private val checkDedupAndIncrementScript: DefaultRedisScript<Long>,
    private val checkFailAndIncrementScript: DefaultRedisScript<Long>,
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

    fun handleSuccessBatch(
        workflowId: Long,
        taskId: Long,
        messages: List<TaskResponseMessage>,
    ) {
        val dedupTtl = Duration.ofSeconds(RedisScriptConfig.DEDUP_TTL_SECONDS)
        val validMessages =
            messages.filter { msg ->
                val dedupKey = "{wf:$workflowId}:dedup:$taskId:${msg.sequence}"
                redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", dedupTtl) == true
            }

        if (validMessages.isEmpty()) return

        val failKey = "{wf:$workflowId}:task:$taskId:failed"
        val countKey = "{wf:$workflowId}:task:$taskId:count"
        val count =
            redisTemplate.execute(
                checkFailAndIncrementScript,
                listOf(failKey, countKey),
                validMessages.size.toString(),
            )

        if (count == -1L) {
            logger.info {
                "action=${LogAction.SKIP_SUCCESS_ALREADY_FAILED} workflowId=$workflowId taskId=$taskId batchSize=${validMessages.size}"
            }
            return
        }

        taskResponseBatchRepository.batchInsert(validMessages)

        val expectedCount = workflowService.getExpectedCount(workflowId, taskId)
        if (count >= expectedCount.toLong()) {
            workflowService.completeTask(workflowId, taskId)
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
