package com.tripmuse.controller

import com.tripmuse.dto.request.CreateAlbumRequest
import com.tripmuse.dto.request.ReorderAlbumsRequest
import com.tripmuse.dto.request.UpdateAlbumRequest
import com.tripmuse.dto.response.AlbumDetailResponse
import com.tripmuse.dto.response.AlbumListResponse
import com.tripmuse.dto.response.AlbumResponse
import com.tripmuse.service.AlbumService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/albums")
class AlbumController(
    private val albumService: AlbumService
) {
    @GetMapping
    fun getAlbums(
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<AlbumListResponse> {
        val albums = albumService.getAlbumsByUser(userId)
        return ResponseEntity.ok(albums)
    }

    @PostMapping
    fun createAlbum(
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: CreateAlbumRequest
    ): ResponseEntity<AlbumResponse> {
        val album = albumService.createAlbum(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(album)
    }

    @GetMapping("/{albumId}")
    fun getAlbumDetail(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable albumId: Long
    ): ResponseEntity<AlbumDetailResponse> {
        val album = albumService.getAlbumDetail(albumId, userId)
        return ResponseEntity.ok(album)
    }

    @PutMapping("/{albumId}")
    fun updateAlbum(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable albumId: Long,
        @Valid @RequestBody request: UpdateAlbumRequest
    ): ResponseEntity<AlbumResponse> {
        val album = albumService.updateAlbum(albumId, userId, request)
        return ResponseEntity.ok(album)
    }

    @DeleteMapping("/{albumId}")
    fun deleteAlbum(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable albumId: Long
    ): ResponseEntity<Void> {
        albumService.deleteAlbum(albumId, userId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/reorder")
    fun reorderAlbums(
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: ReorderAlbumsRequest
    ): ResponseEntity<Void> {
        albumService.reorderAlbums(userId, request.albumIds)
        return ResponseEntity.noContent().build()
    }
}
