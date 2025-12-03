package com.tripmuse.controller

import com.tripmuse.config.StorageConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/media/files")
@EnableConfigurationProperties(StorageConfig::class)
class FileController(
    private val storageConfig: StorageConfig
) {
    private val logger = LoggerFactory.getLogger(FileController::class.java)

    @GetMapping("/**")
    fun serveFile(request: HttpServletRequest): ResponseEntity<Resource> {
        val basePath = storageConfig.getBasePath()
        val requestPath = request.requestURI.removePrefix("/media/files/")

        logger.info("File request: $requestPath, basePath: $basePath")

        if (requestPath.contains("..")) {
            logger.warn("Path traversal attempt detected: $requestPath")
            return ResponseEntity.badRequest().build()
        }

        val filePath: Path = Paths.get(basePath, requestPath)

        if (!Files.exists(filePath)) {
            logger.warn("File not found: $filePath")
            return ResponseEntity.notFound().build()
        }

        if (!Files.isRegularFile(filePath)) {
            logger.warn("Not a regular file: $filePath")
            return ResponseEntity.notFound().build()
        }

        val resource = FileSystemResource(filePath)
        val contentType = determineContentType(requestPath)
        val lastModified = Files.getLastModifiedTime(filePath).toMillis()
        val eTag = "\"${filePath.fileName}-${lastModified}\""

        logger.info("Serving file: $filePath, contentType: $contentType")

        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800, immutable")
            .header(HttpHeaders.ETAG, eTag)
            .lastModified(lastModified)
            .body(resource)
    }

    @GetMapping("/debug/storage-info")
    fun getStorageInfo(): ResponseEntity<Map<String, Any>> {
        val basePath = storageConfig.getBasePath()
        val baseDir = Paths.get(basePath)

        val info = mutableMapOf<String, Any>(
            "basePath" to basePath,
            "storageType" to storageConfig.type,
            "basePathExists" to Files.exists(baseDir),
            "basePathIsDirectory" to Files.isDirectory(baseDir)
        )

        if (Files.exists(baseDir) && Files.isDirectory(baseDir)) {
            try {
                val subDirs = listOf("images", "thumbnails", "videos")
                val dirInfo = mutableMapOf<String, Any>()

                for (subDir in subDirs) {
                    val subPath = baseDir.resolve(subDir)
                    if (Files.exists(subPath) && Files.isDirectory(subPath)) {
                        val fileCount = Files.list(subPath).count()
                        dirInfo[subDir] = mapOf(
                            "exists" to true,
                            "fileCount" to fileCount
                        )
                    } else {
                        dirInfo[subDir] = mapOf("exists" to false)
                    }
                }
                info["directories"] = dirInfo
            } catch (e: Exception) {
                info["error"] = e.message ?: "Unknown error"
            }
        }

        return ResponseEntity.ok(info)
    }

    private fun determineContentType(filePath: String): MediaType {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> MediaType.IMAGE_JPEG
            "png" -> MediaType.IMAGE_PNG
            "gif" -> MediaType.IMAGE_GIF
            "webp" -> MediaType.parseMediaType("image/webp")
            "mp4" -> MediaType.parseMediaType("video/mp4")
            "mov" -> MediaType.parseMediaType("video/quicktime")
            "avi" -> MediaType.parseMediaType("video/x-msvideo")
            "webm" -> MediaType.parseMediaType("video/webm")
            else -> MediaType.APPLICATION_OCTET_STREAM
        }
    }
}
