package com.tripmuse.controller

import com.tripmuse.dto.response.ShareResolveResponse
import com.tripmuse.security.CustomUserDetails
import com.tripmuse.service.AlbumService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ShareController(
    private val albumService: AlbumService,
    @Value("\${tripmuse.public-base-url}") private val publicBaseUrl: String
) {
    /**
     * 공유 토큰 리졸브 (앱 딥링크 진입 시 호출) — 열람 권한 부여 후 앨범 ID 반환
     */
    @GetMapping("/api/v1/share/{token}")
    fun resolveShareLink(
        @AuthenticationPrincipal user: CustomUserDetails,
        @PathVariable token: String
    ): ResponseEntity<ShareResolveResponse> {
        return ResponseEntity.ok(albumService.resolveShareLink(token, user.id))
    }

    /**
     * 공유 링크 랜딩 페이지 (인증 불필요).
     * - 카카오톡 등 메신저 크롤러에 OG 메타태그 제공 (앨범 제목/커버 미리보기)
     * - 앱 설치 시 App Links로 앱이 바로 열리고, 이 페이지는 미설치/미검증 시에만 보인다
     */
    @GetMapping("/share/{token}", produces = [MediaType.TEXT_HTML_VALUE])
    fun shareLanding(@PathVariable token: String): String {
        val album = albumService.findAlbumByShareToken(token)
            ?: return landingHtml(
                title = "유효하지 않은 링크",
                description = "공유가 해제되었거나 존재하지 않는 앨범입니다.",
                imageUrl = null,
                token = null
            )

        val coverUrl = album.coverImageUrl?.let { if (it.startsWith("/")) "$publicBaseUrl$it" else it }
        return landingHtml(
            title = escapeHtml(album.title),
            description = "TripMuse에서 여행 앨범을 확인해보세요",
            imageUrl = coverUrl,
            token = token
        )
    }

    /**
     * Android App Links 검증용 (https 링크 탭 시 브라우저 없이 앱이 바로 열리도록)
     */
    @GetMapping("/.well-known/assetlinks.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun assetLinks(): String = """
        [{
          "relation": ["delegate_permission/common.handle_all_urls"],
          "target": {
            "namespace": "android_app",
            "package_name": "com.tripmuse",
            "sha256_cert_fingerprints": [
              "04:2A:F3:85:A2:0F:57:17:D1:18:3E:59:05:09:BE:D6:3E:CF:BB:E0:43:DE:3C:13:22:C7:61:0D:AD:D4:26:98",
              "56:18:1B:E7:D1:65:2E:A7:50:F4:7F:18:23:0C:20:C7:4D:7D:3E:17:8D:09:AF:DB:34:B7:61:FF:4D:EB:C7:A5"
            ]
          }
        }]
    """.trimIndent()

    private fun landingHtml(title: String, description: String, imageUrl: String?, token: String?): String {
        val ogImage = imageUrl?.let { """<meta property="og:image" content="${escapeHtml(it)}" />""" } ?: ""
        val openAppUrl = token?.let {
            "intent://share/$it#Intent;scheme=tripmuse;package=com.tripmuse;end"
        }
        val openAppButton = openAppUrl?.let {
            """<a class="btn" href="$it">TripMuse 앱에서 열기</a>"""
        } ?: ""
        val autoOpen = openAppUrl?.let {
            """<script>setTimeout(function(){ window.location.href = "$it"; }, 300);</script>"""
        } ?: ""

        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>$title - TripMuse</title>
                <meta property="og:title" content="$title" />
                <meta property="og:description" content="$description" />
                $ogImage
                <meta property="og:type" content="website" />
                <style>
                    body { margin:0; font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;
                           display:flex; align-items:center; justify-content:center; min-height:100vh;
                           background:#f5f7ff; color:#1f2937; text-align:center; }
                    .card { padding:40px 28px; }
                    h1 { font-size:22px; margin:0 0 8px; }
                    p { color:#6b7280; margin:0 0 24px; }
                    .btn { display:inline-block; background:#5B7FFF; color:#fff; text-decoration:none;
                           padding:14px 28px; border-radius:12px; font-weight:600; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>$title</h1>
                    <p>$description</p>
                    $openAppButton
                </div>
                $autoOpen
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
