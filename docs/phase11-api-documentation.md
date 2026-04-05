# Phase 11 — API 문서화 (SpringDoc OpenAPI + Swagger UI)

## 1. 개요

### 1.1 OpenAPI란?

OpenAPI(구 Swagger Specification)는 **REST API의 구조를 기술하는 표준 스펙**이다.
API의 엔드포인트, 요청/응답 형식, 인증 방식 등을 JSON/YAML로 정의한다.

### 1.2 SpringDoc OpenAPI란?

SpringDoc은 **Spring 애플리케이션에서 OpenAPI 3.0 문서를 자동 생성**하는 라이브러리이다.
Spring WebFlux 컨트롤러를 분석하여 별도 코드 없이도 API 문서를 만든다.

```
Spring WebFlux Controller → SpringDoc 자동 분석 → OpenAPI 3.0 JSON → Swagger UI 렌더링
```

### 1.3 Swagger UI란?

Swagger UI는 OpenAPI 스펙을 **인터랙티브한 웹 UI로 시각화**하는 도구이다.
브라우저에서 직접 API를 호출하고 응답을 확인할 수 있다.

## 2. SpringDoc과 Spring WebFlux

### 2.1 의존성

```gradle
// WebFlux 전용 SpringDoc (MVC가 아닌 WebFlux용)
implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6")
```

이 하나의 의존성이 다음을 모두 포함한다:
- OpenAPI 3.0 스펙 자동 생성 (`/v3/api-docs`)
- Swagger UI 웹 인터페이스 (`/swagger-ui.html`)
- Spring WebFlux Mono/Flux 타입 자동 해석

### 2.2 자동 문서화

SpringDoc은 다음을 자동으로 분석한다:

| Spring 어노테이션 | OpenAPI 변환 |
|-------------------|-------------|
| `@RestController` | API 태그 (컨트롤러 단위) |
| `@GetMapping("/api/...")` | GET 엔드포인트 |
| `@PostMapping` | POST 엔드포인트 |
| `@RequestParam` | 쿼리 파라미터 |
| `@PathVariable` | 경로 파라미터 |
| `@RequestBody` | 요청 본문 스키마 |
| `Mono<T>` / `Flux<T>` | 응답 스키마 (T 타입) |
| `@Schema` | 필드 설명/예시 |

**별도 어노테이션 없이도** 기존 컨트롤러에서 API 문서가 자동 생성된다.

### 2.3 OpenAPI 설정 빈

```kotlin
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info()
            .title("Channel Manager API")
            .description("호텔 멀티 채널 예약 동기화 시스템")
            .version("1.0.0"))
}
```

## 3. 접근 경로

| 경로 | 설명 |
|------|------|
| `/swagger-ui.html` | Swagger UI (인터랙티브 API 탐색기) |
| `/v3/api-docs` | OpenAPI 3.0 JSON 스펙 |
| `/v3/api-docs.yaml` | OpenAPI 3.0 YAML 스펙 |

## 4. 기존 @Schema 활용

프로젝트의 도메인 엔티티에는 이미 `@Schema` 어노테이션이 적용되어 있다.
SpringDoc은 이를 자동으로 인식하여 API 문서의 스키마 섹션에 반영한다.

```kotlin
// 이미 적용된 @Schema — SpringDoc이 자동 인식
@Schema(description = "예약 정보를 나타내는 엔티티")
data class Reservation(
    @field:Schema(description = "예약 ID (PK)", example = "1")
    val id: Long? = null,
    // ...
)
```

## 5. 핵심 학습 포인트

1. **OpenAPI 3.0**: REST API 문서화 표준 스펙
2. **SpringDoc**: Spring WebFlux에서 자동 API 문서 생성
3. **Swagger UI**: 인터랙티브 API 탐색기
4. **자동 문서화**: 기존 코드에서 추가 어노테이션 없이 문서 생성
5. **@Schema 재활용**: 도메인 엔티티의 기존 @Schema가 API 문서에 반영
