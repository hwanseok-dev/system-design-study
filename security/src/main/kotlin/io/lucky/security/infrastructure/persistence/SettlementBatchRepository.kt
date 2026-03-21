package io.lucky.security.infrastructure.persistence

import io.lucky.security.application.ExecutionResultPayload
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Repository
class SettlementBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun batchInsert(
        executions: List<ExecutionResultPayload>,
        side: String,
    ) {
        val sql =
            """
            INSERT INTO settlement (execution_id, order_id, user_id, stock_code, side, status,
                                    quantity, amount, settlement_date, created_at)
            SELECT e.id, ?, ?, ?, ?, 'PENDING', ?, ?, ?, now()
            FROM execution e WHERE e.exchange_exec_id = ?
            ON CONFLICT (execution_id) DO NOTHING
            """.trimIndent()

        jdbcTemplate.batchUpdate(sql, executions, 500) { ps, exec ->
            val amount = exec.price * exec.quantity.toBigDecimal()
            val settlementDate =
                exec.executedAt
                    .plus(2, ChronoUnit.DAYS)
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .toLocalDate()

            ps.setLong(1, exec.orderId)
            ps.setLong(2, exec.userId)
            ps.setString(3, exec.stockCode)
            ps.setString(4, side)
            ps.setInt(5, exec.quantity)
            ps.setBigDecimal(6, amount)
            ps.setDate(7, Date.valueOf(settlementDate))
            ps.setString(8, exec.exchangeExecId)
        }
    }
}
