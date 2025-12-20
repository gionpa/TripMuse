# TripMuse 구현 기록

## 프로젝트 개요
- **앱 이름**: TripMuse (여행 사진/동영상 관리 앱)
- **백엔드**: Spring Boot (Kotlin) + PostgreSQL
- **프론트엔드**: Android (Kotlin + Jetpack Compose)
- **배포**: Railway (백엔드 + PostgreSQL)

---

## 주요 구현 기능

### 1. 앨범 순서 드래그 변경 및 DB 저장

#### 구현 내용
- 사용자가 홈 화면에서 앨범 카드를 길게 눌러 드래그하면 순서 변경
- 변경된 순서를 서버 DB에 저장하여 앱 재시작 후에도 유지

#### 백엔드 변경사항
**Album Entity** (`backend/src/main/kotlin/com/tripmuse/domain/Album.kt`)
```kotlin
@Column(name = "display_order", nullable = false)
var displayOrder: Int = 0
```

**AlbumRepository** (`backend/src/main/kotlin/com/tripmuse/repository/AlbumRepository.kt`)
```kotlin
fun findByUserIdOrderByDisplayOrderAsc(userId: Long): List<Album>
fun findMaxDisplayOrderByUserId(userId: Long): Int
fun findByUserIdAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(userId: Long, displayOrder: Int): List<Album>
```

**AlbumService** (`backend/src/main/kotlin/com/tripmuse/service/AlbumService.kt`)
```kotlin
fun reorderAlbums(userId: Long, albumIds: List<Long>) {
    albumIds.forEachIndexed { index, albumId ->
        val album = albumRepository.findByUserIdAndId(userId, albumId)
        album?.let {
            it.displayOrder = index
            albumRepository.save(it)
        }
    }
}
```

**AlbumController** (`backend/src/main/kotlin/com/tripmuse/controller/AlbumController.kt`)
```kotlin
@PutMapping("/reorder")
fun reorderAlbums(
    @RequestHeader("X-User-Id") userId: Long,
    @RequestBody request: ReorderAlbumsRequest
): ResponseEntity<Unit>
```

#### Android 변경사항
**TripMuseApi** (`android/.../data/api/TripMuseApi.kt`)
```kotlin
@PUT("albums/reorder")
suspend fun reorderAlbums(
    @Header("X-User-Id") userId: Long,
    @Body request: ReorderAlbumsRequest
): Response<Unit>
```

**HomeViewModel** (`android/.../ui/home/HomeViewModel.kt`)
```kotlin
fun moveAlbum(fromIndex: Int, toIndex: Int) {
    // UI 즉시 업데이트
    // 서버에 새 순서 저장
    viewModelScope.launch {
        albumRepository.reorderAlbums(albumIds)
    }
}
```

---

### 2. 비디오 썸네일 첫 프레임 추출

#### 구현 내용
- 기존: 1초 지점에서 썸네일 추출 → 짧은 영상에서 검은 화면 문제
- 변경: 0.001초 (첫 프레임)에서 썸네일 추출

#### 변경사항
**StorageService** (`backend/src/main/kotlin/com/tripmuse/service/StorageService.kt`)
```kotlin
val process = ProcessBuilder(
    "ffmpeg",
    "-i", sourcePath.toAbsolutePath().toString(),
    "-ss", "00:00:00.001",  // 첫 프레임 추출
    "-vframes", "1",
    "-vf", "scale=$THUMBNAIL_WIDTH:$THUMBNAIL_HEIGHT:force_original_aspect_ratio=decrease",
    "-y",
    thumbnailPath.toAbsolutePath().toString()
)
```

---

### 3. Railway 배포 설정

#### nixpacks.toml
```toml
[phases.setup]
nixPkgs = ["ffmpeg"]

[phases.build]
cmds = ["./gradlew build -x test"]

[start]
cmd = "java -jar build/libs/tripmuse-backend-0.0.1-SNAPSHOT.jar"
```

#### application.yml (prod 프로필)
```yaml
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}
    username: ${PGUSER}
    password: ${PGPASSWORD}

storage:
  type: volume
  volume:
    path: /data/media

server:
  port: ${PORT:8080}
```

