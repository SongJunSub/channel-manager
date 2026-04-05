# Phase 13 — Testcontainers (테스트 DB 자동화)

## 1. 문제: 현재 테스트의 한계

### 1.1 현재 방식

```
테스트 실행 전: docker compose up -d (수동)
  ↓
테스트: localhost:5432 PostgreSQL에 연결
  ↓
문제: Docker가 안 돌아가면 테스트 실패
```

- 테스트 실행 전에 `docker compose up -d`로 PostgreSQL을 수동으로 기동해야 한다
- 로컬 DB에 테스트 데이터가 누적되어 테스트 간 간섭이 발생할 수 있다
- CI/CD 환경에서 Docker Compose를 별도로 설정해야 한다

### 1.2 Testcontainers 방식

```
테스트 실행 시: Testcontainers가 자동으로 PostgreSQL 컨테이너 기동
  ↓
테스트: 임시 컨테이너의 랜덤 포트로 연결
  ↓
테스트 완료: 컨테이너 자동 삭제 (격리된 환경)
```

## 2. Testcontainers란?

Testcontainers는 **테스트 시 Docker 컨테이너를 자동으로 생성/삭제**하는 Java 라이브러리이다.

### 장점
- **자동화**: 테스트 시작 시 컨테이너 자동 기동, 종료 시 자동 삭제
- **격리**: 매 테스트마다 깨끗한 DB — 테스트 간 간섭 없음
- **CI/CD 호환**: Docker만 있으면 어디서든 테스트 가능
- **랜덤 포트**: 포트 충돌 없음 (로컬 PostgreSQL과 공존 가능)

## 3. Spring Boot 3.1+ ServiceConnection

Spring Boot 3.1부터 `@ServiceConnection` 어노테이션을 제공한다.
Testcontainers가 기동한 컨테이너의 접속 정보를 **자동으로 Spring 설정에 주입**한다.

```kotlin
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:17-alpine")
}
```

- `@ServiceConnection`: 컨테이너의 호스트/포트를 자동으로 `spring.r2dbc.url`, `spring.flyway.url`에 주입
- 별도 `application-test.yml`이 필요 없다 — Spring Boot가 알아서 설정

## 4. 구현 방식

### @Import로 테스트 설정 공유

```kotlin
// 테스트 설정 클래스 — Testcontainers PostgreSQL 빈 등록
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig { ... }

// 각 테스트 클래스에서 Import
@SpringBootTest
@Import(TestcontainersConfig::class)
class ReservationControllerTest { ... }
```

## 5. 핵심 학습 포인트

1. **Testcontainers**: 테스트 시 Docker 컨테이너 자동 생명주기 관리
2. **@ServiceConnection**: Spring Boot의 자동 접속 정보 주입
3. **PostgreSQLContainer**: PostgreSQL 전용 Testcontainers 모듈
4. **테스트 격리**: 매번 깨끗한 DB 환경에서 테스트
5. **CI/CD 호환**: Docker만 있으면 어디서든 동작
