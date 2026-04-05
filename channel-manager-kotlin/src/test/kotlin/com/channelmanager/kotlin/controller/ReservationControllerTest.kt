package com.channelmanager.kotlin.controller // 컨트롤러 테스트 패키지

import com.channelmanager.kotlin.domain.Inventory // 재고 엔티티
import com.channelmanager.kotlin.domain.ReservationStatus // 예약 상태 enum
import com.channelmanager.kotlin.dto.ReservationCreateRequest // 예약 생성 요청 DTO
import com.channelmanager.kotlin.dto.ReservationResponse // 예약 응답 DTO
import com.channelmanager.kotlin.repository.ChannelEventRepository // 이벤트 리포지토리 (정리용)
import com.channelmanager.kotlin.repository.InventoryRepository // 재고 리포지토리 (테스트 데이터 준비용)
import com.channelmanager.kotlin.repository.ReservationRepository // 예약 리포지토리 (정리용)
import reactor.core.publisher.Mono // 0~1개 비동기 스트림 (테스트 정리용)
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
import org.springframework.http.MediaType // HTTP 미디어 타입
import org.springframework.test.web.reactive.server.WebTestClient // WebFlux 테스트용 HTTP 클라이언트
import java.time.LocalDate // 날짜 타입

// 예약 컨트롤러 통합 테스트
// 실제 서버를 랜덤 포트로 기동하여 예약 API를 테스트한다
// 테스트 전에 재고 데이터를 준비하고, 테스트 후에 생성된 예약/이벤트/재고를 정리한다
// @TestInstance(PER_CLASS): @BeforeAll/@AfterAll에서 non-static 메서드 사용 가능
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationControllerTest {

    @LocalServerPort // Spring이 실제 기동된 서버의 랜덤 포트 번호를 주입한다
    private var port: Int = 0

    @Autowired // 테스트 데이터 준비/정리를 위해 리포지토리를 주입한다
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var channelEventRepository: ChannelEventRepository

    // WebTestClient를 직접 생성한다 (Spring Boot 4.x 패턴)
    private lateinit var webTestClient: WebTestClient

    // 테스트 중 생성된 예약 ID를 추적하여 정리한다
    private val createdReservationIds = mutableListOf<Long>()
    // 테스트 중 생성된 재고 ID를 추적하여 정리한다
    private val createdInventoryIds = mutableListOf<Long>()

    // 테스트에서 사용할 날짜 상수 — 12월 2026년, 샘플 데이터(3월)와 겹치지 않는 먼 미래 날짜
    companion object {
        val TEST_CHECK_IN: LocalDate = LocalDate.of(2026, 12, 1)      // 체크인 날짜
        val TEST_CHECK_OUT: LocalDate = LocalDate.of(2026, 12, 3)     // 체크아웃 날짜 (2박)
        const val TEST_ROOM_TYPE_ID = 1L                                // 테스트용 객실 타입 ID
    }

    @BeforeAll // 모든 테스트 시작 전 — 테스트용 재고 데이터를 준비한다
    fun setupTestData() {
        // 이전 테스트 실행에서 남은 잔여 데이터를 정리한다
        cleanupPreviousData()

        // 체크인~체크아웃 기간(12/1, 12/2)의 재고를 생성한다
        // 예약 테스트에서 이 재고를 차감하게 된다
        val dates = TEST_CHECK_IN.datesUntil(TEST_CHECK_OUT).toList() // [12/1, 12/2]
        dates.forEach { date ->
            val saved = inventoryRepository.save(
                Inventory(
                    roomTypeId = TEST_ROOM_TYPE_ID,  // 객실 타입 ID
                    stockDate = date,                 // 재고 날짜
                    totalQuantity = 10,               // 전체 10실
                    availableQuantity = 10            // 가용 10실
                )
            ).block()!! // 테스트 준비에서만 block() 사용 허용
            createdInventoryIds.add(saved.id!!) // 정리 대상에 추가
        }
    }

    @BeforeEach // 각 테스트 실행 전에 WebTestClient를 초기화한다
    fun setUp() {
        webTestClient = WebTestClient.bindToServer() // 실제 서버에 바인딩
            .baseUrl("http://localhost:$port") // 랜덤 포트로 기본 URL 설정
            .build() // WebTestClient 생성
    }

    @AfterAll // 모든 테스트 완료 후 생성된 데이터를 정리한다
    fun cleanupAfter() {
        // FK 의존성 순서대로 삭제: 이벤트 → 예약 → 재고
        // 예약이 참조하는 이벤트를 먼저 삭제해야 예약 삭제가 가능하다
        cleanupReservationRelatedData()
        // 생성된 재고 삭제
        createdInventoryIds.forEach { id ->
            inventoryRepository.deleteById(id).block()
        }
    }

    // 이전 테스트 실행에서 남은 잔여 데이터를 정리하는 헬퍼 메서드
    // FK 의존성 순서대로 삭제한다: 이벤트 → 예약 → 재고
    // 테스트 날짜 범위(12월 2026년)에 해당하는 데이터만 삭제하여 V7 샘플 데이터를 보존한다
    private fun cleanupPreviousData() {
        // 1. 테스트 날짜 범위의 예약만 조회한다 (V7 샘플 데이터 보존)
        val testStartDate = LocalDate.of(2026, 12, 1) // 테스트 시작 날짜
        val testEndDate = LocalDate.of(2026, 12, 31) // 테스트 종료 날짜
        val previousReservations = reservationRepository.findByRoomTypeId(TEST_ROOM_TYPE_ID)
            .filter { reservation -> // 12월 2026년 범위의 예약만 필터
                !reservation.checkInDate.isBefore(testStartDate) &&
                    !reservation.checkInDate.isAfter(testEndDate)
            }
            .collectList().block() ?: emptyList()

        // 2. 각 예약에 연결된 이벤트를 먼저 삭제한다 (FK 제약조건)
        previousReservations.forEach { reservation ->
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter { it.reservationId == reservation.id }
                .flatMap { channelEventRepository.deleteById(it.id!!) }
                .collectList().block()
        }

        // 3. 테스트 범위의 예약만 삭제
        previousReservations.forEach { reservation ->
            reservationRepository.deleteById(reservation.id!!).block()
        }

        // 4. 12월 2026년 날짜 범위의 기존 재고를 삭제한다
        inventoryRepository
            .findByRoomTypeIdAndStockDateBetween(
                TEST_ROOM_TYPE_ID, testStartDate, testEndDate
            )
            .flatMap { inventoryRepository.deleteById(it.id!!) }
            .collectList().block()
    }

    // 예약 관련 데이터 정리 — FK 순서: 이벤트 → 예약
    private fun cleanupReservationRelatedData() {
        // 1. 예약에 연결된 이벤트를 먼저 삭제 (FK 제약조건)
        createdReservationIds.forEach { reservationId ->
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter { it.reservationId == reservationId }
                .flatMap { channelEventRepository.deleteById(it.id!!) }
                .collectList().block()
        }
        // 2. 예약 삭제
        createdReservationIds.forEach { id ->
            reservationRepository.deleteById(id).block()
        }
    }

    // ===== 예약 생성 성공 테스트 =====

    @Test // 정상적인 예약 생성 테스트
    @Order(1) // 첫 번째로 실행 — 재고가 충분한 상태에서 예약
    fun `예약 생성 - 유효한 요청이면 201 Created를 반환한다`() {
        val request = ReservationCreateRequest(
            channelCode = "BOOKING",                    // Booking.com 채널
            roomTypeId = TEST_ROOM_TYPE_ID,             // 테스트용 객실 타입
            checkInDate = TEST_CHECK_IN,                // 12/1 체크인
            checkOutDate = TEST_CHECK_OUT,              // 12/3 체크아웃 (2박)
            guestName = "테스트 투숙객",                 // 투숙객 이름
            roomQuantity = 1                            // 1실
        )

        webTestClient.post() // POST 요청 생성
            .uri("/api/reservations") // 예약 API 엔드포인트
            .contentType(MediaType.APPLICATION_JSON) // Content-Type: application/json
            .bodyValue(request) // 요청 본문에 DTO를 JSON으로 직렬화
            .exchange() // 요청 실행
            .expectStatus().isCreated // HTTP 201 Created 확인
            .expectBody(ReservationResponse::class.java) // 응답 본문 검증
            .consumeWith { result ->
                val response = result.responseBody!!
                assertThat(response.channelCode).isEqualTo("BOOKING") // 채널 코드 확인
                assertThat(response.roomTypeId).isEqualTo(TEST_ROOM_TYPE_ID) // 객실 타입 확인
                assertThat(response.checkInDate).isEqualTo(TEST_CHECK_IN) // 체크인 날짜 확인
                assertThat(response.checkOutDate).isEqualTo(TEST_CHECK_OUT) // 체크아웃 날짜 확인
                assertThat(response.guestName).isEqualTo("테스트 투숙객") // 투숙객 이름 확인
                assertThat(response.status).isEqualTo(ReservationStatus.CONFIRMED) // 예약 확정 상태
                assertThat(response.totalPrice).isNotNull // 총 금액이 계산되었는지 확인
                assertThat(response.totalPrice!!.signum()).isPositive // 금액이 양수인지 확인
                createdReservationIds.add(response.id) // 정리 대상에 추가
            }
    }

    @Test // 예약 생성 후 재고가 차감되었는지 확인하는 테스트
    @Order(2) // 예약 생성 이후에 실행
    fun `예약 생성 후 - 재고가 차감되었는지 확인한다`() {
        // 12/1 재고를 조회하여 가용 수량이 차감되었는지 확인한다
        // Order(1) 테스트에서 1실을 예약했으므로 10 → 9로 차감되어야 한다
        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/inventories")
                    .queryParam("roomTypeId", TEST_ROOM_TYPE_ID)
                    .queryParam("startDate", TEST_CHECK_IN.toString())
                    .queryParam("endDate", TEST_CHECK_IN.toString()) // 하루만 조회
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBodyList(com.channelmanager.kotlin.dto.InventoryResponse::class.java)
            .hasSize(1)
            .consumeWith<WebTestClient.ListBodySpec<com.channelmanager.kotlin.dto.InventoryResponse>> {
                result ->
                val inventory = result.responseBody!!.first()
                assertThat(inventory.availableQuantity).isEqualTo(9) // 10 - 1 = 9
            }
    }

    // ===== 예약 생성 실패 테스트 =====

    @Test // 존재하지 않는 채널 코드로 예약 시 404 에러
    @Order(3)
    fun `예약 생성 - 존재하지 않는 채널이면 404를 반환한다`() {
        val request = ReservationCreateRequest(
            channelCode = "NONEXISTENT",                // 존재하지 않는 채널 코드
            roomTypeId = TEST_ROOM_TYPE_ID,
            checkInDate = TEST_CHECK_IN,
            checkOutDate = TEST_CHECK_OUT,
            guestName = "테스트 투숙객",
            roomQuantity = 1
        )

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNotFound // 404 Not Found 확인
    }

    @Test // 비활성 채널로 예약 시 400 에러
    @Order(4)
    fun `예약 생성 - 비활성 채널이면 400을 반환한다`() {
        val request = ReservationCreateRequest(
            channelCode = "TRIP",                       // Trip.com (V7에서 비활성 설정)
            roomTypeId = TEST_ROOM_TYPE_ID,
            checkInDate = TEST_CHECK_IN,
            checkOutDate = TEST_CHECK_OUT,
            guestName = "테스트 투숙객",
            roomQuantity = 1
        )

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest // 400 Bad Request 확인
    }

    @Test // 존재하지 않는 객실 타입으로 예약 시 404 에러
    @Order(5)
    fun `예약 생성 - 존재하지 않는 객실 타입이면 404를 반환한다`() {
        val request = ReservationCreateRequest(
            channelCode = "BOOKING",
            roomTypeId = 99999L,                        // 존재하지 않는 객실 타입 ID
            checkInDate = TEST_CHECK_IN,
            checkOutDate = TEST_CHECK_OUT,
            guestName = "테스트 투숙객",
            roomQuantity = 1
        )

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNotFound // 404 Not Found 확인
    }

    @Test // 체크인 >= 체크아웃이면 400 에러
    @Order(6)
    fun `예약 생성 - 체크인이 체크아웃 이후이면 400을 반환한다`() {
        val request = ReservationCreateRequest(
            channelCode = "BOOKING",
            roomTypeId = TEST_ROOM_TYPE_ID,
            checkInDate = LocalDate.of(2026, 12, 5),    // 체크인이 체크아웃 이후
            checkOutDate = LocalDate.of(2026, 12, 3),   // 체크아웃이 체크인 이전
            guestName = "테스트 투숙객",
            roomQuantity = 1
        )

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest // 400 Bad Request 확인
    }

    @Test // 재고가 없는 날짜로 예약 시 404 에러
    @Order(7)
    fun `예약 생성 - 재고가 없는 날짜이면 404를 반환한다`() {
        val request = ReservationCreateRequest(
            channelCode = "BOOKING",
            roomTypeId = TEST_ROOM_TYPE_ID,
            checkInDate = LocalDate.of(2026, 12, 20),   // 재고가 준비되지 않은 날짜
            checkOutDate = LocalDate.of(2026, 12, 22),
            guestName = "테스트 투숙객",
            roomQuantity = 1
        )

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNotFound // 404 Not Found 확인 (재고 레코드 없음)
    }

    // ===== 시뮬레이터 제어 테스트 =====

    @Test // 시뮬레이터 시작 테스트
    @Order(8)
    fun `시뮬레이터 시작 - POST 요청으로 시뮬레이터를 시작한다`() {
        webTestClient.post()
            .uri("/api/simulator/start") // 시뮬레이터 시작 엔드포인트
            .exchange()
            .expectStatus().isOk // 200 OK 확인
            .expectBody() // 응답 본문 검증
            .jsonPath("$.running").isEqualTo(true) // 실행 상태가 true인지 확인
    }

    @Test // 시뮬레이터 상태 조회 테스트
    @Order(9) // 시작 이후에 실행
    fun `시뮬레이터 상태 - 시작 후 실행 중 상태를 반환한다`() {
        // Order(8)에서 시작했으므로 running=true여야 한다
        webTestClient.get()
            .uri("/api/simulator/status") // 시뮬레이터 상태 엔드포인트
            .exchange()
            .expectStatus().isOk // 200 OK 확인
            .expectBody()
            .jsonPath("$.running").isEqualTo(true) // 실행 중 확인
    }

    @Test // 시뮬레이터 중지 테스트
    @Order(10)
    fun `시뮬레이터 중지 - POST 요청으로 시뮬레이터를 중지한다`() {
        webTestClient.post()
            .uri("/api/simulator/stop") // 시뮬레이터 중지 엔드포인트
            .exchange()
            .expectStatus().isOk // 200 OK 확인
            .expectBody()
            .jsonPath("$.running").isEqualTo(false) // 중지 상태 확인
    }

    @Test // 중지 후 상태 확인 테스트
    @Order(11)
    fun `시뮬레이터 상태 - 중지 후 실행 중이 아닌 상태를 반환한다`() {
        webTestClient.get()
            .uri("/api/simulator/status")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.running").isEqualTo(false) // 중지 확인
    }

    // ===== 예약 취소 테스트 (Phase 7) =====

    @Test // 예약 취소 성공 테스트
    @Order(12)
    fun `예약 취소 - CONFIRMED 예약을 취소하면 CANCELLED 상태를 반환한다`() {
        // 먼저 취소할 예약을 생성한다
        val request = ReservationCreateRequest(
            channelCode = "BOOKING",
            roomTypeId = TEST_ROOM_TYPE_ID,
            checkInDate = TEST_CHECK_IN,
            checkOutDate = TEST_CHECK_OUT,
            guestName = "취소 테스트 투숙객",
            roomQuantity = 1
        )

        // 예약 생성
        val createResult = webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody(ReservationResponse::class.java)
            .returnResult()
            .responseBody!!
        createdReservationIds.add(createResult.id)

        // 예약 취소 — DELETE /api/reservations/{id}
        webTestClient.delete()
            .uri("/api/reservations/${createResult.id}")
            .exchange()
            .expectStatus().isOk // 200 OK 확인
            .expectBody(ReservationResponse::class.java)
            .consumeWith { result ->
                val response = result.responseBody!!
                assertThat(response.id).isEqualTo(createResult.id) // 같은 예약 ID
                assertThat(response.status).isEqualTo(ReservationStatus.CANCELLED) // 취소 상태
                assertThat(response.guestName).isEqualTo("취소 테스트 투숙객") // 투숙객 이름 유지
            }
    }

    @Test // 예약 취소 후 재고 복구 확인 테스트
    @Order(13)
    fun `예약 취소 후 - 재고가 복구되었는지 확인한다`() {
        // Order(1)에서 1실 예약, Order(12)에서 1실 예약 후 취소했으므로
        // 재고는 Order(1)의 차감만 남아 9여야 한다
        webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/inventories")
                    .queryParam("roomTypeId", TEST_ROOM_TYPE_ID)
                    .queryParam("startDate", TEST_CHECK_IN.toString())
                    .queryParam("endDate", TEST_CHECK_IN.toString())
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBodyList(com.channelmanager.kotlin.dto.InventoryResponse::class.java)
            .hasSize(1)
            .consumeWith<WebTestClient.ListBodySpec<com.channelmanager.kotlin.dto.InventoryResponse>> {
                result ->
                val inventory = result.responseBody!!.first()
                // Order(1)에서 1실 차감(10→9), Order(12)에서 1실 차감 후 취소(9→8→9)
                assertThat(inventory.availableQuantity).isEqualTo(9) // 취소 후 복구 확인
            }
    }

    @Test // 이미 취소된 예약 재취소 시 400 에러
    @Order(14)
    fun `예약 취소 - 이미 취소된 예약이면 400을 반환한다`() {
        // Order(12)에서 취소한 예약을 다시 취소 시도한다
        // createdReservationIds의 마지막 항목이 Order(12)에서 생성된 예약
        val cancelledId = createdReservationIds.last()

        webTestClient.delete()
            .uri("/api/reservations/$cancelledId")
            .exchange()
            .expectStatus().isBadRequest // 400 Bad Request 확인
    }

    @Test // 존재하지 않는 예약 취소 시 404 에러
    @Order(15)
    fun `예약 취소 - 존재하지 않는 예약이면 404를 반환한다`() {
        webTestClient.delete()
            .uri("/api/reservations/99999") // 존재하지 않는 예약 ID
            .exchange()
            .expectStatus().isNotFound // 404 Not Found 확인
    }

    // ===== 예약 조회 테스트 (Phase 9) =====

    @Test // 예약 단건 조회 테스트
    @Order(16)
    fun `예약 조회 - ID로 단건 예약을 조회한다`() {
        // Order(1)에서 생성한 첫 번째 예약을 조회한다
        val reservationId = createdReservationIds.first()

        webTestClient.get()
            .uri("/api/reservations/$reservationId")
            .exchange()
            .expectStatus().isOk
            .expectBody(ReservationResponse::class.java)
            .consumeWith { result ->
                val response = result.responseBody!!
                assertThat(response.id).isEqualTo(reservationId)
                assertThat(response.channelCode).isNotBlank
                assertThat(response.guestName).isEqualTo("테스트 투숙객")
            }
    }

    @Test // 존재하지 않는 예약 조회 시 404
    @Order(17)
    fun `예약 조회 - 존재하지 않는 예약이면 404를 반환한다`() {
        webTestClient.get()
            .uri("/api/reservations/99999")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test // 예약 목록 전체 조회
    @Order(18)
    fun `예약 목록 - 필터 없이 전체 예약을 반환한다`() {
        webTestClient.get()
            .uri("/api/reservations")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(ReservationResponse::class.java)
            .consumeWith<WebTestClient.ListBodySpec<ReservationResponse>> { result ->
                val reservations = result.responseBody!!
                // V7 샘플(3건) + 테스트에서 생성한 예약이 포함되어야 한다
                assertThat(reservations).isNotEmpty
                // 각 예약에 channelCode가 포함되어 있는지 확인
                reservations.forEach { r ->
                    assertThat(r.channelCode).isNotBlank
                }
            }
    }

    @Test // 상태 필터링 테스트
    @Order(19)
    fun `예약 목록 - status 필터로 확정 예약만 조회한다`() {
        webTestClient.get()
            .uri("/api/reservations?status=CONFIRMED")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(ReservationResponse::class.java)
            .consumeWith<WebTestClient.ListBodySpec<ReservationResponse>> { result ->
                val reservations = result.responseBody!!
                // 모든 예약이 CONFIRMED 상태여야 한다
                reservations.forEach { r ->
                    assertThat(r.status).isEqualTo(ReservationStatus.CONFIRMED)
                }
            }
    }

    @Test // 페이징 테스트
    @Order(20)
    fun `예약 목록 - page와 size로 페이징한다`() {
        webTestClient.get()
            .uri("/api/reservations?page=0&size=2")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(ReservationResponse::class.java)
            .consumeWith<WebTestClient.ListBodySpec<ReservationResponse>> { result ->
                val reservations = result.responseBody!!
                // size=2이므로 최대 2개만 반환
                assertThat(reservations.size).isLessThanOrEqualTo(2)
            }
    }
}
