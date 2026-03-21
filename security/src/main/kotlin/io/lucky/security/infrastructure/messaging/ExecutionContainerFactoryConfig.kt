package io.lucky.security.infrastructure.messaging

import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ExecutionContainerFactoryConfig {
    @Bean
    fun executionContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory =
        SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory)
            setConcurrentConsumers(10)
            setMaxConcurrentConsumers(50)
            setPrefetchCount(50)
            setConsumerBatchEnabled(true)
            setBatchSize(100)
            setAcknowledgeMode(AcknowledgeMode.MANUAL)
        }
}
