package io.lucky.security.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.application.ExecutionDbService
import io.lucky.security.application.ExecutionResultPayload
import io.lucky.security.application.ExecutionService
import io.lucky.security.domain.LogAction
import io.lucky.security.infrastructure.redis.RedisScriptConfig
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Component
class ExecutionResultConsumer(
    private val executionService: ExecutionService,
    private val executionDbService: ExecutionDbService,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
    private val checkAndIncrementFillScript: DefaultRedisScript<Long>,
) {
    @RabbitListener(
        queues = [ExecutionQueueConfig.EXECUTION_RESULT_QUEUE],
        containerFactory = "executionContainerFactory",
    )
    fun handleBatch(
        messages: List<Message>,
        channel: Channel,
    ) {
        val parsed =
            messages.mapNotNull { raw ->
                try {
                    raw to objectMapper.readValue(raw.body, ExecutionResultPayload::class.java)
                } catch (e: Exception) {
                    logger.error(e) {
                        "action=${LogAction.CONSUME_EXECUTION_FAILED} " +
                            "deliveryTag=${raw.messageProperties.deliveryTag}"
                    }
                    channel.basicNack(raw.messageProperties.deliveryTag, false, false)
                    null
                }
            }

        val grouped = parsed.groupBy { (_, payload) -> payload.orderId }

        for ((orderId, group) in grouped) {
            try {
                processGroup(orderId, group, channel)
            } catch (e: Exception) {
                logger.error(e) {
                    "action=${LogAction.CONSUME_EXECUTION_FAILED} orderId=$orderId batchSize=${group.size}"
                }
                group.forEach { (raw, _) ->
                    channel.basicNack(raw.messageProperties.deliveryTag, false, false)
                }
            }
        }
    }

    private fun processGroup(
        orderId: Long,
        group: List<Pair<Message, ExecutionResultPayload>>,
        channel: Channel,
    ) {
        val cancelKey = "{order:$orderId}:cancelled"
        val filledKey = "{order:$orderId}:filled_qty"

        // Dedup each execution individually
        val newExecs =
            group
                .map { (_, payload) -> payload }
                .filter { exec ->
                    val dedupKey = "{order:$orderId}:dedup:${exec.exchangeExecId}"
                    redisTemplate
                        .opsForValue()
                        .setIfAbsent(dedupKey, "1", Duration.ofSeconds(RedisScriptConfig.DEDUP_TTL_SECONDS)) == true
                }

        if (newExecs.isEmpty()) {
            ackGroup(group, channel)
            return
        }

        // Lua: cancel check + batch increment
        val totalQty = newExecs.sumOf { it.quantity }
        val totalFilled =
            redisTemplate.execute(
                checkAndIncrementFillScript,
                listOf(cancelKey, filledKey),
                totalQty.toString(),
            )

        if (totalFilled == -1L) {
            logger.info {
                "action=${LogAction.EXECUTION_SKIPPED_CANCELLED} orderId=$orderId batchSize=${newExecs.size}"
            }
            ackGroup(group, channel)
            return
        }

        executionDbService.applyExecutionBatch(orderId, newExecs, totalFilled!!)
        ackGroup(group, channel)

        logger.info {
            "action=${LogAction.CONSUME_EXECUTION_BATCH} orderId=$orderId batchSize=${newExecs.size} " +
                "totalFilled=$totalFilled"
        }
    }

    private fun ackGroup(
        group: List<Pair<Message, ExecutionResultPayload>>,
        channel: Channel,
    ) {
        group.forEach { (raw, _) ->
            channel.basicAck(raw.messageProperties.deliveryTag, false)
        }
    }
}
