# Phase 15 — Docker 이미지 빌드 (컨테이너화)

## 1. Docker란?

### 1.1 컨테이너 vs 가상머신

```
┌─────────────────────────────────────────────────┐
│              가상머신 (VM)                        │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐            │
│  │  App A  │ │  App B  │ │  App C  │            │
│  │  Libs   │ │  Libs   │ │  Libs   │            │
│  │ Guest OS│ │ Guest OS│ │ Guest OS│  ← OS 전체 │
│  └─────────┘ └─────────┘ └─────────┘            │
│  ┌─────────────────────────────────┐             │
│  │        Hypervisor               │             │
│  └─────────────────────────────────┘             │
│  ┌─────────────────────────────────┐             │
│  │          Host OS                │             │
│  └─────────────────────────────────┘             │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│              컨테이너 (Docker)                    │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐            │
│  │  App A  │ │  App B  │ │  App C  │            │
│  │  Libs   │ │  Libs   │ │  Libs   │            │
│  └─────────┘ └─────────┘ └─────────┘            │
│  ┌─────────────────────────────────┐             │
│  │        Docker Engine            │  ← OS 공유  │
│  └─────────────────────────────────┘             │
│  ┌─────────────────────────────────┐             │
│  │          Host OS                │             │
│  └─────────────────────────────────┘             │
└─────────────────────────────────────────────────┘
```

- **가상머신**: Guest OS를 통째로 띄움 → 무겁고 느림 (GB 단위)
- **컨테이너**: Host OS의 커널을 공유 → 가볍고 빠름 (MB 단위)
- 컨테이너는 프로세스 격리 기술(namespace, cgroup)을 사용

### 1.2 Docker의 핵심 개념

| 개념 | 설명 | 비유 |
|------|------|------|
| **Image** | 실행에 필요한 모든 것을 포함한 읽기 전용 템플릿 | 클래스 |
| **Container** | 이미지를 실행한 인스턴스 | 객체 (new Class()) |
| **Dockerfile** | 이미지를 만드는 빌드 스크립트 | 소스코드 |
| **Registry** | 이미지를 저장/배포하는 저장소 | Maven Central |
| **Volume** | 컨테이너 외부에 데이터를 영속화 | 외장 하드 |
| **Network** | 컨테이너 간 통신을 위한 가상 네트워크 | VPC |

## 2. Dockerfile 작성법

### 2.1 기본 명령어

```dockerfile
# FROM: 베이스 이미지 지정 (모든 Dockerfile의 시작)
FROM eclipse-temurin:25-jre-alpine

# LABEL: 이미지 메타데이터
LABEL maintainer="developer@example.com"

# WORKDIR: 작업 디렉토리 설정 (없으면 자동 생성)
WORKDIR /app

# COPY: 호스트 파일을 컨테이너로 복사
COPY build/libs/*.jar app.jar

# EXPOSE: 컨테이너가 사용할 포트 선언 (문서화 목적)
EXPOSE 8080

# ENV: 환경변수 설정
ENV JAVA_OPTS="-Xmx512m"

# ENTRYPOINT: 컨테이너 시작 시 실행할 명령 (변경 불가)
ENTRYPOINT ["java", "-jar", "app.jar"]

# CMD: ENTRYPOINT의 기본 인자 (docker run 시 덮어쓰기 가능)
CMD ["--spring.profiles.active=prod"]
```

### 2.2 멀티스테이지 빌드

하나의 Dockerfile에서 **빌드 단계**와 **실행 단계**를 분리하는 기법이다.

```
┌──────────────────────────────────┐
│  Stage 1: Build (JDK + Gradle)  │
│  - 소스 코드 복사                  │
│  - Gradle 빌드 실행               │
│  - JAR 파일 생성                   │
│  (이미지 크기: ~800MB)             │
└──────────┬───────────────────────┘
           │ JAR 파일만 복사
           ▼
┌──────────────────────────────────┐
│  Stage 2: Run (JRE만)            │
│  - JRE만 포함 (JDK 불필요)         │
│  - JAR 파일 실행                   │
│  (이미지 크기: ~200MB)             │
└──────────────────────────────────┘
```

