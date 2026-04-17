package com.channelmanager.kotlin.config // 설정 테스트 패키지

import org.junit.jupiter.api.BeforeEach // 각 테스트 전 실행
import org.junit.jupiter.api.MethodOrderer // 테스트 메서드 실행 순서 제어
import org.junit.jupiter.api.Order // 테스트 실행 순서 지정
import org.junit.jupiter.api.Test // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance // 테스트 인스턴스 생명주기 설정
import org.junit.jupiter.api.TestMethodOrder // 테스트 메서드 정렬 전략
import org.springframework.boot.test.context.SpringBootTest // 전체 애플리케이션 컨텍스트 로드
import org.springframework.boot.test.web.server.LocalServerPort // 랜덤 포트 주입
import org.springframework.context.annotation.Import // 설정 클래스 임포트
import org.springframework.test.web.reactive.server.WebTestClient // WebFlux 테스트용 HTTP 클라이언트
import java.time.Duration // 시간 간격

// Rate Limiting 필터 통합 테스트
// Token Bucket 알고리즘 기반 IP별 API 호출 제한을 검증한다
// @TestMethodOrder: 테스트 순서를 제어하여 정상 요청 테스트를 먼저 실행한다
//   — 같은 IP의 버킷을 공유하므로, Rate Limit 초과 테스트가 먼저 실행되면
//     정상 요청 테스트가 토큰 부족으로 실패할 수 있다
@Import(TestcontainersConfig::class, TestSecurityConfig::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["rate-limit.capacity=50", "rate-limit.refill-tokens=50"] // Rate Limit 테스트용 낮은 값
)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RateLimitFilterTest {

    @LocalServerPort // 랜덤 포트 주입
    private var port: Int = 0

    private lateinit var webTestClient: WebTestClient

    @BeforeEach // 각 테스트 전 WebTestClient 초기화
    fun setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port") // 테스트 서버 URL
            .responseTimeout(Duration.ofSeconds(10)) // 응답 타임아웃
            .build()
    }

    // 정상 요청 시 200 OK 반환 확인 (가장 먼저 실행)
    // Rate Limiting 제한(50건/초) 이내의 단일 요청은 정상 처리되어야 한다
    @Test
    @Order(1)
    fun `정상 요청은 200 OK를 반환한다`() {
        webTestClient.get()
            .uri("/api/statistics/summary") // 통계 API (인증 불필요, 파라미터 불필요)
            .exchange()
            .expectStatus().isOk // 200 OK 확인
    }

    // 제외 경로는 Rate Limiting 미적용 확인 (Rate Limit 초과 전에 실행)
    // /v3/api-docs는 제외 경로이므로 항상 정상 응답이어야 한다
    @Test
    @Order(2)
    fun `제외 경로는 Rate Limiting이 적용되지 않는다`() {
        webTestClient.get()
            .uri("/v3/api-docs") // OpenAPI 스펙 — 제외 경로
            .exchange()
            .expectStatus().isOk // 제외 경로는 항상 200 OK
    }

    // Rate Limit 초과 시 429 Too Many Requests 반환 확인
    // 버킷 용량(50건)을 초과하면 429 응답이 반환되어야 한다
    @Test
    @Order(3)
    fun `Rate Limit 초과 시 429 Too Many Requests를 반환한다`() {
        // 버킷 용량을 모두 소비하여 429 유발
        var rateLimited = false
        for (i in 1..100) { // 충분한 요청으로 Rate Limit 초과 보장
            val result = webTestClient.get()
                .uri("/api/statistics/summary")
                .exchange()
                .returnResult(String::class.java)

            // 429 응답을 받으면 Rate Limiting이 동작한 것
            if (result.status.value() == 429) {
                rateLimited = true

                // Retry-After 헤더도 함께 검증
                val retryAfter = result.responseHeaders.getFirst("Retry-After")
                assert(retryAfter == "1") { "Retry-After 헤더가 '1'이어야 합니다. 실제: $retryAfter" }

                break
            }
        }

        // 100건 요청 중 최소 1건은 429를 받아야 한다
        assert(rateLimited) { "100건 요청 중 429 응답을 받지 못했습니다" }
    }

    // 제외 경로는 Rate Limit 초과 후에도 정상 응답하는지 확인
    // API 경로의 버킷이 소진되어도, 제외 경로는 영향받지 않아야 한다
    @Test
    @Order(4)
    fun `Rate Limit 초과 후에도 제외 경로는 정상 응답한다`() {
        // 이전 테스트에서 버킷이 소진된 상태
        // 제외 경로는 Rate Limiting을 거치지 않으므로 여전히 200 OK
        webTestClient.get()
            .uri("/v3/api-docs") // OpenAPI 스펙 — 제외 경로
            .exchange()
            .expectStatus().isOk
    }
}
