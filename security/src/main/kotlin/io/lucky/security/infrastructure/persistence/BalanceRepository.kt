package io.lucky.security.infrastructure.persistence

import io.lucky.security.domain.balance.Balance
import org.springframework.data.jpa.repository.JpaRepository

interface BalanceRepository : JpaRepository<Balance, Long> {
    fun findByUserId(userId: Long): Balance?
}