---

## 트러블슈팅 기록

### 1. 앨범 순서 변경 시 500 에러

#### 증상
```
Failed to reorder albums: 500
{"code":"INTERNAL_ERROR","message":"An unexpected error occurred"}
```

#### 원인
- Android 앱이 Railway production 서버에 연결되어 있었음
- Railway DB에는 `display_order` 컬럼이 없어서 500 에러 발생
- Hibernate `ddl-auto: update`로 자동 마이그레이션이 기대되었으나, Railway 재배포 전이라 반영 안됨

#### 해결 방법
1. **로컬 개발 시**: 앱의 BASE_URL을 로컬 서버로 변경
   ```kotlin
   // build.gradle.kts
   debug {
       buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/api/v1/\"")
   }
   ```
   - `10.0.2.2`는 Android 에뮬레이터에서 호스트 머신의 localhost를 가리킴

2. **Railway DB 수동 마이그레이션** (필요시)
   ```sql
   ALTER TABLE albums ADD COLUMN IF NOT EXISTS display_order INTEGER DEFAULT 0 NOT NULL;
   ```

#### 디버깅 과정
```bash
# Android logcat으로 실제 요청 URL 확인
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -i "okhttp\|albums"

# 결과: Railway로 요청이 가고 있었음
# PUT https://tripmuse-production.up.railway.app/api/v1/albums/reorder -> 500
```

---

### 2. 로컬 DB display_order 컬럼 누락

#### 증상
```
ERROR: column "display_order" of relation "albums" does not exist
```

#### 원인
- Hibernate `ddl-auto: update`가 새 컬럼을 자동 생성해야 하나, 기존 테이블에서 제대로 반영 안됨

#### 해결
```bash
podman exec -i tripmuse-postgres psql -U tripmuse -d tripmuse -c \
  "ALTER TABLE albums ADD COLUMN IF NOT EXISTS display_order INTEGER DEFAULT 0 NOT NULL;"
```

---

### 3. findByUserIdOrderByCreatedAtDesc 메서드 누락

#### 증상
```
Unresolved reference: findByUserIdOrderByCreatedAtDesc
```

#### 원인
- RecommendationService에서 사용하는 메서드가 AlbumRepository에 없었음

#### 해결
```kotlin
// AlbumRepository.kt에 추가
fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Album>
```

---

### 4. Railway 파일 서빙 500 에러

#### 증상
- 이미지/비디오 파일 요청 시 500 에러

#### 원인
- Railway의 ephemeral 파일 시스템 + Volume 마운트 경로 불일치

#### 해결
- `storage.type: volume` 설정 추가
- Railway Volume을 `/data/media`에 마운트
- StorageService에서 프로필별 경로 분기 처리

---

### 5. FFmpeg Railway에서 실행 불가

#### 증상
- 비디오 업로드 시 썸네일 생성 실패

#### 원인
- Railway 기본 이미지에 FFmpeg가 설치되어 있지 않음

#### 해결
`nixpacks.toml` 추가:
```toml
[phases.setup]
nixPkgs = ["ffmpeg"]
```

---

## 환경 설정

### Android BASE_URL 설정
```kotlin
// android/app/build.gradle.kts
buildTypes {
    debug {
        // 로컬 개발: buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/api/v1/\"")
        // Railway:
        buildConfigField("String", "BASE_URL", "\"https://tripmuse-production.up.railway.app/api/v1/\"")
    }
    release {
        buildConfigField("String", "BASE_URL", "\"https://tripmuse-production.up.railway.app/api/v1/\"")
    }
}
```

### 로컬 PostgreSQL (Podman)
```bash
# 컨테이너 시작
podman start tripmuse-postgres

# DB 접속
podman exec -it tripmuse-postgres psql -U tripmuse -d tripmuse

# 테이블 확인
\d albums
```

### 백엔드 로컬 실행
```bash
cd backend
./gradlew bootRun
# http://localhost:8080 에서 실행
```

### Android 빌드 및 설치
```bash
cd android
./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.tripmuse/.MainActivity
```

