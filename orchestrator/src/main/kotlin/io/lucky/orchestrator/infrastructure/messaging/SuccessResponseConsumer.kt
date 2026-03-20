package io.lucky.orchestrator.infrastructure.messaging

import com.rabbitmq.client.Channel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.application.WorkflowService
import io.lucky.orchestrator.domain.LogAction
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SuccessResponseConsumer(
    private val workflowService: WorkflowService,
) {
    @RabbitListener(
        queues = [RabbitConfig.SUCCESS_QUEUE],
        containerFactory = "successContainerFactory",
    )
    fun handle(
        message: TaskResponseMessage,
        channel: Channel,
        @Header(AmqpHeaders.DELIVERY_TAG) tag: Long,
    ) {
        try {
            workflowService.handleSuccessResponse(message)
            channel.basicAck(tag, false)
            logger.info {
                "action=${LogAction.HANDLE_SUCCESS_RESPONSE} workflowId=${message.workflowId} taskId=${message.taskId} sequence=${message.sequence}"
            }
        } catch (e: Exception) {
            logger.error(e) {
                "action=${LogAction.HANDLE_SUCCESS_RESPONSE_FAILED} workflowId=${message.workflowId} taskId=${message.taskId} sequence=${message.sequence}"
            }
            channel.basicNack(tag, false, false)
        }
    }
}
