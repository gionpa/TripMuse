package com.tripmuse.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
import java.time.Duration
import java.net.URI

@Configuration
class RedisConfig(
    private val redisProperties: RedisProperties
) {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val urlConfig = ParsedRedisUrl.from(redisProperties.url)
        val standaloneConfig = buildStandaloneConfig(redisProperties, urlConfig)

        val clientConfigBuilder = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(5))
            .shutdownTimeout(Duration.ZERO)
        if (redisProperties.ssl.isEnabled || urlConfig?.useSsl == true) {
            clientConfigBuilder.useSsl()
        }

        return LettuceConnectionFactory(standaloneConfig, clientConfigBuilder.build())
    }

    @Bean
    fun cacheConfiguration(objectMapper: ObjectMapper): RedisCacheConfiguration {
        val mapper = objectMapper.copy().registerKotlinModule()
        val ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(Any::class.java)
            .build()
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY)
        val serializer = GenericJackson2JsonRedisSerializer(mapper)

        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeValuesWith(SerializationPair.fromSerializer(serializer))
    }

    @Bean
    fun cacheManagerBuilderCustomizer(
        cacheConfiguration: RedisCacheConfiguration
    ): RedisCacheManagerBuilderCustomizer {
        return RedisCacheManagerBuilderCustomizer { builder ->
            builder
                .cacheDefaults(cacheConfiguration)
                .withCacheConfiguration(
                    "albumMedia",
                    cacheConfiguration.entryTtl(Duration.ofMinutes(3))
                )
        }
    }

    private fun buildStandaloneConfig(
        redisProperties: RedisProperties,
        urlConfig: ParsedRedisUrl?
    ): RedisStandaloneConfiguration {
        urlConfig?.let { parsed ->
            return RedisStandaloneConfiguration().apply {
                hostName = parsed.host
                port = parsed.port
                parsed.password?.let { setPassword(it) }
            }
        }

        val host = redisProperties.host
        val port = if (redisProperties.port > 0) redisProperties.port else 6379

        require(host.isNotBlank()) { "Redis host is empty. Set REDIS_URL or REDIS_HOST." }

        return RedisStandaloneConfiguration().apply {
            hostName = host
            this.port = port
            if (!redisProperties.username.isNullOrBlank()) {
                username = redisProperties.username
            }
            if (!redisProperties.password.isNullOrBlank()) {
                setPassword(redisProperties.password)
            }
        }
    }

    private data class ParsedRedisUrl(
        val host: String,
        val port: Int,
        val password: String?,
        val useSsl: Boolean
    ) {
        companion object {
            fun from(url: String?): ParsedRedisUrl? {
                if (url.isNullOrBlank()) return null
                val uri = URI(url)
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 6379
                val password = uri.userInfo
                    ?.split(":", limit = 2)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { parts ->
                        when {
                            parts.size == 2 -> parts[1]
                            parts.size == 1 -> parts[0]
                            else -> null
                        }
                    }
                val useSsl = uri.scheme.equals("rediss", ignoreCase = true)
                return ParsedRedisUrl(host, port, password, useSsl)
            }
        }
    }
}
