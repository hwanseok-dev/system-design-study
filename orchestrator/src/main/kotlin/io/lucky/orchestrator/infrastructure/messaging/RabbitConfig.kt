package io.lucky.orchestrator.infrastructure.messaging

import org.springframework.amqp.core.DirectExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig {
    companion object {
        const val TASK_EXCHANGE = "task.exchange"
        const val TASK_EXECUTE_ROUTING_KEY = "task.execute"
    }

    @Bean
    fun taskExchange() = DirectExchange(TASK_EXCHANGE)
}