---

## 프로젝트 구조

```
TripMuse/
├── backend/
│   └── src/main/kotlin/com/tripmuse/
│       ├── controller/     # REST API 컨트롤러
│       ├── service/        # 비즈니스 로직
│       ├── repository/     # JPA 리포지토리
│       ├── domain/         # 엔티티 클래스
│       ├── dto/            # 요청/응답 DTO
│       └── config/         # 설정 클래스
├── android/
│   └── app/src/main/kotlin/com/tripmuse/
│       ├── ui/             # Compose UI (화면별)
│       ├── data/           # API, Repository, Model
│       └── di/             # Hilt 의존성 주입
├── nixpacks.toml           # Railway 빌드 설정
└── implementation.md       # 이 문서
```

---

### 6. 동영상 전체화면 재생 수정

#### 구현 내용
- 동영상 클릭 시 전체화면 진입 문제 해결
- 전체화면 전환 후 원래 화면으로 되돌아가는 문제 해결

#### 문제점
1. **터치 이벤트 소비 문제**: PlayerView가 터치 이벤트를 소비하여 부모 레이어로 전달되지 않음
2. **Activity 재생성 문제**: 화면 회전 시 Activity가 재생성되면서 Compose 상태(`showFullscreen`)가 초기화됨

#### 해결 방법

**MediaDetailScreen.kt** - PlayerView 터치 이벤트 전달
```kotlin
AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply {
            player = exoPlayer
            useController = false  // 미리보기에서는 컨트롤러 비활성화
            // 터치 이벤트를 소비하지 않고 부모로 전달
            setOnTouchListener { _, _ -> false }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    },
    modifier = Modifier.fillMaxSize()
)
```

**AndroidManifest.xml** - Activity 재생성 방지
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
    android:theme="@style/Theme.TripMuse">
```
- `configChanges` 속성으로 화면 회전 시 Activity 재생성 대신 `onConfigurationChanged()` 콜백 호출
- Compose 상태가 유지되어 전체화면 모드가 정상 작동

---

### 7. 네트워크 복원력 강화

#### 구현 내용
- OkHttp에 `retryOnConnectionFailure(true)` 설정 추가
- 네트워크 불안정 시 자동 재시도

#### 변경사항
**AppModule.kt** (`android/.../di/AppModule.kt`)
```kotlin
return OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor)
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)  // 연결 실패 시 자동 재시도
    .build()
```

---

### 8. 백엔드 예외 처리 개선

#### 구현 내용
- `NoResourceFoundException` 핸들러 추가
- 존재하지 않는 리소스 요청 시 INTERNAL_ERROR 대신 적절한 NOT_FOUND 응답

#### 변경사항
**GlobalExceptionHandler.kt** (`backend/.../exception/GlobalExceptionHandler.kt`)
```kotlin
@ExceptionHandler(NoResourceFoundException::class)
fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse("NOT_FOUND", "Resource not found: ${e.resourcePath}"))
}
```

---

## 트러블슈팅 기록

### 6. Android 에뮬레이터 DNS 문제

#### 증상
- 에뮬레이터에서 Railway 백엔드에 연결 실패
- 실제 스마트폰에서는 정상 작동
- 호스트 PC의 인터넷 연결은 정상

#### 원인
- Android 에뮬레이터의 DNS 설정이 호스트 PC의 DNS와 다르게 동작
- 일부 네트워크 환경에서 에뮬레이터 기본 DNS가 외부 도메인 해석 실패

#### 해결
에뮬레이터 실행 시 DNS 서버 직접 지정:
```bash
~/Library/Android/sdk/emulator/emulator -avd "Medium_Phone_API_36.0" -dns-server 8.8.8.8 -no-snapshot-load
```
- `-dns-server 8.8.8.8`: Google Public DNS 사용
- `-no-snapshot-load`: 이전 상태 스냅샷 무시하고 새로 시작

---

### 7. 동영상 전체화면 전환 후 원래 화면 복귀 문제

#### 증상
- 동영상에서 전체화면 버튼 클릭 시 잠깐 전환되다가 다시 원래 화면으로 돌아옴

#### 원인
- 전체화면 진입 시 `ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE`로 화면 회전
- Activity 재생성 발생 → Compose 상태 초기화 → `showFullscreen = false`로 리셋
- 결과적으로 전체화면 모드가 즉시 해제됨

#### 해결
**AndroidManifest.xml**에 `configChanges` 추가:
```xml
android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
```
- 화면 회전 시 Activity 재생성 방지
- Compose 상태(`showFullscreen`)가 유지됨

---

---

### 9. JWT 인증 시스템

#### 구현 내용
- 이메일/비밀번호 로그인 및 회원가입
- 네이버 소셜 로그인 (OAuth2)
- JWT Access Token / Refresh Token 관리
- 토큰 자동 갱신 (401 응답 시)

#### 백엔드 구조
**AuthController** (`backend/.../auth/AuthController.kt`)
```kotlin
@PostMapping("/login")
fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse>

