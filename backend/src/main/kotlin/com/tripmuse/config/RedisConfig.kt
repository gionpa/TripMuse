package com.tripmuse.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
@ConditionalOnProperty(name = ["spring.cache.type"], havingValue = "redis")
class RedisConfig(
    @Value("\${spring.data.redis.url:}") private val redisUrl: String
) {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val urlConfig = ParsedRedisUrl.from(redisUrl)
            ?: throw IllegalArgumentException("REDIS_URL is required when CACHE_TYPE=redis")

        val standaloneConfig = RedisStandaloneConfiguration().apply {
            hostName = urlConfig.host
            port = urlConfig.port
            urlConfig.password?.let { setPassword(it) }
        }

        val clientConfigBuilder = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(5))
            .shutdownTimeout(Duration.ZERO)
        if (urlConfig.useSsl) {
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
                    cacheConfiguration.entryTtl(Duration.ofMinutes(10))
                )
                .withCacheConfiguration(
                    "mediaDetail",
                    cacheConfiguration.entryTtl(Duration.ofMinutes(10))
                )
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
