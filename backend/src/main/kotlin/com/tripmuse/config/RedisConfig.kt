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

@Configuration
class RedisConfig(
    private val redisProperties: RedisProperties
) {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val standaloneConfig = RedisStandaloneConfiguration().apply {
            hostName = redisProperties.host
            port = redisProperties.port
            if (!redisProperties.password.isNullOrBlank()) {
                setPassword(redisProperties.password)
            }
        }

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
}
