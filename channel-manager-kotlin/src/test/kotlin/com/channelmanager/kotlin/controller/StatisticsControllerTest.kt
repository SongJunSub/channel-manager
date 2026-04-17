package com.channelmanager.kotlin.controller // 컨트롤러 테스트 패키지

import com.channelmanager.kotlin.dto.ChannelStatistics // 채널별 통계 DTO
import com.channelmanager.kotlin.dto.EventStatistics // 이벤트 타입별 통계 DTO
import com.channelmanager.kotlin.dto.RoomTypeStatistics // 객실 타입별 통계 DTO
import com.channelmanager.kotlin.dto.SummaryStatistics // 전체 요약 통계 DTO
import org.assertj.core.api.Assertions.assertThat // AssertJ 검증 메서드
import org.junit.jupiter.api.BeforeEach // 각 테스트 전 실행
import org.junit.jupiter.api.MethodOrderer // 테스트 메서드 실행 순서 제어
import org.junit.jupiter.api.Order // 테스트 실행 순서 지정
import org.junit.jupiter.api.Test // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance // 테스트 인스턴스 생명주기 설정
import org.junit.jupiter.api.TestMethodOrder // 테스트 메서드 정렬 전략
import org.springframework.beans.factory.annotation.Autowired // 의존성 주입
import org.springframework.boot.test.context.SpringBootTest // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.kotlin.config.TestcontainersConfig
import com.channelmanager.kotlin.config.TestSecurityConfig // Phase 21: 테스트 보안 설정
import org.springframework.context.annotation.Import
import org.springframework.boot.test.web.server.LocalServerPort // 랜덤 포트 주입
import org.springframework.test.web.reactive.server.WebTestClient // WebFlux 테스트용 HTTP 클라이언트
import java.math.BigDecimal // 금액 타입
import java.time.Duration // 시간 간격

