package io.lucky.orchestrator.infrastructure.messaging

import com.rabbitmq.client.Channel
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.application.TaskResponseHandler
import io.lucky.orchestrator.domain.LogAction
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SuccessResponseConsumer(
    private val taskResponseHandler: TaskResponseHandler,
    private val jsonMessageConverter: Jackson2JsonMessageConverter,
) {
    @RabbitListener(
        queues = [RabbitConfig.SUCCESS_QUEUE],
        containerFactory = "successContainerFactory",
    )
    fun handleBatch(
        messages: List<Message>,
        channel: Channel,
    ) {
        val parsed =
            messages.map { raw ->
                raw to (jsonMessageConverter.fromMessage(raw) as TaskResponseMessage)
            }
        val grouped = parsed.groupBy { (_, msg) -> msg.workflowId to msg.taskId }

        for ((key, group) in grouped) {
            val (workflowId, taskId) = key
            try {
                taskResponseHandler.handleSuccessBatch(
                    workflowId,
                    taskId,
                    group.map { it.second },
                )
                group.forEach { (raw, _) ->
                    channel.basicAck(raw.messageProperties.deliveryTag, false)
                }
                logger.info {
                    "action=${LogAction.HANDLE_SUCCESS_RESPONSE} workflowId=$workflowId taskId=$taskId batchSize=${group.size}"
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "action=${LogAction.HANDLE_SUCCESS_RESPONSE_FAILED} workflowId=$workflowId taskId=$taskId batchSize=${group.size}"
                }
                group.forEach { (raw, _) ->
                    channel.basicNack(raw.messageProperties.deliveryTag, false, false)
                }
            }
        }
    }
}
