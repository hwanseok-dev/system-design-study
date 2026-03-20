package io.lucky.security.api

import io.lucky.security.application.OrderService
import io.lucky.security.domain.order.Order
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/orders")
class OrderController(
    private val orderService: OrderService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateOrderRequest,
    ): OrderResponse {
        val order =
            orderService.create(
                userId = request.userId,
                stockCode = request.stockCode,
                orderType = request.orderType,
                side = request.side,
                quantity = request.quantity,
                price = request.price,
            )
        return order.toResponse()
    }

    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: Long,
    ): OrderResponse = orderService.findById(id).toResponse()

    @GetMapping
    fun findByUserId(
        @RequestParam userId: Long,
    ): List<OrderResponse> = orderService.findByUserId(userId).map { it.toResponse() }

    private fun Order.toResponse() =
        OrderResponse(
            id = id,
            userId = userId,
            stockCode = stockCode,
            orderType = orderType.name,
            side = side.name,
            status = status.name,
            quantity = quantity,
            filledQuantity = filledQuantity,
            price = price,
            avgFilledPrice = avgFilledPrice,
            lockedAmount = lockedAmount,
        )
}
