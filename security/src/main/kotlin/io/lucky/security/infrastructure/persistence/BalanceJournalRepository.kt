package io.lucky.security.infrastructure.persistence

import io.lucky.security.domain.balance.BalanceJournal
import org.springframework.data.jpa.repository.JpaRepository

interface BalanceJournalRepository : JpaRepository<BalanceJournal, Long>
