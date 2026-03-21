package io.lucky.security.domain

object LogAction {
    const val CREATE_ORDER = "createOrder"
    const val PUBLISH_OUTBOX = "publishOutbox"
    const val ORDER_VALIDATED = "orderValidated"
    const val ORDER_REJECTED = "orderRejected"
    const val SUBMIT_ORDER = "submitOrder"
    const val CONSUME_VALIDATED = "consumeValidated"
    const val CONSUME_VALIDATED_FAILED = "consumeValidatedFailed"
    const val CONSUME_REJECTED = "consumeRejected"
    const val CONSUME_REJECTED_FAILED = "consumeRejectedFailed"
    const val SIMULATE_VALIDATED = "simulateValidated"
    const val SIMULATE_REJECTED = "simulateRejected"
}
