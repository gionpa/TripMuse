# TripMuse 로그인 및 권한 관리 구현 계획

## 1. 개요
- **목표**: 자체 이메일 로그인과 네이버 아이디로 로그인(네아로)을 통합 지원하고, JWT 기반의 인증 시스템을 구축하여 보안성을 강화한다.
- **주요 변경점**:
  - `X-User-Id` 헤더 제거 → `Authorization: Bearer <Token>` 전환
  - Spring Security 도입
  - 사용자 프로필(닉네임, 이미지)과 소셜 계정 연동

---

## 2. 도메인 및 DB 설계

### 2.1 User Entity (`users` 테이블) 확장
기존 `email`, `nickname`, `profileImageUrl`에 다음 필드 추가:

| 필드명 | 타입 | 설명 | 제약조건 |
|---|---|---|---|
| `password` | String | BCrypt 해시된 비밀번호 | Nullable (소셜 로그인 시 null) |
| `role` | Enum | 사용자 권한 (`USER`, `ADMIN`) | Not Null (Default: USER) |
| `provider` | Enum | 로그인 제공자 (`LOCAL`, `NAVER`) | Not Null |
| `provider_id` | String | 소셜 서비스의 고유 식별자 | Nullable |

### 2.2 Refresh Token 관리
- **저장소**: Redis (기존 인프라 활용)
- **구조**: Key-Value (Key: `rt:{email}`, Value: `token_value`, TTL: 14일)

---

## 3. 백엔드 구현 (Spring Boot)

### 3.1 의존성 추가
- `spring-boot-starter-security`
- `io.jsonwebtoken:jjwt` (JWT 생성/검증)
- `spring-boot-starter-webflux` (네이버 API 호출용 WebClient)

### 3.2 Security 설정
- **SecurityConfig**:
  - CSRF 비활성화 (Rest API)
  - CORS 허용
  - Session Creation Policy: `STATELESS`
  - PasswordEncoder: `BCryptPasswordEncoder`
- **JwtAuthenticationFilter**:
  - 헤더에서 Bearer 토큰 추출 및 검증
  - 유효한 토큰일 경우 `SecurityContext`에 `Authentication` 객체 저장

### 3.3 Auth API (`AuthController`)
| 메서드 | 경로 | 설명 | 비고 |
|---|---|---|---|
| POST | `/api/v1/auth/signup` | 자체 회원가입 | 이메일, 비번, 닉네임 |
| POST | `/api/v1/auth/login` | 자체 로그인 | JWT (Access/Refresh) 발급 |
| POST | `/api/v1/auth/naver` | 네이버 로그인 | 클라이언트가 보낸 네이버 AccessToken으로 검증 및 JWT 발급 |
| POST | `/api/v1/auth/refresh` | 토큰 갱신 | Refresh Token 검증 후 Access Token 재발급 |

### 3.4 프로필 및 계정 연동 전략
- **네이버 로그인 시나리오**:
  1. 클라이언트에서 네이버 SDK 로그인 → 백엔드로 AccessToken 전송
  2. 백엔드가 네이버 API (`/nid/me`) 호출하여 사용자 정보(이메일, 닉네임, 프사) 획득
  3. **DB 조회**:
     - **신규 유저**: `provider=NAVER`로 회원가입. 네이버 닉네임/프사 저장.
     - **기존 유저(이메일 일치)**: 로그인 성공 처리. (프로필 정보는 기존 정보 유지 또는 비어있을 경우만 업데이트)
- **프로필 수정**:
  - 소셜 유저도 앱 내에서 닉네임/프사 변경 가능 (네이버 정보와 독립적 관리)

### 3.5 리팩토링 (가장 중요)
- **`@CurrentUser` 어노테이션 도입**: `X-User-Id` 헤더 의존성 제거
- 컨트롤러 전역 수정:
  ```kotlin
  // 변경 전
  fun doSomething(@RequestHeader("X-User-Id") userId: Long)
  
  // 변경 후
  fun doSomething(@CurrentUser user: User) // 또는 userDetails
  ```

---

## 4. Android 구현 (Kotlin/Compose)

### 4.1 의존성
- **Naver NidOAuth SDK** 추가

### 4.2 로그인 UI/UX
- **스플래시 화면 (`SplashScreen`)**:
  - 앱 실행 시 가장 먼저 표시
  - **자동 로그인 체크**:
    1. 저장된 토큰(Access/Refresh) 유무 확인
    2. 토큰이 있다면 백엔드 유효성 검사 (또는 Refresh 시도)
    3. 유효하다면 `HomeScreen`(앨범 리스트)으로 즉시 이동
    4. 유효하지 않다면 `LoginScreen`으로 이동
- **로그인 화면 (`LoginScreen`)**:
  - 이메일/비번 입력 폼 (자동 로그인 체크박스 포함 - 기본값 true)
  - "네이버로 시작하기" 버튼
- 회원가입 화면 (`SignupScreen`)

### 4.3 인증 로직
- **TokenManager**: DataStore에 Access/Refresh Token 암호화 저장
- **자동 로그인 처리**:
  - **자체 로그인**: 로그인 성공 시 발급받은 JWT를 DataStore에 저장. 재실행 시 스플래시에서 확인.
  - **네이버 로그인**:
    1. 네이버 SDK 토큰 존재 확인
    2. 백엔드 `/api/v1/auth/naver` 호출하여 서비스 JWT 발급
    3. 서비스 JWT 저장 후 자동 로그인 처리 완료
- **네이버 로그인 정보 동기화**:
  - 백엔드는 네이버 로그인 요청마다 프로필(닉네임, 이미지) 정보를 확인하여 `users` 테이블 최신화 (옵션: 사용자가 수동 변경한 경우 제외)

### 4.4 화면 전환 플로우
- `Splash` → (토큰 유효) → `Home`
- `Splash` → (토큰 없음/만료) → `Login`
- `Login` → (로그인 성공) → `Home`
- `Home` (로그아웃) → `Login`

---

## 5. 실행 계획 (Phase)

### Phase 1: 백엔드 인프라 & DB
1. Gradle 의존성 추가
2. User 엔티티 수정 및 DB 마이그레이션
3. Security Config 및 JWT Provider 구현

### Phase 2: 인증 API 개발
1. AuthService 구현 (회원가입, 로그인, 네이버 검증)
2. AuthController 구현

### Phase 3: 백엔드 리팩토링
1. `@CurrentUser` 구현
2. 모든 Controller에서 `X-User-Id` 제거 및 SecurityContext 연동

### Phase 4: Android 클라이언트
1. 로그인 UI 구현
2. 네이버 SDK 연동
3. Interceptor 및 토큰 관리 구현

