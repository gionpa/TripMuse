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
            // 비공개 앨범 원본이 섞여 나가므로 공유 캐시·CDN에 남지 않게 private으로 준다.
            // (앨범 권한을 검사하는 mediaId 기반 스트리밍 전환은 다음 단계)
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=604800")
            .header(HttpHeaders.ETAG, eTag)
            .lastModified(lastModified)
            .body(resource)
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
