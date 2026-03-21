package io.lucky.security.application

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.LogAction
import io.lucky.security.infrastructure.redis.RedisScriptConfig
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ExecutionService(
    private val redisTemplate: StringRedisTemplate,
    private val checkDedupAndIncrementFillScript: DefaultRedisScript<Long>,
    private val executionDbService: ExecutionDbService,
) {
    fun applyExecution(payload: ExecutionResultPayload) {
        val cancelKey = "{order:${payload.orderId}}:cancelled"
        val filledKey = "{order:${payload.orderId}}:filled_qty"
        val dedupKey = "{order:${payload.orderId}}:dedup:${payload.exchangeExecId}"

        val result =
            redisTemplate.execute(
                checkDedupAndIncrementFillScript,
                listOf(cancelKey, filledKey, dedupKey),
                payload.quantity.toString(),
                RedisScriptConfig.DEDUP_TTL_SECONDS.toString(),
            )

        when (result) {
            -1L -> {
                logger.info {
                    "action=${LogAction.EXECUTION_SKIPPED_CANCELLED} orderId=${payload.orderId} " +
                        "exchangeExecId=${payload.exchangeExecId}"
                }
                return
            }
            -2L -> {
                logger.info {
                    "action=${LogAction.EXECUTION_SKIPPED_DUPLICATE} orderId=${payload.orderId} " +
                        "exchangeExecId=${payload.exchangeExecId}"
                }
                return
            }
            else -> executionDbService.applyExecutionToDb(payload, result!!)
        }
    }
}
