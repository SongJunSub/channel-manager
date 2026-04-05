package com.channelmanager.java.controller; // 컨트롤러 테스트 패키지

import com.channelmanager.java.domain.Inventory; // 재고 엔티티
import com.channelmanager.java.domain.ReservationStatus; // 예약 상태 enum
import com.channelmanager.java.dto.InventoryResponse; // 재고 응답 DTO (재고 차감 확인용)
import com.channelmanager.java.dto.ReservationCreateRequest; // 예약 생성 요청 DTO
import com.channelmanager.java.dto.ReservationResponse; // 예약 응답 DTO
import com.channelmanager.java.repository.ChannelEventRepository; // 이벤트 리포지토리 (정리용)
import com.channelmanager.java.repository.InventoryRepository; // 재고 리포지토리 (테스트 데이터 준비용)
import com.channelmanager.java.repository.ReservationRepository; // 예약 리포지토리 (정리용)
import org.junit.jupiter.api.AfterAll; // 모든 테스트 완료 후 실행
import org.junit.jupiter.api.BeforeAll; // 모든 테스트 시작 전 실행
import org.junit.jupiter.api.BeforeEach; // 각 테스트 전 실행
import org.junit.jupiter.api.MethodOrderer; // 테스트 메서드 실행 순서 제어
import org.junit.jupiter.api.Order; // 테스트 실행 순서 지정
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance; // 테스트 인스턴스 생명주기 설정
import org.junit.jupiter.api.TestMethodOrder; // 테스트 메서드 정렬 전략
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.java.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort; // 랜덤 포트 주입
import org.springframework.http.MediaType; // HTTP 미디어 타입
import org.springframework.test.web.reactive.server.WebTestClient; // WebFlux 테스트용 HTTP 클라이언트
import java.time.LocalDate; // 날짜 타입
import java.util.ArrayList; // 가변 리스트
import java.util.List; // 리스트 인터페이스

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 정적 import

