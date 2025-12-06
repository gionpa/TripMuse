package com.tripmuse.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RedisClientConfig {

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory::class)
    fun redisConnectionFactory(
        @Value("\${spring.data.redis.host:localhost}") host: String,
        @Value("\${spring.data.redis.port:6379}") port: Int,
        @Value("\${spring.data.redis.password:}") password: String?
    ): RedisConnectionFactory {
        val config = RedisStandaloneConfiguration(host, port)
        if (!password.isNullOrBlank()) {
            config.setPassword(password)
        }
        return LettuceConnectionFactory(config)
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate::class)
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(connectionFactory)
}

