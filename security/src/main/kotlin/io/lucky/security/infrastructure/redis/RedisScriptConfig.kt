package io.lucky.security.infrastructure.redis

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.script.DefaultRedisScript

@Configuration
class RedisScriptConfig {
    companion object {
        const val DEDUP_TTL_SECONDS = 86400L
        const val CANCEL_KEY_TTL_SECONDS = 86400L
    }

    @Bean
    fun checkDedupAndIncrementFillScript(): DefaultRedisScript<Long> =
        DefaultRedisScript<Long>().apply {
            setLocation(ClassPathResource("scripts/check_dedup_and_increment_fill.lua"))
            setResultType(Long::class.java)
        }

    @Bean
    fun cancelOrderScript(): DefaultRedisScript<Long> =
        DefaultRedisScript<Long>().apply {
            setLocation(ClassPathResource("scripts/cancel_order.lua"))
            setResultType(Long::class.java)
        }
}
