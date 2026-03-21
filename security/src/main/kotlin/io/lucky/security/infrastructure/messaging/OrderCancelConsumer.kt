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
class OrderCancelConsumer(
    private val orderService: OrderService,
    private val objectMapper: ObjectMapper,
) {
    @RabbitListener(
        queues = [OrderQueueConfig.CANCEL_CONFIRMED_QUEUE],
        containerFactory = "orderContainerFactory",
    )
    fun handleCancelConfirmed(
        message: Message,
        channel: Channel,
    ) {
        val tag = message.messageProperties.deliveryTag
        try {
            val msg = objectMapper.readTree(message.body)
            val orderId = msg["orderId"].asLong()
            orderService.onCancelConfirmed(orderId)
            channel.basicAck(tag, false)
            logger.info { "action=${LogAction.CONSUME_CANCEL_CONFIRMED} orderId=$orderId" }
        } catch (e: Exception) {
            logger.error(e) { "action=${LogAction.CONSUME_CANCEL_CONFIRMED_FAILED} deliveryTag=$tag" }
            channel.basicNack(tag, false, false)
        }
    }
}
