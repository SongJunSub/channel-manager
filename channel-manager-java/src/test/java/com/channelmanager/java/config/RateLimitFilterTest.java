package com.channelmanager.java.config; // 설정 테스트 패키지

import org.junit.jupiter.api.BeforeEach; // 각 테스트 전 실행
import org.junit.jupiter.api.MethodOrderer; // 테스트 메서드 실행 순서 제어
import org.junit.jupiter.api.Order; // 테스트 실행 순서 지정
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance; // 테스트 인스턴스 생명주기 설정
import org.junit.jupiter.api.TestMethodOrder; // 테스트 메서드 정렬 전략
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import org.springframework.boot.test.web.server.LocalServerPort; // 랜덤 포트 주입
import org.springframework.context.annotation.Import; // 설정 클래스 임포트
import org.springframework.test.web.reactive.server.WebTestClient; // WebFlux 테스트용 HTTP 클라이언트

import java.time.Duration; // 시간 간격

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 검증 메서드

// Rate Limiting 필터 통합 테스트
// Token Bucket 알고리즘 기반 IP별 API 호출 제한을 검증한다
// @TestMethodOrder: 테스트 순서를 제어하여 정상 요청 테스트를 먼저 실행한다
//   — 같은 IP의 버킷을 공유하므로, Rate Limit 초과 테스트가 먼저 실행되면
//     정상 요청 테스트가 토큰 부족으로 실패할 수 있다
// Kotlin 모듈의 RateLimitFilterTest와 동일한 시나리오를 Java로 구현한다
@Import(TestcontainersConfig.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"rate-limit.capacity=50", "rate-limit.refill-tokens=50"} // Rate Limit 테스트용 낮은 값
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RateLimitFilterTest {

    @LocalServerPort // 랜덤 포트 주입
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach // 각 테스트 전 WebTestClient 초기화
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port) // 테스트 서버 URL
            .responseTimeout(Duration.ofSeconds(10)) // 응답 타임아웃
            .build();
    }

    // 정상 요청 시 200 OK 반환 확인 (가장 먼저 실행)
    // Rate Limiting 제한(50건/초) 이내의 단일 요청은 정상 처리되어야 한다
    @Test
    @Order(1)
    void 정상_요청은_200_OK를_반환한다() {
        webTestClient.get()
            .uri("/api/statistics/summary") // 통계 API (인증 불필요, 파라미터 불필요)
            .exchange()
            .expectStatus().isOk(); // 200 OK 확인
    }

    // 제외 경로는 Rate Limiting 미적용 확인 (Rate Limit 초과 전에 실행)
    // /v3/api-docs는 제외 경로이므로 항상 정상 응답이어야 한다
    @Test
    @Order(2)
    void 제외_경로는_Rate_Limiting이_적용되지_않는다() {
        webTestClient.get()
            .uri("/v3/api-docs") // OpenAPI 스펙 — 제외 경로
            .exchange()
            .expectStatus().isOk(); // 제외 경로는 항상 200 OK
    }

    // Rate Limit 초과 시 429 Too Many Requests 반환 확인
    // 버킷 용량(50건)을 초과하면 429 응답이 반환되어야 한다
    @Test
    @Order(3)
    void Rate_Limit_초과_시_429_Too_Many_Requests를_반환한다() {
        // 버킷 용량을 모두 소비하여 429 유발
        boolean rateLimited = false;
        for (int i = 1; i <= 100; i++) { // 충분한 요청으로 Rate Limit 초과 보장
            var result = webTestClient.get()
                .uri("/api/statistics/summary")
                .exchange()
                .returnResult(String.class);

            // 429 응답을 받으면 Rate Limiting이 동작한 것
            if (result.getStatus().value() == 429) {
                rateLimited = true;

                // Retry-After 헤더도 함께 검증
                String retryAfter = result.getResponseHeaders().getFirst("Retry-After");
                assertThat(retryAfter)
                    .withFailMessage("Retry-After 헤더가 '1'이어야 합니다. 실제: " + retryAfter)
                    .isEqualTo("1");

                break;
            }
        }

        // 100건 요청 중 최소 1건은 429를 받아야 한다
        assertThat(rateLimited)
            .withFailMessage("100건 요청 중 429 응답을 받지 못했습니다")
            .isTrue();
    }

    // 제외 경로는 Rate Limit 초과 후에도 정상 응답하는지 확인
    // API 경로의 버킷이 소진되어도, 제외 경로는 영향받지 않아야 한다
    @Test
    @Order(4)
    void Rate_Limit_초과_후에도_제외_경로는_정상_응답한다() {
        // 이전 테스트에서 버킷이 소진된 상태
        // 제외 경로는 Rate Limiting을 거치지 않으므로 여전히 200 OK
        webTestClient.get()
            .uri("/v3/api-docs") // OpenAPI 스펙 — 제외 경로
            .exchange()
            .expectStatus().isOk();
    }
}
