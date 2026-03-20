package io.lucky.orchestrator.infrastructure.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.infrastructure.messaging.RabbitConfig
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Component
class TaskExecutionPublisher(
    private val requestRepository: TaskExecutionRequestRepository,
    private val rabbitTemplate: RabbitTemplate,
) {
    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun poll() {
        val requests = requestRepository.findUnpublished(100)
        if (requests.isEmpty()) return

        requests.forEach { req ->
            rabbitTemplate.convertAndSend(RabbitConfig.TASK_EXCHANGE, RabbitConfig.TASK_EXECUTE_ROUTING_KEY, req.payload)
            req.published = true
            req.publishedAt = Instant.now()
            logger.info {
                "action=${LogAction.PUBLISH_TASK_EXECUTION} workflowId=${req.workflowId} taskId=${req.taskId}"
            }
        }
    }
}
