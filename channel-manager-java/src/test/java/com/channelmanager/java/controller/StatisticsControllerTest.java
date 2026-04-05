package com.channelmanager.java.controller; // 컨트롤러 테스트 패키지

import com.channelmanager.java.dto.StatisticsResponse.ChannelStatistics; // 채널별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.EventStatistics; // 이벤트 타입별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.RoomTypeStatistics; // 객실 타입별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.SummaryStatistics; // 전체 요약 통계 DTO
import org.junit.jupiter.api.BeforeEach; // 각 테스트 전 실행
import org.junit.jupiter.api.MethodOrderer; // 테스트 메서드 실행 순서 제어
import org.junit.jupiter.api.Order; // 테스트 실행 순서 지정
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance; // 테스트 인스턴스 생명주기 설정
import org.junit.jupiter.api.TestMethodOrder; // 테스트 메서드 정렬 전략
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.java.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort; // 랜덤 포트 주입
import org.springframework.test.web.reactive.server.WebTestClient; // WebFlux 테스트용 HTTP 클라이언트
import java.math.BigDecimal; // 금액 타입
import java.time.Duration; // 시간 간격

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 정적 import

// 통계 컨트롤러 통합 테스트
// V7 샘플 데이터를 기반으로 통계 API를 검증한다
// 별도 테스트 데이터 생성 없이 V7 샘플 데이터만으로 테스트한다
// Kotlin과 동일한 테스트 구조이지만, Java에서는 명시적 타입 선언과 메서드 호출을 사용한다
// @TestInstance(PER_CLASS): 테스트 간 상태 공유 가능
@Import(TestcontainersConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatisticsControllerTest {

    @LocalServerPort // 랜덤 포트 주입
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach // 각 테스트 전 WebTestClient 초기화
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();
    }

    // ===== 채널별 통계 테스트 =====

    @Test // 채널별 통계 조회 테스트
    @Order(1)
    void 채널별_통계_각_채널의_예약_매출_통계를_반환한다() {
        // V7 샘플: 4개 채널 (DIRECT, BOOKING, AGODA, TRIP)
        webTestClient.get()
            .uri("/api/statistics/channels")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ChannelStatistics.class)
            .consumeWith(result -> {
                var stats = result.getResponseBody();
                // 4개 채널 통계가 반환되어야 한다
                assertThat(stats).hasSizeGreaterThanOrEqualTo(4);
                // 각 통계에 필수 필드가 존재하는지 확인
                stats.forEach(stat -> {
                    assertThat(stat.channelId()).isNotNull();
                    assertThat(stat.channelCode()).isNotBlank();
                    assertThat(stat.channelName()).isNotBlank();
                    assertThat(stat.totalRevenue()).isNotNull();
                });
            });
    }

    @Test // 채널별 통계 — 매출 합계가 0 이상인지 확인
    @Order(2)
    void 채널별_통계_매출_합계가_0_이상이다() {
        webTestClient.get()
            .uri("/api/statistics/channels")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ChannelStatistics.class)
            .consumeWith(result -> {
                var stats = result.getResponseBody();
                stats.forEach(stat -> {
                    assertThat(stat.totalRevenue())
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                    assertThat(stat.reservationCount() + stat.cancelledCount())
                        .isGreaterThanOrEqualTo(0);
                });
            });
    }

    // ===== 이벤트 타입별 통계 테스트 =====

    @Test // 이벤트 타입별 통계 조회 테스트
    @Order(3)
    void 이벤트_통계_각_이벤트_타입의_발생_건수를_반환한다() {
        webTestClient.get()
            .uri("/api/statistics/events")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(EventStatistics.class)
            .consumeWith(result -> {
                var stats = result.getResponseBody();
                assertThat(stats).isNotEmpty();
                stats.forEach(stat -> {
                    assertThat(stat.eventType()).isNotNull();
                    assertThat(stat.count()).isGreaterThanOrEqualTo(1);
                });
            });
    }

    // ===== 객실 타입별 통계 테스트 =====

    @Test // 객실 타입별 통계 조회 테스트
    @Order(4)
    void 객실_타입별_통계_각_객실_타입의_예약_매출_통계를_반환한다() {
        webTestClient.get()
            .uri("/api/statistics/rooms")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(RoomTypeStatistics.class)
            .consumeWith(result -> {
                var stats = result.getResponseBody();
                assertThat(stats).hasSizeGreaterThanOrEqualTo(5);
                stats.forEach(stat -> {
                    assertThat(stat.roomTypeId()).isNotNull();
                    assertThat(stat.roomTypeName()).isNotBlank();
                    assertThat(stat.totalRevenue())
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                });
            });
    }

    // ===== 전체 요약 통계 테스트 =====

    @Test // 전체 요약 통계 조회 테스트
    @Order(5)
    void 요약_통계_전체_시스템_현황을_반환한다() {
        webTestClient.get()
            .uri("/api/statistics/summary")
            .exchange()
            .expectStatus().isOk()
            .expectBody(SummaryStatistics.class)
            .consumeWith(result -> {
                var summary = result.getResponseBody();
                assertThat(summary).isNotNull();
                assertThat(summary.totalReservations()).isGreaterThanOrEqualTo(1);
                assertThat(summary.confirmedCount() + summary.cancelledCount())
                    .isEqualTo(summary.totalReservations());
                assertThat(summary.totalRevenue())
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(summary.totalEvents()).isGreaterThanOrEqualTo(1);
                assertThat(summary.activeChannels()).isGreaterThanOrEqualTo(1);
            });
    }

    @Test // 요약 통계 — 확정 예약만 매출에 포함되는지 확인
    @Order(6)
    void 요약_통계_확정_예약이_있으면_매출이_0보다_크다() {
        webTestClient.get()
            .uri("/api/statistics/summary")
            .exchange()
            .expectStatus().isOk()
            .expectBody(SummaryStatistics.class)
            .consumeWith(result -> {
                var summary = result.getResponseBody();
                if (summary.confirmedCount() > 0) {
                    assertThat(summary.totalRevenue())
                        .isGreaterThan(BigDecimal.ZERO);
                }
            });
    }
}
