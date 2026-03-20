package io.lucky.security.domain.balance

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "balance")
class Balance(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,
    @Column(name = "cash_amount", nullable = false)
    var cashAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "locked_amount", nullable = false)
    var lockedAmount: BigDecimal = BigDecimal.ZERO,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    val version: Long = 0,
) {
    /**
     * Available cash = total cash minus locked (frozen for pending orders).
     * cashAmount holds the total, lockedAmount is a subset.
     */
    fun availableCash(): BigDecimal = cashAmount - lockedAmount

    fun lockCash(amount: BigDecimal) {
        check(availableCash() >= amount) { "Insufficient cash: available=${availableCash()}, required=$amount" }
        lockedAmount += amount
    }

    fun unlockCash(amount: BigDecimal) {
        check(lockedAmount >= amount) { "Invalid unlock: locked=$lockedAmount, unlock=$amount" }
        lockedAmount -= amount
    }

    /**
     * Confirm a buy execution: the locked cash is actually spent.
     * Deduct from both cashAmount (total) and lockedAmount (frozen).
     */
    fun confirmBuyExecution(amount: BigDecimal) {
        check(lockedAmount >= amount) { "Invalid confirm: locked=$lockedAmount, confirm=$amount" }
        cashAmount -= amount
        lockedAmount -= amount
    }

    fun addCash(amount: BigDecimal) {
        cashAmount += amount
    }
}
