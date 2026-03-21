package io.lucky.security.infrastructure.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OrderQueueConfig {
    companion object {
        const val VALIDATED_QUEUE = "queue.order.validated"
        const val REJECTED_QUEUE = "queue.order.rejected"
        const val CANCEL_CONFIRMED_QUEUE = "queue.order.cancel.confirmed"
        const val VALIDATE_QUEUE = "queue.order.validate"

        const val ORDER_DLX = "order.dlx"
        const val VALIDATED_DLQ = "queue.order.validated.dlq"
        const val REJECTED_DLQ = "queue.order.rejected.dlq"
        const val CANCEL_CONFIRMED_DLQ = "queue.order.cancel.confirmed.dlq"
    }

    // Main queues
    @Bean
    fun validatedQueue(): Queue =
        QueueBuilder
            .durable(VALIDATED_QUEUE)
            .withArgument("x-dead-letter-exchange", ORDER_DLX)
            .build()

    @Bean
    fun rejectedQueue(): Queue =
        QueueBuilder
            .durable(REJECTED_QUEUE)
            .withArgument("x-dead-letter-exchange", ORDER_DLX)
            .build()

    @Bean
    fun cancelConfirmedQueue(): Queue =
        QueueBuilder
            .durable(CANCEL_CONFIRMED_QUEUE)
            .withArgument("x-dead-letter-exchange", ORDER_DLX)
            .build()

    @Bean
    fun validateQueue(): Queue =
        QueueBuilder
            .durable(VALIDATE_QUEUE)
            .withArgument("x-dead-letter-exchange", ORDER_DLX)
            .build()

    // Bindings to order.exchange
    @Bean
    fun validatedBinding(
        validatedQueue: Queue,
        orderExchange: DirectExchange,
    ): Binding = BindingBuilder.bind(validatedQueue).to(orderExchange).with(RabbitConfig.RK_ORDER_VALIDATED)

    @Bean
    fun rejectedBinding(
        rejectedQueue: Queue,
        orderExchange: DirectExchange,
    ): Binding = BindingBuilder.bind(rejectedQueue).to(orderExchange).with(RabbitConfig.RK_ORDER_REJECTED)

    @Bean
    fun cancelConfirmedBinding(
        cancelConfirmedQueue: Queue,
        orderExchange: DirectExchange,
    ): Binding = BindingBuilder.bind(cancelConfirmedQueue).to(orderExchange).with(RabbitConfig.RK_ORDER_CANCEL_CONFIRMED)

    @Bean
    fun validateBinding(
        validateQueue: Queue,
        orderExchange: DirectExchange,
    ): Binding = BindingBuilder.bind(validateQueue).to(orderExchange).with(RabbitConfig.RK_ORDER_VALIDATE)

    // DLX and DLQs
    @Bean
    fun orderDlx(): DirectExchange = DirectExchange(ORDER_DLX)

    @Bean
    fun validatedDlq(): Queue = Queue(VALIDATED_DLQ)

    @Bean
    fun rejectedDlq(): Queue = Queue(REJECTED_DLQ)

    @Bean
    fun cancelConfirmedDlq(): Queue = Queue(CANCEL_CONFIRMED_DLQ)

    @Bean
    fun validatedDlqBinding(
        validatedDlq: Queue,
        orderDlx: DirectExchange,
    ): Binding = BindingBuilder.bind(validatedDlq).to(orderDlx).with(VALIDATED_QUEUE)

    @Bean
    fun rejectedDlqBinding(
        rejectedDlq: Queue,
        orderDlx: DirectExchange,
    ): Binding = BindingBuilder.bind(rejectedDlq).to(orderDlx).with(REJECTED_QUEUE)

    @Bean
    fun cancelConfirmedDlqBinding(
        cancelConfirmedDlq: Queue,
        orderDlx: DirectExchange,
    ): Binding = BindingBuilder.bind(cancelConfirmedDlq).to(orderDlx).with(CANCEL_CONFIRMED_QUEUE)
}
