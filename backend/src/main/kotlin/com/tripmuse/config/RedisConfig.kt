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
        val standaloneConfig = buildStandaloneConfig(redisProperties)

        val clientConfigBuilder = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(5))
            .shutdownTimeout(Duration.ZERO)
        if (redisProperties.ssl.isEnabled) {
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

    private fun buildStandaloneConfig(redisProperties: RedisProperties): RedisStandaloneConfiguration {
        val url = redisProperties.url
        if (!url.isNullOrBlank()) {
            val uri = URI(url)
            val config = RedisStandaloneConfiguration().apply {
                hostName = uri.host
                port = if (uri.port > 0) uri.port else 6379
                uri.userInfo?.split(":", limit = 2)?.let { parts ->
                    if (parts.size == 2) {
                        // Railway는 기본 사용자로 인증, username 지정 없이 패스워드만 설정
                        setPassword(parts[1])
                    } else if (parts.size == 1) {
                        setPassword(parts[0])
                    }
                }
            }
            return config
        }

        return RedisStandaloneConfiguration().apply {
            hostName = redisProperties.host
            port = redisProperties.port
            if (!redisProperties.password.isNullOrBlank()) {
                setPassword(redisProperties.password)
            }
        }
    }
}
