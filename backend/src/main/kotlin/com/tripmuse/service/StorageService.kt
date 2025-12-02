package com.tripmuse.service

import com.tripmuse.config.StorageConfig
import com.tripmuse.exception.StorageException
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO
import jakarta.annotation.PostConstruct

@Service
@EnableConfigurationProperties(StorageConfig::class)
class StorageService(
    private val storageConfig: StorageConfig
) {
    private val logger = LoggerFactory.getLogger(StorageService::class.java)
    private lateinit var basePath: Path

    companion object {
        const val THUMBNAIL_WIDTH = 300
        const val THUMBNAIL_HEIGHT = 300
    }

    @PostConstruct
    fun init() {
        basePath = Paths.get(storageConfig.getBasePath())
        try {
            Files.createDirectories(basePath)
            Files.createDirectories(basePath.resolve("images"))
            Files.createDirectories(basePath.resolve("videos"))
            Files.createDirectories(basePath.resolve("thumbnails"))
        } catch (e: IOException) {
            throw StorageException("Could not initialize storage: ${e.message}")
        }
    }

    fun store(file: MultipartFile, subDirectory: String): String {
        if (file.isEmpty) {
            throw StorageException("Failed to store empty file")
        }

        val originalFilename = file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast(".", "")
        val filename = "${UUID.randomUUID()}.$extension"

        val targetDir = basePath.resolve(subDirectory)
        Files.createDirectories(targetDir)

        val targetPath = targetDir.resolve(filename)

        try {
            file.inputStream.use { inputStream ->
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            throw StorageException("Failed to store file: ${e.message}")
        }

        return "$subDirectory/$filename"
    }

    fun storeImage(file: MultipartFile): String {
        return store(file, "images")
    }

    fun storeVideo(file: MultipartFile): String {
        return store(file, "videos")
    }

    fun storeThumbnail(file: MultipartFile): String {
        return store(file, "thumbnails")
    }

    fun delete(filePath: String) {
        try {
            val path = basePath.resolve(filePath)
            Files.deleteIfExists(path)
        } catch (e: IOException) {
            throw StorageException("Failed to delete file: ${e.message}")
        }
    }

    fun getFullPath(relativePath: String): Path {
        return basePath.resolve(relativePath)
    }

    fun getFileBytes(relativePath: String): ByteArray {
        val path = basePath.resolve(relativePath)
        return try {
            Files.readAllBytes(path)
        } catch (e: IOException) {
            throw StorageException("Failed to read file: ${e.message}")
        }
    }

    fun generateImageThumbnail(sourceFilePath: String): String? {
        val sourcePath = basePath.resolve(sourceFilePath)
        if (!Files.exists(sourcePath)) {
            logger.warn("Source file does not exist: $sourceFilePath")
            return null
        }

        return try {
            val originalImage = ImageIO.read(sourcePath.toFile()) ?: run {
                logger.warn("Could not read image: $sourceFilePath")
                return null
            }

            val thumbnail = createThumbnail(originalImage, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)

            val extension = sourceFilePath.substringAfterLast(".", "jpg")
            val thumbnailFilename = "${UUID.randomUUID()}.${extension}"
            val thumbnailPath = basePath.resolve("thumbnails").resolve(thumbnailFilename)

            Files.createDirectories(thumbnailPath.parent)

            val formatName = when (extension.lowercase()) {
                "png" -> "png"
                "gif" -> "gif"
                else -> "jpg"
            }
            ImageIO.write(thumbnail, formatName, thumbnailPath.toFile())

            "thumbnails/$thumbnailFilename"
        } catch (e: Exception) {
            logger.error("Failed to generate thumbnail for: $sourceFilePath", e)
            null
        }
    }

    private fun createThumbnail(original: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
        val originalWidth = original.width
        val originalHeight = original.height

        var targetWidth = maxWidth
        var targetHeight = maxHeight

        val widthRatio = maxWidth.toDouble() / originalWidth
        val heightRatio = maxHeight.toDouble() / originalHeight
        val ratio = minOf(widthRatio, heightRatio)

        targetWidth = (originalWidth * ratio).toInt()
        targetHeight = (originalHeight * ratio).toInt()

        val scaledImage = original.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)

        val thumbnail = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = thumbnail.createGraphics()
        graphics.drawImage(scaledImage, 0, 0, null)
        graphics.dispose()

        return thumbnail
    }
}