// 예약 컨트롤러 통합 테스트
// 실제 서버를 랜덤 포트로 기동하여 예약 API를 테스트한다
// 테스트 전에 재고 데이터를 준비하고, 테스트 후에 생성된 예약/이벤트/재고를 정리한다
// @TestInstance(PER_CLASS): @BeforeAll/@AfterAll에서 인스턴스 필드 접근 가능
// Kotlin과 동일한 테스트 구조이지만, Java에서는 Lombok 없이 명시적으로 선언한다
@Import(TestcontainersConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationControllerTest {

    @LocalServerPort // Spring이 실제 기동된 서버의 랜덤 포트 번호를 주입한다
    private int port;

    @Autowired // 테스트 데이터 준비/정리를 위해 리포지토리를 주입한다
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ChannelEventRepository channelEventRepository;

    // WebTestClient를 직접 생성한다 (Spring Boot 4.x 패턴)
    private WebTestClient webTestClient;

    // 테스트 중 생성된 ID를 추적하여 정리한다
    // Kotlin에서는 mutableListOf<Long>()을 사용하지만, Java에서는 new ArrayList<>()를 사용한다
    private final List<Long> createdReservationIds = new ArrayList<>();
    private final List<Long> createdInventoryIds = new ArrayList<>();

    // 테스트에서 사용할 날짜 상수 — 12월 2026년, 샘플 데이터(3월)와 겹치지 않는 먼 미래 날짜
    // Kotlin에서는 companion object에 val로 선언하지만, Java에서는 static final로 선언한다
    private static final LocalDate TEST_CHECK_IN = LocalDate.of(2026, 12, 1);
    private static final LocalDate TEST_CHECK_OUT = LocalDate.of(2026, 12, 3);
    private static final long TEST_ROOM_TYPE_ID = 1L;

    @BeforeAll // 모든 테스트 시작 전 — 테스트용 재고 데이터를 준비한다
    void setupTestData() {
        // 이전 테스트 실행에서 남은 잔여 데이터를 정리한다
        cleanupPreviousData();

        // 체크인~체크아웃 기간(12/1, 12/2)의 재고를 생성한다
        // Kotlin에서는 datesUntil().toList()로 간결하게 표현하지만,
        // Java에서는 Stream을 List로 변환한다
        List<LocalDate> dates = TEST_CHECK_IN.datesUntil(TEST_CHECK_OUT).toList();
        for (LocalDate date : dates) {
            Inventory saved = inventoryRepository.save(
                Inventory.builder()
                    .roomTypeId(TEST_ROOM_TYPE_ID)  // 객실 타입 ID
                    .stockDate(date)                 // 재고 날짜
                    .totalQuantity(10)               // 전체 10실
                    .availableQuantity(10)           // 가용 10실
                    .build()
            ).block(); // 테스트 준비에서만 block() 사용 허용
            createdInventoryIds.add(saved.getId()); // 정리 대상에 추가
        }
    }

    @BeforeEach // 각 테스트 실행 전에 WebTestClient를 초기화한다
    void setUp() {
        webTestClient = WebTestClient.bindToServer() // 실제 서버에 바인딩
            .baseUrl("http://localhost:" + port) // 랜덤 포트로 기본 URL 설정
            .build();
    }

    @AfterAll // 모든 테스트 완료 후 생성된 데이터를 정리한다
    void cleanupAfter() {
        // FK 의존성 순서대로 삭제: 이벤트 → 예약 → 재고
        cleanupReservationRelatedData();
        // 생성된 재고 삭제
        for (Long id : createdInventoryIds) {
            inventoryRepository.deleteById(id).block();
        }
    }

    // 이전 테스트 실행에서 남은 잔여 데이터를 정리하는 헬퍼 메서드
    // FK 의존성 순서대로 삭제한다: 이벤트 → 예약 → 재고
    // 테스트 날짜 범위(12월 2026년)에 해당하는 데이터만 삭제하여 V7 샘플 데이터를 보존한다
    // Kotlin에서는 filter { ... }와 collectList().block()을 사용하지만,
    // Java에서는 filter(reservation -> ...)를 사용한다
    private void cleanupPreviousData() {
        LocalDate testStartDate = LocalDate.of(2026, 12, 1);
        LocalDate testEndDate = LocalDate.of(2026, 12, 31);

        // 1. 테스트 날짜 범위의 예약만 조회한다 (V7 샘플 데이터 보존)
        List<com.channelmanager.java.domain.Reservation> previousReservations =
            reservationRepository.findByRoomTypeId(TEST_ROOM_TYPE_ID)
                .filter(reservation ->
                    !reservation.getCheckInDate().isBefore(testStartDate) &&
                    !reservation.getCheckInDate().isAfter(testEndDate)
                )
                .collectList().block();
        if (previousReservations == null) previousReservations = List.of();

        // 2. 각 예약에 연결된 이벤트를 먼저 삭제한다 (FK 제약조건)
        for (var reservation : previousReservations) {
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter(event ->
                    reservation.getId().equals(event.getReservationId())
                )
                .flatMap(event -> channelEventRepository.deleteById(event.getId()))
                .collectList().block();
        }

        // 3. 테스트 범위의 예약만 삭제
        for (var reservation : previousReservations) {
            reservationRepository.deleteById(reservation.getId()).block();
        }

        // 4. 12월 2026년 날짜 범위의 기존 재고를 삭제한다
        inventoryRepository
            .findByRoomTypeIdAndStockDateBetween(TEST_ROOM_TYPE_ID, testStartDate, testEndDate)
            .flatMap(inventory -> inventoryRepository.deleteById(inventory.getId()))
            .collectList().block();
    }

    // 예약 관련 데이터 정리 — FK 순서: 이벤트 → 예약
    private void cleanupReservationRelatedData() {
        // 1. 예약에 연결된 이벤트를 먼저 삭제 (FK 제약조건)
        for (Long reservationId : createdReservationIds) {
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter(event -> reservationId.equals(event.getReservationId()))
                .flatMap(event -> channelEventRepository.deleteById(event.getId()))
                .collectList().block();
        }
        // 2. 예약 삭제
        for (Long id : createdReservationIds) {
            reservationRepository.deleteById(id).block();
        }
    }

    // ===== 예약 생성 성공 테스트 =====

    @Test // 정상적인 예약 생성 테스트
    @Order(1)
    void 예약_생성_유효한_요청이면_201_Created를_반환한다() {
        var request = new ReservationCreateRequest(
            "BOOKING",                    // Booking.com 채널
            TEST_ROOM_TYPE_ID,            // 테스트용 객실 타입
            TEST_CHECK_IN,                // 12/1 체크인
            TEST_CHECK_OUT,               // 12/3 체크아웃 (2박)
            "테스트 투숙객",               // 투숙객 이름
            1                             // 1실
        );

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(ReservationResponse.class)
            .consumeWith(result -> {
                var response = result.getResponseBody();
                assertThat(response).isNotNull();
                assertThat(response.channelCode()).isEqualTo("BOOKING");
                assertThat(response.roomTypeId()).isEqualTo(TEST_ROOM_TYPE_ID);
                assertThat(response.checkInDate()).isEqualTo(TEST_CHECK_IN);
                assertThat(response.checkOutDate()).isEqualTo(TEST_CHECK_OUT);
                assertThat(response.guestName()).isEqualTo("테스트 투숙객");
                assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
                assertThat(response.totalPrice()).isNotNull();
                assertThat(response.totalPrice().signum()).isPositive();
                createdReservationIds.add(response.id());
            });
    }

    @Test // 예약 생성 후 재고가 차감되었는지 확인하는 테스트
    @Order(2)
    void 예약_생성_후_재고가_차감되었는지_확인한다() {
        webTestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/inventories")
                .queryParam("roomTypeId", TEST_ROOM_TYPE_ID)
                .queryParam("startDate", TEST_CHECK_IN.toString())
                .queryParam("endDate", TEST_CHECK_IN.toString())
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(InventoryResponse.class)
            .hasSize(1)
            .consumeWith(result -> {
                var inventory = result.getResponseBody().getFirst();
                assertThat(inventory.availableQuantity()).isEqualTo(9); // 10 - 1 = 9
            });
    }

    // ===== 예약 생성 실패 테스트 =====

    @Test // 존재하지 않는 채널 코드로 예약 시 404 에러
    @Order(3)
    void 예약_생성_존재하지_않는_채널이면_404를_반환한다() {
        var request = new ReservationCreateRequest(
            "NONEXISTENT", TEST_ROOM_TYPE_ID,
            TEST_CHECK_IN, TEST_CHECK_OUT, "테스트 투숙객", 1
        );

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test // 비활성 채널로 예약 시 400 에러
    @Order(4)
    void 예약_생성_비활성_채널이면_400을_반환한다() {
        var request = new ReservationCreateRequest(
            "TRIP", TEST_ROOM_TYPE_ID,
            TEST_CHECK_IN, TEST_CHECK_OUT, "테스트 투숙객", 1
        );

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test // 존재하지 않는 객실 타입으로 예약 시 404 에러
    @Order(5)
    void 예약_생성_존재하지_않는_객실_타입이면_404를_반환한다() {
        var request = new ReservationCreateRequest(
            "BOOKING", 99999L,
            TEST_CHECK_IN, TEST_CHECK_OUT, "테스트 투숙객", 1
        );

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test // 체크인 >= 체크아웃이면 400 에러
    @Order(6)
    void 예약_생성_체크인이_체크아웃_이후이면_400을_반환한다() {
        var request = new ReservationCreateRequest(
            "BOOKING", TEST_ROOM_TYPE_ID,
            LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 3),
            "테스트 투숙객", 1
        );

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test // 재고가 없는 날짜로 예약 시 404 에러
    @Order(7)
    void 예약_생성_재고가_없는_날짜이면_404를_반환한다() {
        var request = new ReservationCreateRequest(
            "BOOKING", TEST_ROOM_TYPE_ID,
            LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 22),
            "테스트 투숙객", 1
        );

        webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNotFound();
    }

    // ===== 시뮬레이터 제어 테스트 =====

    @Test // 시뮬레이터 시작 테스트
    @Order(8)
    void 시뮬레이터_시작_POST_요청으로_시뮬레이터를_시작한다() {
        webTestClient.post()
            .uri("/api/simulator/start")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.running").isEqualTo(true);
    }

    @Test // 시뮬레이터 상태 조회 테스트
    @Order(9)
    void 시뮬레이터_상태_시작_후_실행_중_상태를_반환한다() {
        webTestClient.get()
            .uri("/api/simulator/status")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.running").isEqualTo(true);
    }

    @Test // 시뮬레이터 중지 테스트
    @Order(10)
    void 시뮬레이터_중지_POST_요청으로_시뮬레이터를_중지한다() {
        webTestClient.post()
            .uri("/api/simulator/stop")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.running").isEqualTo(false);
    }

    @Test // 중지 후 상태 확인 테스트
    @Order(11)
    void 시뮬레이터_상태_중지_후_실행_중이_아닌_상태를_반환한다() {
        webTestClient.get()
            .uri("/api/simulator/status")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.running").isEqualTo(false);
    }

    // ===== 예약 취소 테스트 (Phase 7) =====

    @Test // 예약 취소 성공 테스트
    @Order(12)
    void 예약_취소_CONFIRMED_예약을_취소하면_CANCELLED_상태를_반환한다() {
        // 먼저 취소할 예약을 생성한다
        var request = new ReservationCreateRequest(
            "BOOKING",
            TEST_ROOM_TYPE_ID,
            TEST_CHECK_IN,
            TEST_CHECK_OUT,
            "취소 테스트 투숙객",
            1
        );

        // 예약 생성
        var createResult = webTestClient.post()
            .uri("/api/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(ReservationResponse.class)
            .returnResult()
            .getResponseBody();
        createdReservationIds.add(createResult.id());

        // 예약 취소 — DELETE /api/reservations/{id}
        webTestClient.delete()
            .uri("/api/reservations/" + createResult.id())
            .exchange()
            .expectStatus().isOk() // 200 OK 확인
            .expectBody(ReservationResponse.class)
            .consumeWith(result -> {
                var response = result.getResponseBody();
                assertThat(response).isNotNull();
                assertThat(response.id()).isEqualTo(createResult.id()); // 같은 예약 ID
                assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED); // 취소 상태
                assertThat(response.guestName()).isEqualTo("취소 테스트 투숙객"); // 투숙객 이름 유지
            });
    }

    @Test // 예약 취소 후 재고 복구 확인 테스트
    @Order(13)
    void 예약_취소_후_재고가_복구되었는지_확인한다() {
        // Order(1)에서 1실 예약, Order(12)에서 1실 예약 후 취소했으므로
        // 재고는 Order(1)의 차감만 남아 9여야 한다
        webTestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/inventories")
                .queryParam("roomTypeId", TEST_ROOM_TYPE_ID)
                .queryParam("startDate", TEST_CHECK_IN.toString())
                .queryParam("endDate", TEST_CHECK_IN.toString())
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(InventoryResponse.class)
            .hasSize(1)
            .consumeWith(result -> {
                var inventory = result.getResponseBody().getFirst();
                // Order(1)에서 1실 차감(10→9), Order(12)에서 1실 차감 후 취소(9→8→9)
                assertThat(inventory.availableQuantity()).isEqualTo(9); // 취소 후 복구 확인
            });
    }

    @Test // 이미 취소된 예약 재취소 시 400 에러
    @Order(14)
    void 예약_취소_이미_취소된_예약이면_400을_반환한다() {
        // Order(12)에서 취소한 예약을 다시 취소 시도한다
        var cancelledId = createdReservationIds.getLast();

        webTestClient.delete()
            .uri("/api/reservations/" + cancelledId)
            .exchange()
            .expectStatus().isBadRequest(); // 400 Bad Request 확인
    }

    @Test // 존재하지 않는 예약 취소 시 404 에러
    @Order(15)
    void 예약_취소_존재하지_않는_예약이면_404를_반환한다() {
        webTestClient.delete()
            .uri("/api/reservations/99999") // 존재하지 않는 예약 ID
            .exchange()
            .expectStatus().isNotFound(); // 404 Not Found 확인
    }

    // ===== 예약 조회 테스트 (Phase 9) =====

    @Test // 예약 단건 조회 테스트
    @Order(16)
    void 예약_조회_ID로_단건_예약을_조회한다() {
        // Order(1)에서 생성한 첫 번째 예약을 조회한다
        var reservationId = createdReservationIds.getFirst();

        webTestClient.get()
            .uri("/api/reservations/" + reservationId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(ReservationResponse.class)
            .consumeWith(result -> {
                var response = result.getResponseBody();
                assertThat(response).isNotNull();
                assertThat(response.id()).isEqualTo(reservationId);
                assertThat(response.channelCode()).isNotBlank();
                assertThat(response.guestName()).isEqualTo("테스트 투숙객");
            });
    }

    @Test // 존재하지 않는 예약 조회 시 404
    @Order(17)
    void 예약_조회_존재하지_않는_예약이면_404를_반환한다() {
        webTestClient.get()
            .uri("/api/reservations/99999")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test // 예약 목록 전체 조회
    @Order(18)
    void 예약_목록_필터_없이_전체_예약을_반환한다() {
        webTestClient.get()
            .uri("/api/reservations")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ReservationResponse.class)
            .consumeWith(result -> {
                var reservations = result.getResponseBody();
                assertThat(reservations).isNotEmpty();
                reservations.forEach(r ->
                    assertThat(r.channelCode()).isNotBlank()
                );
            });
    }

    @Test // 상태 필터링 테스트
    @Order(19)
    void 예약_목록_status_필터로_확정_예약만_조회한다() {
        webTestClient.get()
            .uri("/api/reservations?status=CONFIRMED")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ReservationResponse.class)
            .consumeWith(result -> {
                var reservations = result.getResponseBody();
                reservations.forEach(r ->
                    assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED)
                );
            });
    }

    @Test // 페이징 테스트
    @Order(20)
    void 예약_목록_page와_size로_페이징한다() {
        webTestClient.get()
            .uri("/api/reservations?page=0&size=2")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ReservationResponse.class)
            .consumeWith(result -> {
                var reservations = result.getResponseBody();
                assertThat(reservations.size()).isLessThanOrEqualTo(2);
            });
    }
}
