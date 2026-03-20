package io.lucky.security.infrastructure.persistence

import io.lucky.security.domain.settlement.Settlement
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementRepository : JpaRepository<Settlement, Long>
