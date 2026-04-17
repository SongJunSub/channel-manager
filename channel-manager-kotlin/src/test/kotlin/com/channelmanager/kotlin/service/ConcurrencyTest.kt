package com.channelmanager.kotlin.service // 서비스 테스트 패키지

import com.channelmanager.kotlin.config.TestcontainersConfig // Phase 13: Testcontainers 설정
import com.channelmanager.kotlin.config.TestSecurityConfig // Phase 21: 테스트 보안 설정
import com.channelmanager.kotlin.domain.Inventory // 재고 엔티티
import com.channelmanager.kotlin.domain.ReservationStatus // 예약 상태 enum
import com.channelmanager.kotlin.dto.ReservationCreateRequest // 예약 생성 요청 DTO
import com.channelmanager.kotlin.dto.ReservationResponse // 예약 응답 DTO
import com.channelmanager.kotlin.repository.ChannelEventRepository // 이벤트 리포지토리
import com.channelmanager.kotlin.repository.InventoryRepository // 재고 리포지토리
import com.channelmanager.kotlin.repository.ReservationRepository // 예약 리포지토리
import org.assertj.core.api.Assertions.assertThat // AssertJ 검증 메서드
import org.junit.jupiter.api.AfterAll // 모든 테스트 완료 후 실행
import org.junit.jupiter.api.BeforeAll // 모든 테스트 시작 전 실행
import org.junit.jupiter.api.BeforeEach // 각 테스트 전 실행
import org.junit.jupiter.api.MethodOrderer // 테스트 메서드 실행 순서 제어
import org.junit.jupiter.api.Order // 테스트 실행 순서 지정
import org.junit.jupiter.api.Test // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance // 테스트 인스턴스 생명주기 설정
import org.junit.jupiter.api.TestMethodOrder // 테스트 메서드 정렬 전략
import org.springframework.beans.factory.annotation.Autowired // 의존성 주입
import org.springframework.boot.test.context.SpringBootTest // 전체 애플리케이션 컨텍스트 로드
import org.springframework.boot.test.web.server.LocalServerPort // 랜덤 포트 주입
import org.springframework.context.annotation.Import // 테스트 설정 임포트
import org.springframework.http.MediaType // HTTP 미디어 타입
import org.springframework.test.web.reactive.server.WebTestClient // WebFlux 테스트용 HTTP 클라이언트
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.time.Duration // 시간 간격
import java.time.LocalDate // 날짜 타입