@PostMapping("/signup")
fun signup(@RequestBody request: SignupRequest): ResponseEntity<AuthResponse>

@PostMapping("/naver")
fun loginWithNaver(@RequestBody request: NaverLoginRequest): ResponseEntity<AuthResponse>

@PostMapping("/refresh")
fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<AuthResponse>
```

**JwtTokenProvider** (`backend/.../security/JwtTokenProvider.kt`)
- Access Token: 1시간 유효
- Refresh Token: 7일 유효
- Redis에 Refresh Token 저장

#### Android 구조
**TokenManager** (`android/.../data/auth/TokenManager.kt`)
- DataStore를 사용한 토큰 영구 저장
- Flow 기반 비동기 토큰 접근

**TokenRefreshAuthenticator** (`android/.../data/auth/TokenRefreshAuthenticator.kt`)
- OkHttp Authenticator로 401 응답 시 자동 토큰 갱신
- 갱신 실패 시 로그아웃 처리

**AuthInterceptor** (`android/.../data/auth/AuthInterceptor.kt`)
- 모든 요청에 Authorization 헤더 자동 추가
- 403 응답 시 토큰 클리어 및 로그아웃

---

### 10. 네이버 소셜 로그인

#### 구현 내용
- 네이버 로그인 SDK 연동 (Android)
- 백엔드에서 네이버 API로 사용자 정보 조회
- 기존 사용자 연동 또는 신규 사용자 생성

#### Android 설정
**MainActivity.kt**
```kotlin
// Naver SDK 초기화
NaverIdLoginSDK.initialize(
    context = this,
    clientId = getString(R.string.naver_client_id),
    clientSecret = getString(R.string.naver_client_secret),
    clientName = getString(R.string.naver_client_name)
)

