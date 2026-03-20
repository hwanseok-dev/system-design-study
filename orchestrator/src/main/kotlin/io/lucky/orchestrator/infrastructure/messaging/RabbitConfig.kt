package io.lucky.orchestrator.infrastructure.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig {
    companion object {
        const val TASK_EXCHANGE = "task.exchange"
        const val TASK_EXECUTE_ROUTING_KEY = "task.execute"

        const val RESPONSE_EXCHANGE = "response.exchange"
        const val RESPONSE_DLX = "response.dlx"

        const val SUCCESS_QUEUE = "queue.response.success"
        const val SUCCESS_ROUTING_KEY = "response.success"
        const val SUCCESS_DLQ = "queue.response.success.dlq"

        const val FAILURE_QUEUE = "queue.response.failure"
        const val FAILURE_ROUTING_KEY = "response.failure"
        const val FAILURE_DLQ = "queue.response.failure.dlq"
    }

    @Bean
    fun taskExchange() = DirectExchange(TASK_EXCHANGE)

    @Bean
    fun responseExchange() = DirectExchange(RESPONSE_EXCHANGE)

    @Bean
    fun responseDlx() = DirectExchange(RESPONSE_DLX)

    @Bean
    fun successQueue(): Queue =
        QueueBuilder
            .durable(SUCCESS_QUEUE)
            .withArgument("x-dead-letter-exchange", RESPONSE_DLX)
            .withArgument("x-dead-letter-routing-key", SUCCESS_ROUTING_KEY)
            .build()

    @Bean
    fun failureQueue(): Queue =
        QueueBuilder
            .durable(FAILURE_QUEUE)
            .withArgument("x-dead-letter-exchange", RESPONSE_DLX)
            .withArgument("x-dead-letter-routing-key", FAILURE_ROUTING_KEY)
            .build()

    @Bean
    fun successDlq() = Queue(SUCCESS_DLQ)

    @Bean
    fun failureDlq() = Queue(FAILURE_DLQ)

    @Bean
    fun successBinding(): Binding = BindingBuilder.bind(successQueue()).to(responseExchange()).with(SUCCESS_ROUTING_KEY)

    @Bean
    fun failureBinding(): Binding = BindingBuilder.bind(failureQueue()).to(responseExchange()).with(FAILURE_ROUTING_KEY)

    @Bean
    fun successDlqBinding(): Binding = BindingBuilder.bind(successDlq()).to(responseDlx()).with(SUCCESS_ROUTING_KEY)

    @Bean
    fun failureDlqBinding(): Binding = BindingBuilder.bind(failureDlq()).to(responseDlx()).with(FAILURE_ROUTING_KEY)

    @Bean
    fun jsonMessageConverter() = Jackson2JsonMessageConverter()

    @Bean
    fun successContainerFactory(cf: ConnectionFactory): SimpleRabbitListenerContainerFactory =
        SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(cf)
            setConcurrentConsumers(1)
            setPrefetchCount(1)
            setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL)
            setMessageConverter(jsonMessageConverter())
        }
}
