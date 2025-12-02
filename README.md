# TripMuse

여행 사진/동영상 관리 앱 - 소중한 여행의 추억을 앨범으로 정리하고 관리하세요.

## 주요 기능

- **앨범 관리**: 여행별로 앨범을 생성하고 사진/동영상을 정리
- **미디어 업로드**: 사진 및 동영상 업로드, 자동 썸네일 생성
- **대표 이미지 설정**: 앨범의 대표 이미지를 지정하여 앨범 카드에 표시
- **메모 및 댓글**: 각 미디어에 메모와 댓글 작성 가능
- **날짜 관리**: 여행 시작일/종료일 DatePicker로 편리하게 입력

## 기술 스택

### Backend
- **Framework**: Spring Boot 3.2.5
- **Language**: Kotlin
- **Database**: PostgreSQL
- **ORM**: Spring Data JPA (Hibernate)

### Android
- **UI**: Jetpack Compose + Material3
- **DI**: Hilt
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Coil
- **Architecture**: MVVM

## 프로젝트 구조

```
TripMuse/
├── backend/                    # Spring Boot 백엔드
│   ├── src/main/kotlin/com/tripmuse/
│   │   ├── config/            # 설정 클래스
│   │   ├── controller/        # REST API 컨트롤러
│   │   ├── domain/            # 엔티티
│   │   ├── dto/               # 요청/응답 DTO
│   │   ├── repository/        # JPA 리포지토리
│   │   └── service/           # 비즈니스 로직
│   └── docker/                # Docker 설정
│
└── android/                   # Android 앱
    └── app/src/main/kotlin/com/tripmuse/
        ├── data/              # 데이터 레이어
        │   ├── api/           # Retrofit API 인터페이스
        │   ├── model/         # 데이터 모델
        │   └── repository/    # 리포지토리
        ├── di/                # Hilt 모듈
        └── ui/                # UI 레이어
            ├── album/         # 앨범 상세/생성/수정
            ├── components/    # 공통 컴포넌트
            ├── gallery/       # 갤러리 화면
            ├── home/          # 홈 화면
            ├── navigation/    # 네비게이션
            └── theme/         # 테마
```

## 실행 방법

### 1. PostgreSQL 실행
```bash
cd backend/docker
podman-compose up -d
```

### 2. Backend 실행
```bash
cd backend
./gradlew bootRun
```

### 3. Android 앱 빌드 및 설치
```bash
cd android
./gradlew assembleDebug

# 에뮬레이터에 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 에뮬레이터에서 localhost 접근을 위한 포트 포워딩
adb reverse tcp:8080 tcp:8080
```

## API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/albums` | 앨범 목록 조회 |
| POST | `/api/v1/albums` | 앨범 생성 |
| GET | `/api/v1/albums/{id}` | 앨범 상세 조회 |
| PUT | `/api/v1/albums/{id}` | 앨범 수정 |
| DELETE | `/api/v1/albums/{id}` | 앨범 삭제 |
| GET | `/api/v1/albums/{id}/media` | 앨범 내 미디어 목록 |
| POST | `/api/v1/albums/{id}/media` | 미디어 업로드 |
| POST | `/api/v1/media/{id}/cover` | 대표 이미지 설정 |
| GET | `/api/v1/media/{id}` | 미디어 상세 조회 |
| DELETE | `/api/v1/media/{id}` | 미디어 삭제 |

## 환경 설정

### Backend (application-local.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/tripmuse
    username: tripmuse
    password: tripmuse
```

### Android (local.properties)
```properties
BASE_URL=http://10.0.2.2:8080/api/v1/
```

## 라이선스

MIT License
