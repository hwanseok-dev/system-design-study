package io.lucky.security.infrastructure.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.rabbit.core.RabbitTemplate

class OutboxPollerTest :
    DescribeSpec({

        val outboxRepository = mock<OutboxRepository>()
        val rabbitTemplate = mock<RabbitTemplate>()
        val poller = OutboxPoller(outboxRepository, rabbitTemplate)

        beforeEach {
            reset(outboxRepository, rabbitTemplate)
        }

        describe("poll") {
            context("미발행 메시지가 있을 때") {
                it("RabbitMQ에 발행하고 published를 true로 마킹한다") {
                    val message =
                        OutboxMessage(
                            id = 1L,
                            aggregateType = "ORDER",
                            aggregateId = 100L,
                            eventType = "ORDER_VALIDATE",
                            exchange = "order.exchange",
                            routingKey = "order.validate",
                            payload = """{"orderId":100}""",
                        )
                    whenever(outboxRepository.findUnpublished(50)).thenReturn(listOf(message))

                    poller.poll()

                    verify(rabbitTemplate).convertAndSend("order.exchange", "order.validate", """{"orderId":100}""")
                    message.published shouldBe true
                }
            }

            context("여러 메시지가 있을 때") {
                it("각각 올바른 exchange/routingKey로 발행한다") {
                    val msg1 =
                        OutboxMessage(
                            id = 1L,
                            aggregateType = "ORDER",
                            aggregateId = 100L,
                            eventType = "ORDER_VALIDATE",
                            exchange = "order.exchange",
                            routingKey = "order.validate",
                            payload = """{"orderId":100}""",
                        )
                    val msg2 =
                        OutboxMessage(
                            id = 2L,
                            aggregateType = "EXECUTION",
                            aggregateId = 200L,
                            eventType = "EXECUTION_SETTLED",
                            exchange = "execution.exchange",
                            routingKey = "execution.settled",
                            payload = """{"executionId":200}""",
                        )
                    whenever(outboxRepository.findUnpublished(50)).thenReturn(listOf(msg1, msg2))

                    poller.poll()

                    verify(rabbitTemplate).convertAndSend("order.exchange", "order.validate", """{"orderId":100}""")
                    verify(rabbitTemplate).convertAndSend("execution.exchange", "execution.settled", """{"executionId":200}""")
                    msg1.published shouldBe true
                    msg2.published shouldBe true
                }
            }

            context("미발행 메시지가 없을 때") {
                it("RabbitMQ에 아무것도 발행하지 않는다") {
                    whenever(outboxRepository.findUnpublished(50)).thenReturn(emptyList())

                    poller.poll()

                    verify(rabbitTemplate, never()).convertAndSend(any<String>(), any<String>(), any<Any>())
                }
            }
        }
    })
