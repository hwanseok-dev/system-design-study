package io.lucky.security.infrastructure.outbox

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.lucky.security.application.OrderService
import io.lucky.security.infrastructure.messaging.RabbitConfig
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
        val orderService = mock<OrderService>()
        val poller = OutboxPoller(outboxRepository, rabbitTemplate, orderService)

        beforeEach {
            reset(outboxRepository, rabbitTemplate, orderService)
        }

        describe("poll") {
            context("미발행 메시지가 있을 때") {
                it("RabbitMQ에 발행하고 published를 true로 마킹한다") {
                    val message =
                        OutboxMessage(
                            id = 1L,
                            aggregateType = AggregateType.ORDER,
                            aggregateId = 100L,
                            eventType = OutboxEventType.ORDER_VALIDATE,
                            exchange = RabbitConfig.ORDER_EXCHANGE,
                            routingKey = RabbitConfig.RK_ORDER_VALIDATE,
                            payload = """{"orderId":100}""",
                        )
                    whenever(outboxRepository.findUnpublished(50)).thenReturn(listOf(message))

                    poller.poll()

                    verify(rabbitTemplate).convertAndSend(
                        RabbitConfig.ORDER_EXCHANGE,
                        RabbitConfig.RK_ORDER_VALIDATE,
                        """{"orderId":100}""",
                    )
                    message.published shouldBe true
                }
            }

            context("여러 메시지가 있을 때") {
                it("각각 올바른 exchange/routingKey로 발행한다") {
                    val msg1 =
                        OutboxMessage(
                            id = 1L,
                            aggregateType = AggregateType.ORDER,
                            aggregateId = 100L,
                            eventType = OutboxEventType.ORDER_VALIDATE,
                            exchange = RabbitConfig.ORDER_EXCHANGE,
                            routingKey = RabbitConfig.RK_ORDER_VALIDATE,
                            payload = """{"orderId":100}""",
                        )
                    val msg2 =
                        OutboxMessage(
                            id = 2L,
                            aggregateType = AggregateType.EXECUTION,
                            aggregateId = 200L,
                            eventType = OutboxEventType.EXECUTION_SETTLED,
                            exchange = RabbitConfig.EXECUTION_EXCHANGE,
                            routingKey = RabbitConfig.RK_EXECUTION_SETTLED,
                            payload = """{"executionId":200}""",
                        )
                    whenever(outboxRepository.findUnpublished(50)).thenReturn(listOf(msg1, msg2))

                    poller.poll()

                    verify(rabbitTemplate).convertAndSend(
                        RabbitConfig.ORDER_EXCHANGE,
                        RabbitConfig.RK_ORDER_VALIDATE,
                        """{"orderId":100}""",
                    )
                    verify(rabbitTemplate).convertAndSend(
                        RabbitConfig.EXECUTION_EXCHANGE,
                        RabbitConfig.RK_EXECUTION_SETTLED,
                        """{"executionId":200}""",
                    )
                    msg1.published shouldBe true
                    msg2.published shouldBe true
                }
            }

            context("ORDER_EXECUTE 이벤트를 발행할 때") {
                it("발행 후 주문을 SUBMITTED로 전이한다") {
                    val message =
                        OutboxMessage(
                            id = 3L,
                            aggregateType = AggregateType.ORDER,
                            aggregateId = 100L,
                            eventType = OutboxEventType.ORDER_EXECUTE,
                            exchange = RabbitConfig.ORDER_EXCHANGE,
                            routingKey = RabbitConfig.RK_ORDER_EXECUTE,
                            payload = """{"orderId":100}""",
                        )
                    whenever(outboxRepository.findUnpublished(50)).thenReturn(listOf(message))

                    poller.poll()

                    verify(rabbitTemplate).convertAndSend(
                        RabbitConfig.ORDER_EXCHANGE,
                        RabbitConfig.RK_ORDER_EXECUTE,
                        """{"orderId":100}""",
                    )
                    verify(orderService).submitOrder(100L)
                    message.published shouldBe true
                }
            }

            context("ORDER_VALIDATE 이벤트를 발행할 때") {
                it("submitOrder를 호출하지 않는다") {
                    val message =
                        OutboxMessage(
                            id = 1L,
                            aggregateType = AggregateType.ORDER,
                            aggregateId = 100L,
                            eventType = OutboxEventType.ORDER_VALIDATE,
                            exchange = RabbitConfig.ORDER_EXCHANGE,
                            routingKey = RabbitConfig.RK_ORDER_VALIDATE,
                            payload = """{"orderId":100}""",
                        )
                    whenever(outboxRepository.findUnpublished(50)).thenReturn(listOf(message))

                    poller.poll()

                    verify(orderService, never()).submitOrder(any())
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
