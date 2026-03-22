package io.lucky.security.infrastructure.redis

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig {
    @Bean
    fun redissonClient(
        @Value("\${spring.data.redis.host}") host: String,
        @Value("\${spring.data.redis.port}") port: Int,
    ): RedissonClient {
        val config = Config()
        config.useSingleServer().address = "redis://$host:$port"
        return Redisson.create(config)
    }
}
