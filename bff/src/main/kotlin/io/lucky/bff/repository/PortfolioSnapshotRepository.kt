package io.lucky.bff.repository

import io.lucky.bff.domain.PortfolioSnapshot
import io.lucky.bff.domain.PortfolioSnapshotId
import org.springframework.data.jpa.repository.JpaRepository

interface PortfolioSnapshotRepository : JpaRepository<PortfolioSnapshot, PortfolioSnapshotId> {
    fun findByUserId(userId: Long): List<PortfolioSnapshot>
}
