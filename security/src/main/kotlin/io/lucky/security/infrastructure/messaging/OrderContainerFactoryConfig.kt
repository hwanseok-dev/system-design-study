package io.lucky.security.infrastructure.messaging

import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OrderContainerFactoryConfig {
    @Bean
    fun orderContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory =
        SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory)
            setConcurrentConsumers(5)
            setMaxConcurrentConsumers(20)
            setPrefetchCount(10)
            setAcknowledgeMode(AcknowledgeMode.MANUAL)
        }
}
