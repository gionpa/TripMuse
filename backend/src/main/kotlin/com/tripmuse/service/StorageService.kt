package com.tripmuse.service

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.tripmuse.config.StorageConfig
import com.tripmuse.exception.StorageException
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.Image
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.imageio.ImageIO
import javax.imageio.IIOImage
import javax.imageio.ImageWriteParam
import jakarta.annotation.PostConstruct
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

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
        const val COMPRESSED_MAX_WIDTH = 2048
        const val COMPRESSED_MAX_HEIGHT = 2048
        const val JPEG_QUALITY = 0.85f  // 85% 품질 (육안으로 차이 거의 없음)
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
        val extension = if (originalFilename.contains(".")) {
            originalFilename.substringAfterLast(".")
        } else {
            "" // No extension for generic store
        }
        val filename = if (extension.isNotEmpty()) "${UUID.randomUUID()}.$extension" else "${UUID.randomUUID()}"

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
        if (file.isEmpty) {
            throw StorageException("Failed to store empty file")
        }

        val originalFilename = file.originalFilename ?: "unknown"
        val extension = if (originalFilename.contains(".")) {
            originalFilename.substringAfterLast(".")
        } else {
            "jpg" // Default extension for images
        }
        val filename = "${UUID.randomUUID()}.$extension"

        val targetDir = basePath.resolve("images")
        Files.createDirectories(targetDir)
        val targetPath = targetDir.resolve(filename)

        try {
            val fileBytes = file.bytes

            // Read EXIF orientation
            val orientation = getExifOrientation(fileBytes)

            // Read image and apply rotation based on EXIF orientation
            val originalImage = ImageIO.read(ByteArrayInputStream(fileBytes))
                ?: throw StorageException("Could not read image file")

            val correctedImage = applyExifOrientation(originalImage, orientation)

            // Save the corrected image
            val formatName = when (extension.lowercase()) {
                "png" -> "png"
                "gif" -> "gif"
                else -> "jpg"
            }
            ImageIO.write(correctedImage, formatName, targetPath.toFile())

            logger.info("Image stored with orientation correction: $filename (orientation: $orientation)")
        } catch (e: IOException) {
            throw StorageException("Failed to store image: ${e.message}")
        }

        return "images/$filename"
    }

    fun storeImageFromBytes(fileBytes: ByteArray, originalFilename: String?): String {
        if (fileBytes.isEmpty()) {
            throw StorageException("Failed to store empty file")
        }

        val filename = originalFilename ?: "unknown"
        val extension = if (filename.contains(".")) {
            filename.substringAfterLast(".")
        } else {
            "jpg" // Default extension when filename has no extension
        }
        val storedFilename = "${UUID.randomUUID()}.$extension"

        val targetDir = basePath.resolve("images")
        Files.createDirectories(targetDir)
        val targetPath = targetDir.resolve(storedFilename)

        try {
            // Try to read and process image
            val originalImage = ImageIO.read(ByteArrayInputStream(fileBytes))

            if (originalImage != null) {
                // Read EXIF orientation
                val orientation = getExifOrientation(fileBytes)

                val correctedImage = applyExifOrientation(originalImage, orientation)

                // Save the corrected image
                val formatName = when (extension.lowercase()) {
                    "png" -> "png"
                    "gif" -> "gif"
                    else -> "jpg"
                }
                ImageIO.write(correctedImage, formatName, targetPath.toFile())

                logger.info("Image stored with orientation correction: $storedFilename (orientation: $orientation)")
            } else {
                // Fallback: save raw bytes if ImageIO cannot read the format
                Files.write(targetPath, fileBytes)
                logger.info("Image stored without processing (unsupported format): $storedFilename")
            }
        } catch (e: Exception) {
            // Fallback: save raw bytes on any error
            try {
                Files.write(targetPath, fileBytes)
                logger.warn("Image stored without processing due to error: $storedFilename - ${e.message}")
            } catch (writeError: IOException) {
                throw StorageException("Failed to store image: ${writeError.message}")
            }
        }

        return "images/$storedFilename"
    }

    /**
     * 이미지를 압축하여 저장 (최대 2048px, JPEG 85% 품질)
     * @return 압축 후 파일 크기 (bytes)
     */
    fun storeImageBytesAtPath(fileBytes: ByteArray, relativePath: String): Long {
        if (fileBytes.isEmpty()) {
            throw StorageException("Failed to store empty file")
        }

        val targetPath = basePath.resolve(relativePath)
        Files.createDirectories(targetPath.parent)

        val extension = if (relativePath.contains(".")) {
            relativePath.substringAfterLast(".")
        } else {
            "jpg"
        }

        try {
            val originalImage = ImageIO.read(ByteArrayInputStream(fileBytes))

            if (originalImage != null) {
                val orientation = getExifOrientation(fileBytes)
                val correctedImage = applyExifOrientation(originalImage, orientation)

                // 리사이즈 및 압축 적용
                val resizedImage = resizeIfNeeded(correctedImage, COMPRESSED_MAX_WIDTH, COMPRESSED_MAX_HEIGHT)

                val formatName = when (extension.lowercase()) {
                    "png" -> "png"
                    "gif" -> "gif"
                    else -> "jpg"
                }

                // JPEG인 경우 품질 조절하여 저장
                if (formatName == "jpg") {
                    writeJpegWithQuality(resizedImage, targetPath, JPEG_QUALITY)
                } else {
                    ImageIO.write(resizedImage, formatName, targetPath.toFile())
                }

                val savedSize = Files.size(targetPath)
                val originalSize = fileBytes.size
                val compressionRatio = if (originalSize > 0) ((1 - savedSize.toDouble() / originalSize) * 100).toInt() else 0
                logger.info("Image compressed and stored: $relativePath (${originalSize/1024}KB -> ${savedSize/1024}KB, $compressionRatio% 절감)")

                return savedSize
            } else {
                Files.write(
                    targetPath,
                    fileBytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
                logger.info("Image stored without processing at path: $relativePath (unsupported format)")
                return fileBytes.size.toLong()
            }
        } catch (e: Exception) {
            try {
                Files.write(
                    targetPath,
                    fileBytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
                logger.warn("Image stored without processing at path: $relativePath due to error: ${e.message}")
                return fileBytes.size.toLong()
            } catch (writeError: IOException) {
                throw StorageException("Failed to store image: ${writeError.message}")
            }
        }
    }

    /**
     * 이미지가 최대 크기를 초과하면 리사이즈
     */
    private fun resizeIfNeeded(image: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
        val width = image.width
        val height = image.height

        // 이미 최대 크기 이하인 경우 그대로 반환
        if (width <= maxWidth && height <= maxHeight) {
            return image
        }

        val widthRatio = maxWidth.toDouble() / width
        val heightRatio = maxHeight.toDouble() / height
        val ratio = minOf(widthRatio, heightRatio)

        val targetWidth = (width * ratio).toInt()
        val targetHeight = (height * ratio).toInt()

        val scaledImage = image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)

        val imageType = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val resizedImage = BufferedImage(targetWidth, targetHeight, imageType)
        val graphics = resizedImage.createGraphics()
        if (!image.colorModel.hasAlpha()) {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, targetWidth, targetHeight)
        }
        graphics.drawImage(scaledImage, 0, 0, null)
        graphics.dispose()

        logger.info("Image resized: ${width}x${height} -> ${targetWidth}x${targetHeight}")
        return resizedImage
    }

    /**
     * JPEG 품질을 지정하여 저장
     */
    private fun writeJpegWithQuality(image: BufferedImage, targetPath: Path, quality: Float) {
        val writers = ImageIO.getImageWritersByFormatName("jpg")
        if (!writers.hasNext()) {
            // fallback
            ImageIO.write(image, "jpg", targetPath.toFile())
            return
        }

        val writer = writers.next()
        val param = writer.defaultWriteParam
        param.compressionMode = ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = quality

        Files.newOutputStream(targetPath).use { os ->
            val output = ImageIO.createImageOutputStream(os)
            writer.output = output
            writer.write(null, IIOImage(image, null, null), param)
            writer.dispose()
            output.close()
        }
    }

    /**
     * 기존 이미지 파일을 압축하여 덮어쓰기 (마이그레이션용)
     * @return 압축 후 파일 크기 (bytes)
     */
    fun compressImageInPlace(relativePath: String, fileBytes: ByteArray): Long {
        val targetPath = basePath.resolve(relativePath)

        val extension = if (relativePath.contains(".")) {
            relativePath.substringAfterLast(".")
        } else {
            "jpg"
        }

        try {
            val originalImage = ImageIO.read(ByteArrayInputStream(fileBytes))

            if (originalImage != null) {
                val orientation = getExifOrientation(fileBytes)
                val correctedImage = applyExifOrientation(originalImage, orientation)

                // 리사이즈 및 압축 적용
                val resizedImage = resizeIfNeeded(correctedImage, COMPRESSED_MAX_WIDTH, COMPRESSED_MAX_HEIGHT)

                val formatName = when (extension.lowercase()) {
                    "png" -> "png"
                    "gif" -> "gif"
                    else -> "jpg"
                }

                // JPEG인 경우 품질 조절하여 저장
                if (formatName == "jpg") {
                    writeJpegWithQuality(resizedImage, targetPath, JPEG_QUALITY)
                } else {
                    ImageIO.write(resizedImage, formatName, targetPath.toFile())
                }

                return Files.size(targetPath)
            }
        } catch (e: Exception) {
            logger.warn("Failed to compress image in place: $relativePath - ${e.message}")
        }

        return fileBytes.size.toLong()
    }

    fun storeVideo(file: MultipartFile): String {
        return store(file, "videos")
    }

    fun storeThumbnail(file: MultipartFile): String {
        return store(file, "thumbnails")
    }

    fun storeBytesAt(relativePath: String, data: ByteArray) {
        val targetPath = basePath.resolve(relativePath)
        try {
            Files.createDirectories(targetPath.parent)
            Files.write(
                targetPath,
                data,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (e: IOException) {
            throw StorageException("Failed to store file: ${e.message}")
        }
    }

    fun storeMultipartFileAtPath(file: MultipartFile, relativePath: String) {
        val targetPath = basePath.resolve(relativePath)
        try {
            Files.createDirectories(targetPath.parent)
            file.inputStream.use { input ->
                Files.copy(
                    input,
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: IOException) {
            throw StorageException("Failed to store multipart file: ${e.message}")
        }
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

            val extension = if (sourceFilePath.contains(".")) {
                sourceFilePath.substringAfterLast(".")
            } else {
                "jpg" // Default extension for thumbnails
            }
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

    fun generateVideoThumbnail(sourceFilePath: String): String? {
        val sourcePath = basePath.resolve(sourceFilePath)
        if (!Files.exists(sourcePath)) {
            logger.warn("Source video file does not exist: $sourceFilePath")
            return null
        }

        return try {
            val thumbnailFilename = "${UUID.randomUUID()}.jpg"
            val thumbnailPath = basePath.resolve("thumbnails").resolve(thumbnailFilename)
            Files.createDirectories(thumbnailPath.parent)

            // Use ffmpeg to extract the first frame from the video
            val process = ProcessBuilder(
                "ffmpeg",
                "-i", sourcePath.toAbsolutePath().toString(),
                "-ss", "00:00:00.001",  // Seek to first frame (0.001s to avoid potential issues)
                "-vframes", "1",     // Extract 1 frame
                "-vf", "scale=$THUMBNAIL_WIDTH:$THUMBNAIL_HEIGHT:force_original_aspect_ratio=decrease",
                "-y",                // Overwrite output
                thumbnailPath.toAbsolutePath().toString()
            )
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                logger.warn("FFmpeg timed out for video: $sourceFilePath")
                return null
            }

            if (process.exitValue() != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                logger.warn("FFmpeg failed for video: $sourceFilePath, exit code: ${process.exitValue()}, output: $errorOutput")
                return null
            }

            if (Files.exists(thumbnailPath) && Files.size(thumbnailPath) > 0) {
                logger.info("Video thumbnail generated: $thumbnailFilename")
                "thumbnails/$thumbnailFilename"
            } else {
                logger.warn("FFmpeg did not create thumbnail for: $sourceFilePath")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to generate video thumbnail for: $sourceFilePath", e)
            null
        }
    }

    private fun createThumbnail(original: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
        val originalWidth = original.width
        val originalHeight = original.height

        val widthRatio = maxWidth.toDouble() / originalWidth
        val heightRatio = maxHeight.toDouble() / originalHeight
        val ratio = minOf(widthRatio, heightRatio)

        val targetWidth = (originalWidth * ratio).toInt()
        val targetHeight = (originalHeight * ratio).toInt()

        val scaledImage = original.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)

        val thumbnail = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = thumbnail.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, targetWidth, targetHeight)
        graphics.drawImage(scaledImage, 0, 0, null)
        graphics.dispose()

        return thumbnail
    }

    private fun getExifOrientation(imageBytes: ByteArray): Int {
        return try {
            val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(imageBytes))
            val exifIFD0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            exifIFD0?.getInt(ExifIFD0Directory.TAG_ORIENTATION) ?: 1
        } catch (e: Exception) {
            logger.debug("Could not read EXIF orientation: ${e.message}")
            1 // Default: no rotation needed
        }
    }

    private fun applyExifOrientation(image: BufferedImage, orientation: Int): BufferedImage {
        val width = image.width
        val height = image.height

        val transform = AffineTransform()

        when (orientation) {
            1 -> return image // Normal - no transformation needed
            2 -> { // Flip horizontal
                transform.scale(-1.0, 1.0)
                transform.translate(-width.toDouble(), 0.0)
            }
            3 -> { // Rotate 180
                transform.translate(width.toDouble(), height.toDouble())
                transform.rotate(Math.PI)
            }
            4 -> { // Flip vertical
                transform.scale(1.0, -1.0)
                transform.translate(0.0, -height.toDouble())
            }
            5 -> { // Transpose (flip horizontal + rotate 270)
                transform.rotate(Math.PI / 2)
                transform.scale(1.0, -1.0)
            }
            6 -> { // Rotate 90 CW
                transform.translate(height.toDouble(), 0.0)
                transform.rotate(Math.PI / 2)
            }
            7 -> { // Transverse (flip horizontal + rotate 90)
                transform.scale(-1.0, 1.0)
                transform.translate(-height.toDouble(), 0.0)
                transform.translate(0.0, width.toDouble())
                transform.rotate(3 * Math.PI / 2)
            }
            8 -> { // Rotate 270 CW (90 CCW)
                transform.translate(0.0, width.toDouble())
                transform.rotate(3 * Math.PI / 2)
            }
            else -> return image
        }

        // Determine new dimensions after rotation
        val newWidth = if (orientation in listOf(5, 6, 7, 8)) height else width
        val newHeight = if (orientation in listOf(5, 6, 7, 8)) width else height

        // Use ARGB to preserve alpha channel (for PNG images)
        val imageType = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val rotatedImage = BufferedImage(newWidth, newHeight, imageType)
        val graphics = rotatedImage.createGraphics()
        if (!image.colorModel.hasAlpha()) {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, newWidth, newHeight)
        }
        graphics.drawImage(image, transform, null)
        graphics.dispose()

        return rotatedImage
    }
}
