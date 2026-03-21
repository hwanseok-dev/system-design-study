package io.lucky.security.infrastructure.messaging

import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig {
    companion object {
        // Exchanges
        const val ORDER_EXCHANGE = "order.exchange"
        const val EXECUTION_EXCHANGE = "execution.exchange"
        const val NOTIFICATION_EXCHANGE = "notification.exchange"

        // Routing keys
        const val RK_ORDER_VALIDATE = "order.validate"
        const val RK_ORDER_VALIDATED = "order.validated"
        const val RK_ORDER_REJECTED = "order.rejected"
        const val RK_ORDER_EXECUTE = "order.execute"
        const val RK_ORDER_CANCEL_CONFIRMED = "order.cancel.confirmed"
        const val RK_EXECUTION_SETTLED = "execution.settled"
    }

    @Bean
    fun orderExchange() = DirectExchange(ORDER_EXCHANGE)

    @Bean
    fun executionExchange() = DirectExchange(EXECUTION_EXCHANGE)

    @Bean
    fun notificationExchange() = TopicExchange(NOTIFICATION_EXCHANGE)
}
