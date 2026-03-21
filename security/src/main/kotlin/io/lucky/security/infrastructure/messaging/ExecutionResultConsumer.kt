package io.lucky.security.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.application.ExecutionResultPayload
import io.lucky.security.application.ExecutionService
import io.lucky.security.domain.LogAction
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class ExecutionResultConsumer(
    private val executionService: ExecutionService,
    private val objectMapper: ObjectMapper,
) {
    @RabbitListener(
        queues = [ExecutionQueueConfig.EXECUTION_RESULT_QUEUE],
        containerFactory = "executionContainerFactory",
    )
    fun handle(
        message: Message,
        channel: Channel,
    ) {
        val tag = message.messageProperties.deliveryTag
        try {
            val payload = objectMapper.readValue(message.body, ExecutionResultPayload::class.java)
            executionService.applyExecution(payload)
            channel.basicAck(tag, false)
            logger.info {
                "action=${LogAction.CONSUME_EXECUTION} orderId=${payload.orderId} " +
                    "exchangeExecId=${payload.exchangeExecId}"
            }
        } catch (e: Exception) {
            logger.error(e) { "action=${LogAction.CONSUME_EXECUTION_FAILED} deliveryTag=$tag" }
            channel.basicNack(tag, false, false)
        }
    }
}
