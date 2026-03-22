package io.lucky.bff.repository

import io.lucky.bff.domain.OrderReadModel
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class OrderReadModelJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun upsert(model: OrderReadModel) {
        jdbcTemplate.update(
            """
            INSERT INTO order_read_model
                (order_id, user_id, stock_code, stock_name, side, order_type, status,
                 quantity, filled_quantity, price, avg_filled_price,
                 locked_amount, event_version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (order_id) DO UPDATE SET
                status = EXCLUDED.status,
                filled_quantity = EXCLUDED.filled_quantity,
                avg_filled_price = EXCLUDED.avg_filled_price,
                locked_amount = EXCLUDED.locked_amount,
                event_version = EXCLUDED.event_version,
                updated_at = EXCLUDED.updated_at
            WHERE order_read_model.event_version < EXCLUDED.event_version
            """,
            model.orderId,
            model.userId,
            model.stockCode,
            model.stockName,
            model.side,
            model.orderType,
            model.status,
            model.quantity,
            model.filledQuantity,
            model.price,
            model.avgFilledPrice,
            model.lockedAmount,
            model.eventVersion,
            Timestamp.from(model.createdAt),
            Timestamp.from(model.updatedAt),
        )
    }
}
