package io.lucky.security.infrastructure.messaging

import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig {
    companion object {
        const val ORDER_EXCHANGE = "order.exchange"
        const val EXECUTION_EXCHANGE = "execution.exchange"
        const val NOTIFICATION_EXCHANGE = "notification.exchange"
    }

    @Bean
    fun orderExchange() = DirectExchange(ORDER_EXCHANGE)

    @Bean
    fun executionExchange() = DirectExchange(EXECUTION_EXCHANGE)

    @Bean
    fun notificationExchange() = TopicExchange(NOTIFICATION_EXCHANGE)
}
