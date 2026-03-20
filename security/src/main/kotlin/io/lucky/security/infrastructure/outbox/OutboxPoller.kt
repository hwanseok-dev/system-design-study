package io.lucky.security.infrastructure.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.security.domain.LogAction
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
class OutboxPoller(
    private val outboxRepository: OutboxRepository,
    private val rabbitTemplate: RabbitTemplate,
) {
    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun poll() {
        val messages = outboxRepository.findUnpublished(50)
        if (messages.isEmpty()) return

        messages.forEach { message ->
            rabbitTemplate.convertAndSend(message.exchange, message.routingKey, message.payload)
            message.published = true
            logger.info {
                "action=${LogAction.PUBLISH_OUTBOX} aggregateType=${message.aggregateType} " +
                    "aggregateId=${message.aggregateId} eventType=${message.eventType}"
            }
        }
    }
}
