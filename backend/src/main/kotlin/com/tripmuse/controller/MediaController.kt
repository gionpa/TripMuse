package com.tripmuse.controller

import com.tripmuse.domain.MediaType
import com.tripmuse.dto.response.MediaDetailResponse
import com.tripmuse.dto.response.MediaListResponse
import com.tripmuse.dto.response.MediaResponse
import com.tripmuse.service.MediaService
import com.tripmuse.service.StorageService
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType as SpringMediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1")
class MediaController(
    private val mediaService: MediaService,
    private val storageService: StorageService
) {
    @GetMapping("/albums/{albumId}/media")
    fun getMediaByAlbum(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable albumId: Long,
        @RequestParam(required = false) type: MediaType?
    ): ResponseEntity<MediaListResponse> {
        val mediaList = mediaService.getMediaByAlbum(albumId, userId, type)
        return ResponseEntity.ok(mediaList)
    }

    @PostMapping("/albums/{albumId}/media")
    fun uploadMedia(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable albumId: Long,
        @RequestParam("file") file: MultipartFile,
        @RequestParam("latitude", required = false) latitude: Double?,
        @RequestParam("longitude", required = false) longitude: Double?,
        @RequestParam("takenAt", required = false) takenAt: String?
    ): ResponseEntity<MediaResponse> {
        val media = mediaService.uploadMedia(albumId, userId, file, latitude, longitude, takenAt)
        return ResponseEntity.status(HttpStatus.CREATED).body(media)
    }

    @GetMapping("/media/{mediaId}")
    fun getMediaDetail(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long
    ): ResponseEntity<MediaDetailResponse> {
        val media = mediaService.getMediaDetail(mediaId, userId)
        return ResponseEntity.ok(media)
    }

    @DeleteMapping("/media/{mediaId}")
    fun deleteMedia(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long
    ): ResponseEntity<Void> {
        mediaService.deleteMedia(mediaId, userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/media/{mediaId}/cover")
    fun setCoverImage(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long
    ): ResponseEntity<MediaResponse> {
        val media = mediaService.setCoverImage(mediaId, userId)
        return ResponseEntity.ok(media)
    }

    @GetMapping("/media/{mediaId}/file")
    fun getMediaFile(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable mediaId: Long
    ): ResponseEntity<Resource> {
        val media = mediaService.getMediaDetail(mediaId, userId)
        val fileBytes = storageService.getFileBytes(media.filePath)
        val resource = ByteArrayResource(fileBytes)

        val contentType = when {
            media.filePath.endsWith(".jpg") || media.filePath.endsWith(".jpeg") -> SpringMediaType.IMAGE_JPEG
            media.filePath.endsWith(".png") -> SpringMediaType.IMAGE_PNG
            media.filePath.endsWith(".gif") -> SpringMediaType.IMAGE_GIF
            media.filePath.endsWith(".mp4") -> SpringMediaType.parseMediaType("video/mp4")
            media.filePath.endsWith(".mov") -> SpringMediaType.parseMediaType("video/quicktime")
            else -> SpringMediaType.APPLICATION_OCTET_STREAM
        }

        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${media.originalFilename}\"")
            .body(resource)
    }
}
