package com.tripmuse.controller

import com.tripmuse.dto.request.CreateAlbumRequest
import com.tripmuse.dto.request.ReorderAlbumsRequest
import com.tripmuse.dto.request.UpdateAlbumRequest
import com.tripmuse.dto.response.AlbumDetailResponse
import com.tripmuse.dto.response.AlbumListResponse
import com.tripmuse.dto.response.AlbumResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.AlbumService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/albums")
class AlbumController(
    private val albumService: AlbumService
) {
    @GetMapping
    fun getAlbums(
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<AlbumListResponse> {
        val albums = albumService.getAlbumsByUser(user.id)
        return ResponseEntity.ok(albums)
    }

    @PostMapping
    fun createAlbum(
        @AuthenticationPrincipal user: CustomUserDetails,
        @Valid @RequestBody request: CreateAlbumRequest
    ): ResponseEntity<AlbumResponse> {
        val album = albumService.createAlbum(user.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(album)
    }

    @GetMapping("/{albumId}")
    fun getAlbumDetail(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable albumId: Long
    ): ResponseEntity<AlbumDetailResponse> {
        val album = albumService.getAlbumDetail(albumId, user.id)
        return ResponseEntity.ok(album)
    }

    @PutMapping("/{albumId}")
    fun updateAlbum(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable albumId: Long,
        @Valid @RequestBody request: UpdateAlbumRequest
    ): ResponseEntity<AlbumResponse> {
        val album = albumService.updateAlbum(albumId, user.id, request)
        return ResponseEntity.ok(album)
    }

    @DeleteMapping("/{albumId}")
    fun deleteAlbum(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable albumId: Long
    ): ResponseEntity<Void> {
        albumService.deleteAlbum(albumId, user.id)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/reorder")
    fun reorderAlbums(
        @AuthenticationPrincipal user: CustomUserDetails,
        @Valid @RequestBody request: ReorderAlbumsRequest
    ): ResponseEntity<Void> {
        albumService.reorderAlbums(user.id, request.albumIds)
        return ResponseEntity.noContent().build()
    }
}
