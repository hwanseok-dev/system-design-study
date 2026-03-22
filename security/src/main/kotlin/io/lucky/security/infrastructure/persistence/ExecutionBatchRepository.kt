package io.lucky.security.infrastructure.persistence

import io.lucky.security.application.ExecutionResultPayload
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Repository
class ExecutionBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun batchInsert(executions: List<ExecutionResultPayload>) {
        val sql =
            """
            INSERT INTO execution (order_id, user_id, stock_code, side, status,
                                   quantity, price, amount, exchange_exec_id,
                                   executed_at, settlement_date, created_at)
            VALUES (?, ?, ?, ?, 'APPLIED', ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (exchange_exec_id) DO NOTHING
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
            ps.setString(4, exec.side)
            ps.setInt(5, exec.quantity)
            ps.setBigDecimal(6, exec.price)
            ps.setBigDecimal(7, amount)
            ps.setString(8, exec.exchangeExecId)
            ps.setTimestamp(9, Timestamp.from(exec.executedAt))
            ps.setDate(10, Date.valueOf(settlementDate))
        }
    }
}
