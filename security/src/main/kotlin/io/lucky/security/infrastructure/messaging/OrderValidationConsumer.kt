package io.lucky.security.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.application.OrderService
import io.lucky.security.domain.LogAction
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class OrderValidationConsumer(
    private val orderService: OrderService,
    private val objectMapper: ObjectMapper,
) {
    @RabbitListener(queues = [OrderQueueConfig.VALIDATED_QUEUE], containerFactory = "orderContainerFactory")
    fun handleValidated(
        message: Message,
        channel: Channel,
    ) {
        val tag = message.messageProperties.deliveryTag
        try {
            val msg = objectMapper.readTree(message.body)
            val orderId = msg["orderId"].asLong()
            orderService.onValidated(orderId)
            channel.basicAck(tag, false)
            logger.info { "action=${LogAction.CONSUME_VALIDATED} orderId=$orderId" }
        } catch (e: Exception) {
            logger.error(e) { "action=${LogAction.CONSUME_VALIDATED_FAILED} deliveryTag=$tag" }
            channel.basicNack(tag, false, false)
        }
    }

    @RabbitListener(queues = [OrderQueueConfig.REJECTED_QUEUE], containerFactory = "orderContainerFactory")
    fun handleRejected(
        message: Message,
        channel: Channel,
    ) {
        val tag = message.messageProperties.deliveryTag
        try {
            val msg = objectMapper.readTree(message.body)
            val orderId = msg["orderId"].asLong()
            val reason = msg["reason"]?.asText() ?: "unknown"
            orderService.onRejected(orderId, reason)
            channel.basicAck(tag, false)
            logger.info { "action=${LogAction.CONSUME_REJECTED} orderId=$orderId reason=$reason" }
        } catch (e: Exception) {
            logger.error(e) { "action=${LogAction.CONSUME_REJECTED_FAILED} deliveryTag=$tag" }
            channel.basicNack(tag, false, false)
        }
    }
}
