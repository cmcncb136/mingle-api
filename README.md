# Mingle API

Mingle은 관심사 기반으로 그룹을 만들고, 같은 관심사를 가진 사람들이 그룹에 신청하거나 초대받아 함께 활동할 수 있는 커뮤니티 서비스입니다.

그룹 내에서는 멤버들이 서로 트랙을 생성하는 HOST와 트랙에 참여하는 FOLLOWER 역할을 맡아, 관심사를 공유하고 다양한 활동을 함께 체험할 수 있습니다.


이 저장소는 Mingle 프론트엔드(https://github.com/cmcncb136/mingle)와 연동되는 백엔드 애플리케이션입니다. 인증, 그룹 참여 흐름, 트랙 운영, 공지사항, 알림, 파일 메타데이터 관리를 담당합니다.

## 주요 기능

### 인증 및 사용자

* 회원가입
* 로그인
* JWT Access Token, Refresh Token 발급
* Refresh Token 기반 Access Token 재발급
* 내 프로필 조회
* 사용자 프로필 조회
* Spring Security 사용자 인증 정보 로드

### 그룹

* 그룹 생성, 조회, 검색, 수정
* 내가 참여한 그룹 목록 조회
* 그룹 가입 신청
* 가입 요청 목록 조회
* 가입 요청 승인/거절
* 이메일 기반 그룹 초대
* 그룹 초대 수락
* 그룹 멤버 조회
* 내 그룹 참여 상태 조회
* 그룹 내 Host/Follower 역할 부여

### 트랙

* 그룹별 트랙 목록 조회
* 사용자별 참여 트랙 조회
* 트랙 상세 조회
* 트랙 생성 및 수정
* 트랙 신청
* 트랙 신청 취소
* 트랙 참여자 조회
* 트랙 역할 관리

### 공지사항

* 그룹 공지사항 생성
* 그룹 공지사항 목록 조회
* 공지사항 상세 조회
* 공지사항 수정
* 공지 타입 기반 관리

### 알림

* 알림 목록 조회
* 알림 타입, 카테고리, 메시지, 읽음 여부 기반 검색
* 알림 읽음 처리
* 도메인 이벤트 기반 알림 생성
* 프론트엔드 이동을 위한 알림 링크 해석

### 파일

* 파일 업로드
* 파일 메타데이터 관리
* 파일 저장 키 관리
* 파일 접근 URL 관리
* 파일 소유자와 목적 분리

## 기술 스택

* Java 25
* Spring Boot 3.5
* Spring Web
* Spring Security
* Spring Data JPA
* JWT
* Lombok
* QueryDSL
* Springdoc OpenAPI
* MariaDB
* H2
* Gradle
* Docker

## 백엔드 아키텍처

백엔드는 `com.kesi.tracker` 루트 패키지 아래에 도메인별 패키지를 두고, 각 도메인 내부에서 계층을 분리하는 layered architecture를 사용합니다.

```text
com.kesi.tracker
├── auth          # 인증, 토큰 재발급
├── user          # 사용자, 프로필, Spring Security 사용자 정보
├── group         # 그룹, 멤버십, 초대, 가입 신청, 그룹 역할
├── track         # 트랙, 트랙 신청, 트랙 멤버, 트랙 역할
├── notice        # 그룹 공지사항
├── notification  # 알림, 알림 링크, 알림 이벤트
├── file          # 파일 업로드, 파일 메타데이터, 파일 소유자/목적
├── core          # 보안, JWT, 예외 처리, 설정, 공통 응답
└── util          # 공통 유틸과 검증
```

### 계층별 역할

* `presentation`: REST Controller 계층입니다. HTTP 요청을 받고 application 계층으로 전달합니다.
* `application`: 서비스와 DTO 계층입니다. 유스케이스, 트랜잭션, 도메인 조합을 담당합니다.
* `domain`: 핵심 도메인 모델, 값 객체, enum, 도메인 이벤트를 포함합니다.
* `infrastructure`: JPA Entity, Spring Data JPA Repository, Repository 구현체 등 기술 세부사항을 담당합니다.
* `core`: Security, JWT, Swagger, CORS, JPA 설정, 예외 처리, 필터 등 전역 설정을 담당합니다.

## 주요 API 영역

| Domain | Endpoint Examples |
| --- | --- |
| Auth | `POST /auth/refresh` |
| User | `POST /users`, `GET /users/profile`, `GET /users/{uid}` |
| Group | `GET /groups`, `POST /groups`, `GET /groups/{gid}`, `PUT /groups/{gid}` |
| Group Member | `GET /groups/{gid}/users`, `GET /groups/{gid}/join-requests`, `PATCH /groups/{gid}/join-requests/{relationId}` |
| Invitation | `POST /groups/{gid}/invitations`, `POST /groups/{gid}/invitations/accept` |
| Track | `GET /tracks/{trackId}`, `POST /groups/{gid}/tracks`, `PUT /tracks/{trackId}` |
| Track Assignment | `POST /tracks/{trackId}/assignments`, `DELETE /tracks/{trackId}/assignments` |
| Notice | `GET /groups/{gid}/notices`, `POST /groups/{gid}/notices`, `GET /notices/{noticeId}`, `PATCH /notices/{noticeId}` |
| Notification | `GET /notifications`, `PATCH /notifications/{notificationId}/read` |
| File | `POST /files` |

로컬 개발 환경에서는 MariaDB 대신 H2를 사용할 수 있도록 런타임 의존성이 포함되어 있습니다. 실제 실행 값은 로컬 환경 또는 배포 환경에 맞춰 조정합니다.

## 실행 방법

### 테스트

```bash
./gradlew test
```

### 빌드

```bash
./gradlew clean build
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

또는 빌드된 jar를 직접 실행합니다.

```bash
java -jar build/libs/*.jar
```

## Swagger

Springdoc OpenAPI를 사용합니다.

* Swagger UI: `http://localhost:1204/swagger-ui/index.html`
* OpenAPI JSON: `http://localhost:1204/v3/api-docs`

## Docker 배포

API 서버 Dockerfile은 빌드된 jar 파일을 실행하는 런타임 이미지입니다.

```bash
./gradlew clean build

docker build -t mingle-api .
docker run -p 1204:1204 mingle-api
```

Dockerfile은 `build/libs/*.jar`를 `/app/app.jar`로 복사한 뒤 Java 25 JRE 환경에서 실행합니다.

## 관련 저장소

* Frontend Repository: https://github.com/cmcncb136/mingle
* 배포 URL: https://kesi-mingle.duckdns.org
* Swagger URL: https://kesi-mingle-api.duckdns.org:1201/swagger-ui/index.html#/
