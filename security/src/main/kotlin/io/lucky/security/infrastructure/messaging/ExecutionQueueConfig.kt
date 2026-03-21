package io.lucky.security.infrastructure.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ExecutionQueueConfig {
    companion object {
        const val EXECUTION_RESULT_QUEUE = "queue.execution.result"
        const val EXECUTE_QUEUE = "queue.order.execute"

        const val EXECUTION_DLX = "execution.dlx"
        const val EXECUTION_RESULT_DLQ = "queue.execution.result.dlq"
    }

    // Main queues
    @Bean
    fun executionResultQueue(): Queue =
        QueueBuilder
            .durable(EXECUTION_RESULT_QUEUE)
            .withArgument("x-dead-letter-exchange", EXECUTION_DLX)
            .build()

    @Bean
    fun executeQueue(): Queue =
        QueueBuilder
            .durable(EXECUTE_QUEUE)
            .withArgument("x-dead-letter-exchange", OrderQueueConfig.ORDER_DLX)
            .build()

    // Bindings
    @Bean
    fun executionResultBinding(
        executionResultQueue: Queue,
        executionExchange: DirectExchange,
    ): Binding = BindingBuilder.bind(executionResultQueue).to(executionExchange).with(RabbitConfig.RK_EXECUTION_RESULT)

    @Bean
    fun executeBinding(
        executeQueue: Queue,
        orderExchange: DirectExchange,
    ): Binding = BindingBuilder.bind(executeQueue).to(orderExchange).with(RabbitConfig.RK_ORDER_EXECUTE)

    // DLX and DLQ
    @Bean
    fun executionDlx(): DirectExchange = DirectExchange(EXECUTION_DLX)

    @Bean
    fun executionResultDlq(): Queue = Queue(EXECUTION_RESULT_DLQ)

    @Bean
    fun executionResultDlqBinding(
        executionResultDlq: Queue,
        executionDlx: DirectExchange,
    ): Binding = BindingBuilder.bind(executionResultDlq).to(executionDlx).with(EXECUTION_RESULT_QUEUE)
}
