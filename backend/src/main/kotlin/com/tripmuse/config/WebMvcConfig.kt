package com.tripmuse.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(StorageConfig::class)
class WebMvcConfig(
    private val storageConfig: StorageConfig
) : WebMvcConfigurer {
    // Static resource handler removed - using FileController for better error handling
}