// 통계 컨트롤러 통합 테스트
// V7 샘플 데이터를 기반으로 통계 API를 검증한다
// 별도 테스트 데이터 생성 없이 V7 샘플 데이터만으로 테스트한다
// @TestInstance(PER_CLASS): 테스트 간 상태 공유 가능
@Import(TestcontainersConfig::class, TestSecurityConfig::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatisticsControllerTest {

    @LocalServerPort // 랜덤 포트 주입
    private var port: Int = 0

    private lateinit var webTestClient: WebTestClient

    @BeforeEach // 각 테스트 전 WebTestClient 초기화
    fun setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(10))
            .build()
    }

    // ===== 채널별 통계 테스트 =====

    @Test // 채널별 통계 조회 테스트
    @Order(1)
    fun `채널별 통계 - 각 채널의 예약과 매출 통계를 반환한다`() {
        // V7 샘플: 4개 채널 (DIRECT, BOOKING, AGODA, TRIP)
        webTestClient.get()
            .uri("/api/statistics/channels")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(ChannelStatistics::class.java)
            .consumeWith<WebTestClient.ListBodySpec<ChannelStatistics>> { result ->
                val stats = result.responseBody!!
                // 4개 채널 통계가 반환되어야 한다
                assertThat(stats).hasSizeGreaterThanOrEqualTo(4)
                // 각 통계에 필수 필드가 존재하는지 확인
                stats.forEach { stat ->
                    assertThat(stat.channelId).isNotNull
                    assertThat(stat.channelCode).isNotBlank
                    assertThat(stat.channelName).isNotBlank
                    assertThat(stat.totalRevenue).isNotNull
                }
            }
    }

    @Test // 채널별 통계 — 매출 합계가 0 이상인지 확인
    @Order(2)
    fun `채널별 통계 - 매출 합계가 0 이상이다`() {
        webTestClient.get()
            .uri("/api/statistics/channels")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(ChannelStatistics::class.java)
            .consumeWith<WebTestClient.ListBodySpec<ChannelStatistics>> { result ->
                val stats = result.responseBody!!
                stats.forEach { stat ->
                    // 매출은 0 이상이어야 한다 (음수 매출은 불가능)
                    assertThat(stat.totalRevenue).isGreaterThanOrEqualTo(BigDecimal.ZERO)
                    // 예약 건수 + 취소 건수가 0 이상
                    assertThat(stat.reservationCount + stat.cancelledCount)
                        .isGreaterThanOrEqualTo(0)
                }
            }
    }

    // ===== 이벤트 타입별 통계 테스트 =====

    @Test // 이벤트 타입별 통계 조회 테스트
    @Order(3)
    fun `이벤트 통계 - 각 이벤트 타입의 발생 건수를 반환한다`() {
        // V7 샘플: 5개 이벤트 (3가지 타입)
        webTestClient.get()
            .uri("/api/statistics/events")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(EventStatistics::class.java)
            .consumeWith<WebTestClient.ListBodySpec<EventStatistics>> { result ->
                val stats = result.responseBody!!
                // 최소 1개 이상의 이벤트 타입이 있어야 한다
                assertThat(stats).isNotEmpty
                // 각 통계의 건수가 1 이상이어야 한다
                stats.forEach { stat ->
                    assertThat(stat.eventType).isNotNull
                    assertThat(stat.count).isGreaterThanOrEqualTo(1)
                }
            }
    }

    // ===== 객실 타입별 통계 테스트 =====

    @Test // 객실 타입별 통계 조회 테스트
    @Order(4)
    fun `객실 타입별 통계 - 각 객실 타입의 예약과 매출 통계를 반환한다`() {
        // V7 샘플: 5개 객실 타입 (서울신라 3 + 파라다이스 부산 2)
        webTestClient.get()
            .uri("/api/statistics/rooms")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(RoomTypeStatistics::class.java)
            .consumeWith<WebTestClient.ListBodySpec<RoomTypeStatistics>> { result ->
                val stats = result.responseBody!!
                assertThat(stats).hasSizeGreaterThanOrEqualTo(5)
                stats.forEach { stat ->
                    assertThat(stat.roomTypeId).isNotNull
                    assertThat(stat.roomTypeName).isNotBlank
                    assertThat(stat.totalRevenue).isGreaterThanOrEqualTo(BigDecimal.ZERO)
                }
            }
    }

    // ===== 전체 요약 통계 테스트 =====

    @Test // 전체 요약 통계 조회 테스트
    @Order(5)
    fun `요약 통계 - 전체 시스템 현황을 반환한다`() {
        // V7 샘플: 예약 3건 (확정 2, 취소 1), 이벤트 5건, 활성 채널 3개
        webTestClient.get()
            .uri("/api/statistics/summary")
            .exchange()
            .expectStatus().isOk
            .expectBody(SummaryStatistics::class.java)
            .consumeWith { result ->
                val summary = result.responseBody!!
                // 전체 예약 수가 1 이상 (V7 샘플 데이터 존재)
                assertThat(summary.totalReservations).isGreaterThanOrEqualTo(1)
                // 확정 + 취소 = 전체
                assertThat(summary.confirmedCount + summary.cancelledCount)
                    .isEqualTo(summary.totalReservations)
                // 매출이 0 이상
                assertThat(summary.totalRevenue).isGreaterThanOrEqualTo(BigDecimal.ZERO)
                // 이벤트가 1개 이상
                assertThat(summary.totalEvents).isGreaterThanOrEqualTo(1)
                // 활성 채널이 1개 이상
                assertThat(summary.activeChannels).isGreaterThanOrEqualTo(1)
            }
    }

    @Test // 요약 통계 — 확정 예약만 매출에 포함되는지 확인
    @Order(6)
    fun `요약 통계 - 확정 예약이 있으면 매출이 0보다 크다`() {
        webTestClient.get()
            .uri("/api/statistics/summary")
            .exchange()
            .expectStatus().isOk
            .expectBody(SummaryStatistics::class.java)
            .consumeWith { result ->
                val summary = result.responseBody!!
                // V7 샘플에 확정 예약이 있으므로 매출이 0보다 커야 한다
                if (summary.confirmedCount > 0) {
                    assertThat(summary.totalRevenue).isGreaterThan(BigDecimal.ZERO)
                }
            }
    }
}
