package com.tripmuse.data.api

import com.tripmuse.BuildConfig

object ApiModule {
    // Base URL for media files (server URL without /api/v1/ suffix)
    val BASE_URL: String = BuildConfig.BASE_URL.removeSuffix("api/v1/").removeSuffix("/")
}
