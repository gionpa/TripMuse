package com.tripmuse.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage")
data class StorageConfig(
    val type: String = "local",
    val local: LocalStorageConfig = LocalStorageConfig(),
    val volume: VolumeStorageConfig = VolumeStorageConfig()
) {
    data class LocalStorageConfig(
        val path: String = ""
    )

    data class VolumeStorageConfig(
        val path: String = "/data/media"
    )

    fun getBasePath(): String {
        return when (type) {
            "local" -> local.path
            "volume" -> volume.path
            else -> local.path
        }
    }
}
