package io.lucky.security.domain.balance

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "balance_journal")
class BalanceJournal(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "journal_type", nullable = false)
    val journalType: JournalType,
    @Column(name = "reference_type", nullable = false)
    val referenceType: String,
    @Column(name = "reference_id", nullable = false)
    val referenceId: Long,
    @Column(name = "stock_code")
    val stockCode: String? = null,
    @Column(name = "cash_delta", nullable = false)
    val cashDelta: BigDecimal = BigDecimal.ZERO,
    @Column(name = "quantity_delta", nullable = false)
    val quantityDelta: Int = 0,
    val description: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
