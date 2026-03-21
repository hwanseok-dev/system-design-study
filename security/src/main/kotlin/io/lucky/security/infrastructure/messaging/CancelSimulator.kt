package io.lucky.security.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.LogAction
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@Profile("local")
class CancelSimulator(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
) {
    @RabbitListener(queues = [OrderQueueConfig.CANCEL_QUEUE])
    fun simulate(message: Message) {
        val msg = objectMapper.readTree(message.body)
        val orderId = msg["orderId"].asLong()

        val payload = objectMapper.writeValueAsString(mapOf("orderId" to orderId))
        rabbitTemplate.convertAndSend(
            RabbitConfig.ORDER_EXCHANGE,
            RabbitConfig.RK_ORDER_CANCEL_CONFIRMED,
            payload,
        )

        logger.info { "action=${LogAction.SIMULATE_CANCEL_CONFIRMED} orderId=$orderId" }
    }
}
