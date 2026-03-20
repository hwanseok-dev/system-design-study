package io.lucky.orchestrator.infrastructure.redis

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.script.DefaultRedisScript

@Configuration
class RedisScriptConfig {
    companion object {
        const val FAIL_KEY_TTL_SECONDS = 86400L
    }

    @Bean
    fun checkAndIncrementScript(): DefaultRedisScript<Long> =
        DefaultRedisScript<Long>().apply {
            setLocation(ClassPathResource("scripts/check_and_increment.lua"))
            resultType = Long::class.java
        }

    @Bean
    fun checkAndFailScript(): DefaultRedisScript<Long> =
        DefaultRedisScript<Long>().apply {
            setLocation(ClassPathResource("scripts/check_and_fail.lua"))
            resultType = Long::class.java
        }
}