// 로그인 콜백
NaverIdLoginSDK.authenticate(this, oauthLoginCallback)
```

**strings.xml** (값은 네이버 개발자 센터에서 발급)
```xml
<string name="naver_client_id">YOUR_CLIENT_ID</string>
<string name="naver_client_secret">YOUR_CLIENT_SECRET</string>
<string name="naver_client_name">TripMuse</string>
```

#### 백엔드 설정
**NaverOAuthClient** (`backend/.../client/NaverOAuthClient.kt`)
```kotlin
fun fetchUserInfo(accessToken: String): NaverUserInfo? {
    return webClient.get()
        .uri("/v1/nid/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        .retrieve()
        .bodyToMono<NaverUserInfoResponse>()
        .timeout(Duration.ofSeconds(10))
        .block()
        ?.response
}
```

**AuthService.loginWithNaver()** - 네이버 로그인 처리
1. 네이버 API로 사용자 정보 조회
2. 이메일로 기존 사용자 검색
3. 없으면 새 사용자 생성 (Provider: NAVER)
4. JWT 토큰 발급

---

### 11. 친구 시스템

#### 구현 내용
- 닉네임/이메일로 사용자 검색
- 친구 요청 전송/수락/거절
- 친구 목록 조회
- 앨범 공유 (친구에게 앨범 멤버 추가)

#### API 엔드포인트
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/friends` | 친구 목록 조회 |
| GET | `/friends/search?query=` | 사용자 검색 |
| GET | `/friends/requests` | 받은 친구 요청 |
| POST | `/friends/request` | 친구 요청 전송 |
| POST | `/friends/accept/{id}` | 친구 요청 수락 |
| POST | `/friends/reject/{id}` | 친구 요청 거절 |

#### 데이터 모델
**Friendship Entity**
```kotlin
@Entity
data class Friendship(
    val userId: Long,
    val friendId: Long,
    val status: FriendshipStatus  // PENDING, ACCEPTED, REJECTED
)
```

---

### 12. 앨범 공유 기능

#### 구현 내용
- 앨범에 멤버 추가/제거
- 앨범 멤버 권한 관리 (OWNER, EDITOR, VIEWER)
- 공유된 앨범 목록 조회

#### API 엔드포인트
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/albums/{id}/members` | 앨범 멤버 목록 |
| POST | `/albums/{id}/members` | 멤버 추가 |
| DELETE | `/albums/{id}/members/{userId}` | 멤버 제거 |

#### 데이터 모델
**AlbumMember Entity**
```kotlin
@Entity
data class AlbumMember(
    val albumId: Long,
    val userId: Long,
    val role: AlbumRole  // OWNER, EDITOR, VIEWER
)
```

---

### 13. UI 테마 (라이트 모드 고정)

#### 구현 내용
- 시스템 다크모드 설정과 무관하게 항상 라이트 테마 사용
- 일관된 사용자 경험 제공

#### 변경사항
**Theme.kt** (`android/.../ui/theme/Theme.kt`)
```kotlin
@Composable
fun TripMuseTheme(
    darkTheme: Boolean = false,  // 항상 false
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme  // 항상 라이트 테마
    // ...
}
```

---

### 14. 네이버 로그인 안정성 개선

#### 구현 내용
- WebClient 타임아웃 설정 (10초)
- 상세 로깅 추가로 디버깅 용이성 향상
- 예외 처리 강화

#### 변경사항
**NaverOAuthClient.kt**
```kotlin
private val httpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(10))

fun fetchUserInfo(accessToken: String): NaverUserInfo? {
    log.info("Fetching Naver user info with token: ${accessToken.take(10)}...")

    return try {
        val response = webClient.get()
            .uri("/v1/nid/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .bodyToMono<NaverUserInfoResponse>()
            .timeout(Duration.ofSeconds(10))
            .block()
        // ... 로깅 및 검증
    } catch (e: WebClientResponseException) {
        log.error("Naver API HTTP error - status: ${e.statusCode}", e)
        null
    } catch (e: Exception) {
        log.error("Naver API error - ${e.javaClass.simpleName}: ${e.message}", e)
        null
    }
}
```

**AuthService.kt** - 상세 로깅 추가
```kotlin
fun loginWithNaver(request: NaverLoginRequest): AuthResponse {
    log.info("Naver login attempt with token: ${request.accessToken.take(10)}...")
    // ... 각 단계별 로깅
    log.info("User saved successfully - id: ${saved.id}, isNewUser: $isNewUser")
    log.info("Tokens issued successfully for user: ${saved.id}")
    return tokens
}
```

---

## 프로젝트 통계

### 코드량 (LOC)
| 영역 | 파일 타입 | LOC |
|------|----------|-----|
| Backend | Kotlin | 4,508 |
| Android | Kotlin | 7,625 |
| Android | XML | 105 |
| **총합** | - | **12,238** |

---

## Git 커밋 히스토리 (최근)

```
afd2aa9 fix: Improve Naver login stability with timeout and logging
xxxxxxx feat: Add friend system and album sharing
xxxxxxx feat: Add JWT authentication with Naver OAuth
e91f85b Fix video fullscreen playback issue
32ff854 Add album reorder API and use video first frame for thumbnails
e171d86 Fix file serving 500 error on Railway
19555f4 Add nixpacks config for FFmpeg on Railway
01b3d3b Add video thumbnail generation using FFmpeg
10bbc4d Add UI improvements: logo title, blue theme cards, drag reorder
```
