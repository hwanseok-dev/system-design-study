package io.lucky.security.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.LogAction
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
@Profile("local")
class ExecutionSimulator(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
) {
    @RabbitListener(queues = [ExecutionQueueConfig.EXECUTE_QUEUE])
    fun simulate(message: Message) {
        val msg = objectMapper.readTree(message.body)
        val orderId = msg["orderId"].asLong()
        val userId = msg["userId"].asLong()
        val stockCode = msg["stockCode"].asText()
        val side = msg["side"].asText()
        val quantity = msg["quantity"].asInt()
        val price = msg["price"]?.decimalValue() ?: return

        val payload =
            objectMapper.writeValueAsString(
                mapOf(
                    "orderId" to orderId,
                    "userId" to userId,
                    "stockCode" to stockCode,
                    "side" to side,
                    "quantity" to quantity,
                    "price" to price,
                    "exchangeExecId" to UUID.randomUUID().toString(),
                    "executedAt" to Instant.now().toString(),
                ),
            )

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXECUTION_EXCHANGE,
            RabbitConfig.RK_EXECUTION_RESULT,
            payload,
        )

        logger.info {
            "action=${LogAction.SIMULATE_EXECUTION} orderId=$orderId quantity=$quantity price=$price"
        }
    }
}
