## 작업 요약

- **백엔드**
  - 동영상 업로드 시 멀티파트를 바로 파일로 스트리밍(`storeMultipartFileAtPath`)하여 메모리 사용을 줄이고 OOM을 방지. 이미지만 바이트 배열로 읽어 `MediaUploadAsyncService`로 전달.
  - `MediaUploadAsyncService`가 이미 저장된 파일을 건너뛸 수 있도록 `alreadyStored` 플래그와 파일 경로를 처리. 업로드 상태/메타데이터 흐름 정리.
  - `application.yml` 기본 프로파일을 `prod`로 설정하고 업로드 `file-size-threshold`를 `0B`로 내려 스트리밍 업로드를 활성화. Dockerfile에 `MaxRAMPercentage=75`로 JVM 메모리 제한 추가.

- **Android (네트워크/구성)**
  - `BASE_URL`(debug)을 Railway 프로덕션 도메인으로 변경해 앱이 기본적으로 프로덕션 BE를 바라보도록 수정.
  - Retrofit/Repo에서 URL 상대 경로를 절대 경로로 보정(`withFullUrls`) 유지.

- **Android (업로드/리스트/상세)**
  - 업로드 성공 시 앨범 리스트를 즉시 갱신하도록 `AlbumViewModel`과 NavGraph 플로우를 수정(업로드 성공 후 폴링/refresh 호출).
  - 동영상 업로드 시 썸네일 없을 경우 리스트에 플레이스홀더 아이콘을 표시하고, 업로드 진행/실패 상태를 명확히 오버레이.
  - `CancellationException`을 네트워크 에러로 오인하지 않도록 Album/Media Repository에서 재던지기로 처리(탭 전환 시 “StandAloneCoroutine was cancelled” 회피).

- **Android (UI/UX)**
  - `MediaDetailScreen`에서 위도/경도를 도시/국가명으로 역지오코딩하여 표시(Geocoder, API33 비동기 대응, 실패 시 좌표 fallback).
  - 앨범 카드 타이틀/주소/날짜/카운트의 폰트 크기를 키우고 짙은 색으로 변경해 시인성 개선.
  - 앨범/미디어 썸네일 로딩에 Coil `ImageRequest`를 사용해 크기 제한(앨범 720px, 미디어 512px), 캐시/크로스페이드 활성화로 썸네일 우선·지연 로딩.

- **빌드/배포 관련**
  - GRADLE_USER_HOME을 프로젝트 내부로 지정해 Gradle 8.6 다운로드 및 `:app:installDebug`로 에뮬레이터에 재설치.
  - ADB를 이용해 에뮬레이터 앱 강제 종료/재실행을 반복 수행.

## 미해결/추가 확인 사항
- 역지오코딩은 Geocoder 서비스/네트워크에 의존하므로 Play 서비스가 없는 AVD에서는 도시명이 표시되지 않을 수 있음.
- 썸네일 지연 로딩만 도입했으며, 응답 크기/TTFB 최적화(페이징, CDN, 압축)는 추가 측정 후 적용 필요.