**왜 멀티스테이지인가?**
- 빌드에는 JDK, Gradle, 소스 코드가 필요하지만 실행에는 JRE + JAR만 필요
- 빌드 도구를 최종 이미지에 포함하지 않으므로 이미지 크기가 대폭 감소
- 보안: 소스 코드, 빌드 도구가 프로덕션 이미지에 노출되지 않음

### 2.3 레이어 캐싱 전략

Docker는 Dockerfile의 각 명령을 **레이어**로 캐싱한다.
변경이 적은 레이어를 먼저, 자주 변경되는 레이어를 나중에 배치하면 빌드 속도가 빨라진다.

```dockerfile
# 나쁜 예: 소스 변경 시 의존성도 다시 다운로드
COPY . .
RUN ./gradlew build

# 좋은 예: 의존성 레이어를 분리하여 캐싱
COPY build.gradle.kts settings.gradle.kts ./     # 변경 드묾 → 캐시 활용
RUN ./gradlew dependencies                       # 의존성 다운로드 (캐시됨)
COPY src/ src/                                    # 자주 변경 → 여기부터 재빌드
RUN ./gradlew build                              # 소스만 다시 컴파일
```

```
레이어 캐싱 동작:

Layer 1: FROM eclipse-temurin:25    ← 캐시됨 (베이스 이미지 변경 없음)
Layer 2: COPY build.gradle.kts      ← 캐시됨 (빌드 설정 변경 없음)
Layer 3: RUN gradlew dependencies   ← 캐시됨 (의존성 변경 없음)
Layer 4: COPY src/ src/             ← ❌ 변경됨 (소스 코드 수정)
Layer 5: RUN gradlew build          ← ❌ 재실행 (이전 레이어 변경)
```

## 3. Docker Compose

### 3.1 왜 Docker Compose가 필요한가?

```
# 컨테이너를 하나씩 실행하면?
docker run -d --name postgres ...
docker run -d --name kotlin-app --link postgres ...
docker run -d --name java-app --link postgres ...

# 문제점:
# - 매번 긴 명령어를 입력
# - 컨테이너 간 네트워크 수동 설정
# - 시작 순서 관리 어려움
# - 환경변수 관리 복잡
```

Docker Compose는 **여러 컨테이너를 하나의 YAML 파일로 정의하고 한 번에 관리**하는 도구다.

### 3.2 docker-compose.yml 구조

```yaml
services:
  # 서비스 정의 (각각 하나의 컨테이너)
  postgres:
    image: postgres:17-alpine        # 사용할 이미지
    environment:                     # 환경변수
      POSTGRES_DB: channel_manager
    ports:
      - "5432:5432"                  # 포트 매핑
    volumes:
      - pgdata:/var/lib/postgresql   # 데이터 영속화
    healthcheck:                     # 헬스체크
      test: ["CMD-SHELL", "pg_isready -U postgres"]

  kotlin-app:
    build:                           # Dockerfile로 이미지 빌드
      context: .                     # 빌드 컨텍스트 (루트 디렉토리)
      dockerfile: channel-manager-kotlin/Dockerfile
    depends_on:                      # 의존 서비스 (시작 순서)
      postgres:
        condition: service_healthy   # postgres가 healthy일 때 시작
    environment:
      DB_HOST: postgres              # 컨테이너 네트워크에서는 서비스명으로 접근

volumes:
  pgdata:                            # 네임드 볼륨 정의
```

### 3.3 서비스 간 네트워크

```
┌─────────────────────────────────────────────────┐
│          Docker Compose 기본 네트워크              │
│          (channel-manager_default)               │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ postgres │  │kotlin-app│  │ java-app │       │
│  │ :5432    │  │ :8080    │  │ :8081    │       │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘       │
│       │              │              │             │
│  서비스명으로 서로 통신 가능                         │
│  kotlin-app → postgres:5432 (OK)                 │
│  java-app   → postgres:5432 (OK)                 │
└─────────────────────────────────────────────────┘
│              │              │
호스트:5432    호스트:8080    호스트:8081
```

- Docker Compose는 자동으로 **기본 네트워크**를 생성
- 같은 네트워크 안에서는 **서비스 이름**으로 DNS 해석 가능
- `localhost` 대신 `postgres`(서비스명)로 DB에 접속

## 4. 환경변수 외부화

### 4.1 Spring Boot의 환경변수 바인딩