// 동시성 테스트 — Phase 4의 FOR UPDATE 비관적 잠금이 동시 예약에서 재고 정합성을 보장하는지 검증
// 여러 예약 요청을 동시에 발행하고, 최종 재고가 정확한지 확인한다
// Flux.merge()로 동시 HTTP 요청을 발행하여 실제 동시성 환경을 시뮬레이션한다
@Import(TestcontainersConfig::class, TestSecurityConfig::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var channelEventRepository: ChannelEventRepository

    private lateinit var webTestClient: WebTestClient

    // 동시성 테스트 전용 날짜 — 다른 테스트와 겹치지 않는 2027년 1월
    companion object {
        val CONCURRENCY_DATE: LocalDate = LocalDate.of(2027, 1, 10) // 체크인
        val CONCURRENCY_DATE_OUT: LocalDate = LocalDate.of(2027, 1, 11) // 체크아웃 (1박)
        const val TEST_ROOM_TYPE_ID = 1L // Superior Double
        const val INITIAL_QUANTITY = 10 // 초기 재고
        const val CONCURRENCY = 10 // 동시 요청 수
    }

    // 동시성 테스트 전용 재고 ID 추적
    private val createdInventoryIds = mutableListOf<Long>()
    private val createdReservationIds = mutableListOf<Long>()

    @BeforeAll // 동시성 테스트용 재고 데이터 준비
    fun setupTestData() {
        // 기존 테스트 데이터 정리
        cleanupPreviousData()

        // 1박용 재고 생성 (10실)
        val saved = inventoryRepository.save(
            Inventory(
                roomTypeId = TEST_ROOM_TYPE_ID,
                stockDate = CONCURRENCY_DATE,
                totalQuantity = INITIAL_QUANTITY,
                availableQuantity = INITIAL_QUANTITY
            )
        ).block()!!
        createdInventoryIds.add(saved.id!!)
    }

    @BeforeEach
    fun setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(30)) // 동시 요청은 처리 시간이 길 수 있다
            .build()
    }

    @AfterAll // 동시성 테스트 데이터 정리
    fun cleanupAfter() {
        // FK 순서: 이벤트 → 예약 → 재고
        createdReservationIds.forEach { reservationId ->
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter { it.reservationId == reservationId }
                .flatMap { channelEventRepository.deleteById(it.id!!) }
                .collectList().block()
        }
        createdReservationIds.forEach { id ->
            reservationRepository.deleteById(id).block()
        }
        createdInventoryIds.forEach { id ->
            inventoryRepository.deleteById(id).block()
        }
    }

    // ===== 동시 예약 테스트 =====

    @Test // 동시 10건 예약 — 재고 정합성 검증
    @Order(1)
    fun `동시 예약 - 10건 동시 요청 시 재고가 정확하게 차감된다`() {
        // 10개의 동시 예약 요청을 생성한다
        // Flux.merge: 여러 Mono를 동시에 실행한다 (병렬 처리)
        // 각 요청은 서로 다른 게스트 이름으로 구분한다
        val requests = (1..CONCURRENCY).map { i ->
            webTestClient.post()
                .uri("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    ReservationCreateRequest(
                        channelCode = "BOOKING",
                        roomTypeId = TEST_ROOM_TYPE_ID,
                        checkInDate = CONCURRENCY_DATE,
                        checkOutDate = CONCURRENCY_DATE_OUT,
                        guestName = "동시테스트$i",
                        roomQuantity = 1
                    )
                )
                .exchange()
                .returnResult(ReservationResponse::class.java)
                .responseBody
                .next() // 첫 번째 응답만 추출
                .map { response ->
                    createdReservationIds.add(response.id)
                    "SUCCESS" // 성공
                }
                .onErrorResume { Mono.just("FAIL") } // 실패해도 계속
        }

        // Flux.merge로 모든 요청을 동시에 실행하고 결과를 수집한다
        val results = Flux.merge(requests)
            .collectList()
            .block(Duration.ofSeconds(30))!!

        // 결과 검증
        val successCount = results.count { it == "SUCCESS" }
        val failCount = results.count { it == "FAIL" }

        // 총 10건 요청 = 성공 + 실패
        assertThat(successCount + failCount).isEqualTo(CONCURRENCY)

        // 재고가 10실이므로 10건 모두 성공 가능
        assertThat(successCount).isEqualTo(INITIAL_QUANTITY)

        // 재고 정합성 확인 — 최종 가용 수량 = 초기 - 성공 건수
        val inventory = inventoryRepository.findByRoomTypeIdAndStockDate(
            TEST_ROOM_TYPE_ID, CONCURRENCY_DATE
        ).block()!!
        assertThat(inventory.availableQuantity).isEqualTo(INITIAL_QUANTITY - successCount)
    }

    @Test // 재고 부족 시 동시 예약 — 초과 예약 방지 검증
    @Order(2)
    fun `동시 예약 - 재고 부족 시 초과 예약이 발생하지 않는다`() {
        // Order(1)에서 10실 모두 소진되었으므로 추가 예약은 실패해야 한다
        val additionalRequests = (1..5).map { i ->
            // WebClient를 사용하여 상태 코드로 성공/실패를 판단한다
            // WebTestClient의 returnResult는 4xx 에러도 본문을 반환하므로,
            // WebClient의 retrieve()를 사용하여 4xx 시 에러를 발생시킨다
            org.springframework.web.reactive.function.client.WebClient.create("http://localhost:$port")
                .post()
                .uri("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    ReservationCreateRequest(
                        channelCode = "DIRECT",
                        roomTypeId = TEST_ROOM_TYPE_ID,
                        checkInDate = CONCURRENCY_DATE,
                        checkOutDate = CONCURRENCY_DATE_OUT,
                        guestName = "초과테스트$i",
                        roomQuantity = 1
                    )
                )
                .retrieve() // 4xx/5xx 시 에러 발생
                .bodyToMono(String::class.java)
                .map { "SUCCESS" }
                .onErrorResume { Mono.just("FAIL") }
        }

        val results = Flux.merge(additionalRequests)
            .collectList()
            .block(Duration.ofSeconds(30))!!

        // 재고가 0이므로 모두 실패해야 한다
        val successCount = results.count { it == "SUCCESS" }
        assertThat(successCount).isEqualTo(0)

        // 재고가 여전히 0인지 확인 (오버부킹 방지)
        val inventory = inventoryRepository.findByRoomTypeIdAndStockDate(
            TEST_ROOM_TYPE_ID, CONCURRENCY_DATE
        ).block()!!
        assertThat(inventory.availableQuantity).isEqualTo(0)
    }

    // 이전 실행 잔여 데이터 정리
    private fun cleanupPreviousData() {
        val startDate = LocalDate.of(2027, 1, 1)
        val endDate = LocalDate.of(2027, 1, 31)

        // 예약 관련 이벤트 + 예약 삭제
        val previousReservations = reservationRepository.findByRoomTypeId(TEST_ROOM_TYPE_ID)
            .filter { !it.checkInDate.isBefore(startDate) && !it.checkInDate.isAfter(endDate) }
            .collectList().block() ?: emptyList()

        previousReservations.forEach { reservation ->
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter { it.reservationId == reservation.id }
                .flatMap { channelEventRepository.deleteById(it.id!!) }
                .collectList().block()
        }
        previousReservations.forEach { reservationRepository.deleteById(it.id!!).block() }

        // 재고 삭제
        inventoryRepository.findByRoomTypeIdAndStockDateBetween(
            TEST_ROOM_TYPE_ID, startDate, endDate
        ).flatMap { inventoryRepository.deleteById(it.id!!) }.collectList().block()
    }
}
