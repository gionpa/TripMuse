# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TripMuse - 여행 사진/동영상 관리 앱. 여행지별 앨범 관리, GPS/날짜 메타데이터 기반 스마트 추천, 메모 및 댓글 기능 제공.

## Build & Development Commands

### Backend (Spring Boot + Kotlin)
```bash
# Local PostgreSQL 실행 (Podman/Docker)
cd backend/docker && podman-compose up -d

# 백엔드 실행
cd backend && ./gradlew bootRun

# 테스트
cd backend && ./gradlew test

# 빌드
cd backend && ./gradlew build
```

### Android (Kotlin + Jetpack Compose)
```bash
# Android Studio에서 열기 또는
cd android && ./gradlew assembleDebug

# 테스트
cd android && ./gradlew test
```

## Architecture

### Backend
```
backend/src/main/kotlin/com/tripmuse/
├── config/          # Spring 설정 (JPA, Web, Storage)
├── controller/      # REST API 엔드포인트
├── service/         # 비즈니스 로직
├── repository/      # JPA Repository
├── domain/          # Entity 클래스
├── dto/             # Request/Response DTO
└── exception/       # 예외 처리
```

- **환경 분리**: `local` (Podman PostgreSQL, 로컬 파일) / `prod` (Railway PostgreSQL, Volume)
- **인증**: 현재 `X-User-Id` 헤더로 임시 처리, 더미 유저 ID=1 사용
- **API Base**: `/api/v1/`

### Android
```
android/app/src/main/kotlin/com/tripmuse/
├── ui/
│   ├── navigation/  # NavGraph
│   ├── home/        # 앨범 목록
│   ├── album/       # 앨범 상세/생성
│   ├── media/       # 미디어 상세
│   ├── gallery/     # 기기 갤러리
│   ├── recommendation/  # 스마트 추천
│   └── profile/     # 프로필
├── data/
│   ├── api/         # Retrofit API
│   ├── repository/  # Repository 패턴
│   └── model/       # 데이터 모델
└── di/              # Hilt DI
```

- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **네트워크**: Retrofit + OkHttp
- **이미지**: Coil

## Key APIs

| Endpoint | Description |
|----------|-------------|
| GET/POST `/albums` | 앨범 목록/생성 |
| GET/PUT/DELETE `/albums/{id}` | 앨범 상세/수정/삭제 |
| GET/POST `/albums/{id}/media` | 미디어 목록/업로드 |
| GET/DELETE `/media/{id}` | 미디어 상세/삭제 |
| PUT `/media/{id}/memo` | 메모 수정 |
| GET/POST `/media/{id}/comments` | 댓글 목록/작성 |
| POST `/recommendations/analyze` | 스마트 추천 분석 |

## Database Schema

주요 테이블: `users`, `albums`, `media`, `memos`, `comments`

## Tech Stack

- **Backend**: Kotlin 1.9, Spring Boot 3.2, JDK 21, PostgreSQL
- **Android**: Kotlin 1.9, minSdk 26, Jetpack Compose, Hilt

## Development Notes

- 미디어 파일은 `/media/files/**` 경로로 정적 제공
- Android에서 서버 이미지 로드 시 `fileUrl` 필드 사용 (전체 URL)
- 더미 유저: `test@tripmuse.com` (ID=1), `friend@tripmuse.com` (ID=2)