Spring Boot는 `application.yml`의 값을 **환경변수로 오버라이드**할 수 있다.

```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:channel_manager}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

- `${DB_HOST:localhost}` — 환경변수 `DB_HOST`가 있으면 사용, 없으면 `localhost` (기본값)
- 로컬 개발 시: 환경변수 없이 기본값으로 동작 (localhost)
- Docker 환경: 환경변수로 서비스명 전달 (postgres)

### 4.2 환경변수 우선순위

```
1. 명령줄 인자 (--server.port=9090)
2. 환경변수 (SERVER_PORT=9090)
3. application-{profile}.yml
4. application.yml
5. 기본값
```

## 5. 베이스 이미지 선택

### 5.1 JDK vs JRE

| 이미지 | 크기 | 용도 |
|--------|------|------|
| eclipse-temurin:25-jdk | ~400MB | 빌드 단계 (javac, jar 필요) |
| eclipse-temurin:25-jre | ~200MB | 실행 단계 (java만 필요) |
| eclipse-temurin:25-jre-alpine | ~150MB | 실행 단계 (Alpine Linux 기반, 최소 크기) |

### 5.2 Alpine Linux

- 일반 Linux (Ubuntu/Debian): ~80MB
- Alpine Linux: ~5MB
- 패키지 관리자: apk (apt 대신)
- musl libc 사용 (glibc 대신) → 일부 호환성 이슈 가능하나 JRE에서는 문제없음

## 6. 헬스체크 (Health Check)

### 6.1 왜 헬스체크가 필요한가?

```
컨테이너 시작 ≠ 애플리케이션 준비 완료

컨테이너 상태: running  (프로세스가 실행 중)
애플리케이션:  아직 초기화 중... (DB 연결, Flyway 마이그레이션...)
```

`depends_on`만으로는 **컨테이너가 시작**되었는지만 확인한다.
`healthcheck`를 추가하면 **애플리케이션이 실제로 준비**되었는지 확인할 수 있다.

### 6.2 Spring Boot Actuator 헬스체크

```yaml
# docker-compose.yml
healthcheck:
  test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
  interval: 10s      # 10초마다 체크
  timeout: 5s        # 5초 이내 응답 없으면 실패
  retries: 5         # 5회 실패 시 unhealthy
  start_period: 30s  # 시작 후 30초는 실패 무시 (초기화 시간)
```

이 프로젝트에서는 Actuator를 사용하지 않으므로, 간단한 curl/wget 대신
Spring Boot가 8080 포트에 응답하는지로 판단한다.

## 7. 이 프로젝트의 Docker 구성

### 7.1 전체 아키텍처

```
docker compose up
      │
      ├── postgres (PostgreSQL 17)
      │     ├── 포트: 5432
      │     ├── healthcheck: pg_isready
      │     └── 볼륨: channel-manager-pgdata
      │
      ├── kotlin-app (Spring Boot WebFlux)
      │     ├── 포트: 8080
      │     ├── depends_on: postgres (healthy)
      │     ├── DB_HOST=postgres
      │     └── Flyway 마이그레이션 자동 실행
      │
      └── java-app (Spring Boot WebFlux)
            ├── 포트: 8081
            ├── depends_on: postgres (healthy)
            ├── DB_HOST=postgres
            └── Flyway 마이그레이션 자동 실행
```

### 7.2 빌드 흐름

```
① docker compose build
   └── 각 모듈의 Dockerfile 실행
       ├── Stage 1 (build): Gradle 빌드 → JAR 생성
       └── Stage 2 (run): JRE + JAR → 최종 이미지

② docker compose up -d
   └── postgres 시작 → healthy 확인 → kotlin-app, java-app 시작

③ Flyway 마이그레이션
   └── 각 앱이 시작되면서 자동으로 DB 스키마 생성

④ 준비 완료
   └── http://localhost:8080 (Kotlin)
   └── http://localhost:8081 (Java)
```

### 7.3 환경 분리

```
로컬 개발 (IDE에서 실행):
  - DB_HOST 미설정 → 기본값 localhost 사용
  - docker compose up postgres 만 실행

Docker 환경 (전체 컨테이너):
  - DB_HOST=postgres → Docker 네트워크의 postgres 서비스 접속
  - docker compose up 으로 전체 실행
```
