package com.channelmanager.java.service; // 서비스 테스트 패키지

import com.channelmanager.java.config.TestcontainersConfig; // Phase 13: Testcontainers 설정
import com.channelmanager.java.domain.Inventory; // 재고 엔티티
import com.channelmanager.java.dto.ReservationCreateRequest; // 예약 생성 요청 DTO
import com.channelmanager.java.dto.ReservationResponse; // 예약 응답 DTO
import com.channelmanager.java.repository.ChannelEventRepository; // 이벤트 리포지토리
import com.channelmanager.java.repository.InventoryRepository; // 재고 리포지토리
import com.channelmanager.java.repository.ReservationRepository; // 예약 리포지토리
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
import org.springframework.boot.test.web.server.LocalServerPort; // 랜덤 포트 주입
import org.springframework.context.annotation.Import; // 테스트 설정 임포트
import org.springframework.http.MediaType; // HTTP 미디어 타입
import org.springframework.test.web.reactive.server.WebTestClient; // WebFlux 테스트용 HTTP 클라이언트
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.time.Duration; // 시간 간격
import java.time.LocalDate; // 날짜 타입
import java.util.ArrayList; // 가변 리스트
import java.util.List; // 리스트 인터페이스
import java.util.stream.IntStream; // 정수 스트림

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 정적 import

// 동시성 테스트 — Phase 4의 FOR UPDATE 비관적 잠금이 동시 예약에서 재고 정합성을 보장하는지 검증
// Kotlin과 동일한 테스트 구조이지만, Java에서는 명시적 타입 선언과 메서드 호출을 사용한다
@Import(TestcontainersConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyTest {

    @LocalServerPort
    private int port;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ChannelEventRepository channelEventRepository;

    private WebTestClient webTestClient;

    // 동시성 테스트 전용 날짜
    private static final LocalDate CONCURRENCY_DATE = LocalDate.of(2027, 1, 10);
    private static final LocalDate CONCURRENCY_DATE_OUT = LocalDate.of(2027, 1, 11);
    private static final long TEST_ROOM_TYPE_ID = 1L;
    private static final int INITIAL_QUANTITY = 10;
    private static final int CONCURRENCY = 10;

    private final List<Long> createdInventoryIds = new ArrayList<>();
    private final List<Long> createdReservationIds = new ArrayList<>();

    @BeforeAll
    void setupTestData() {
        cleanupPreviousData();

        var saved = inventoryRepository.save(
            Inventory.builder()
                .roomTypeId(TEST_ROOM_TYPE_ID)
                .stockDate(CONCURRENCY_DATE)
                .totalQuantity(INITIAL_QUANTITY)
                .availableQuantity(INITIAL_QUANTITY)
                .build()
        ).block();
        createdInventoryIds.add(saved.getId());
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(30))
            .build();
    }

    @AfterAll
    void cleanupAfter() {
        for (Long reservationId : createdReservationIds) {
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter(e -> reservationId.equals(e.getReservationId()))
                .flatMap(e -> channelEventRepository.deleteById(e.getId()))
                .collectList().block();
        }
        for (Long id : createdReservationIds) {
            reservationRepository.deleteById(id).block();
        }
        for (Long id : createdInventoryIds) {
            inventoryRepository.deleteById(id).block();
        }
    }

    // ===== 동시 예약 테스트 =====

    @Test
    @Order(1)
    void 동시_예약_10건_동시_요청_시_재고가_정확하게_차감된다() {
        // 10개의 동시 예약 요청을 생성한다
        var requests = IntStream.rangeClosed(1, CONCURRENCY)
            .mapToObj(i ->
                webTestClient.post()
                    .uri("/api/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ReservationCreateRequest(
                        "BOOKING",
                        TEST_ROOM_TYPE_ID,
                        CONCURRENCY_DATE,
                        CONCURRENCY_DATE_OUT,
                        "동시테스트" + i,
                        1
                    ))
                    .exchange()
                    .returnResult(ReservationResponse.class)
                    .getResponseBody()
                    .next()
                    .map(response -> {
                        createdReservationIds.add(response.id());
                        return "SUCCESS";
                    })
                    .onErrorResume(e -> Mono.just("FAIL"))
            )
            .toList();

        var results = Flux.merge(requests)
            .collectList()
            .block(Duration.ofSeconds(30));

        var successCount = results.stream().filter("SUCCESS"::equals).count();
        var failCount = results.stream().filter("FAIL"::equals).count();

        assertThat(successCount + failCount).isEqualTo(CONCURRENCY);
        assertThat(successCount).isEqualTo(INITIAL_QUANTITY);

        var inventory = inventoryRepository.findByRoomTypeIdAndStockDate(
            TEST_ROOM_TYPE_ID, CONCURRENCY_DATE
        ).block();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(INITIAL_QUANTITY - (int) successCount);
    }

    @Test
    @Order(2)
    void 동시_예약_재고_부족_시_초과_예약이_발생하지_않는다() {
        // WebClient를 사용하여 상태 코드로 성공/실패를 판단한다
        // WebTestClient의 returnResult는 4xx 에러도 본문을 반환하므로,
        // WebClient의 retrieve()를 사용하여 4xx 시 에러를 발생시킨다
        var webClient = org.springframework.web.reactive.function.client.WebClient
            .create("http://localhost:" + port);

        var additionalRequests = IntStream.rangeClosed(1, 5)
            .mapToObj(i ->
                webClient.post()
                    .uri("/api/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ReservationCreateRequest(
                        "DIRECT",
                        TEST_ROOM_TYPE_ID,
                        CONCURRENCY_DATE,
                        CONCURRENCY_DATE_OUT,
                        "초과테스트" + i,
                        1
                    ))
                    .retrieve() // 4xx/5xx 시 에러 발생
                    .bodyToMono(String.class)
                    .map(r -> "SUCCESS")
                    .onErrorResume(e -> Mono.just("FAIL"))
            )
            .toList();

        var results = Flux.merge(additionalRequests)
            .collectList()
            .block(Duration.ofSeconds(30));

        var successCount = results.stream().filter("SUCCESS"::equals).count();
        assertThat(successCount).isEqualTo(0);

        var inventory = inventoryRepository.findByRoomTypeIdAndStockDate(
            TEST_ROOM_TYPE_ID, CONCURRENCY_DATE
        ).block();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(0);
    }

    private void cleanupPreviousData() {
        LocalDate startDate = LocalDate.of(2027, 1, 1);
        LocalDate endDate = LocalDate.of(2027, 1, 31);

        var previousReservations = reservationRepository.findByRoomTypeId(TEST_ROOM_TYPE_ID)
            .filter(r -> !r.getCheckInDate().isBefore(startDate)
                && !r.getCheckInDate().isAfter(endDate))
            .collectList().block();
        if (previousReservations == null) previousReservations = List.of();

        for (var reservation : previousReservations) {
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter(e -> reservation.getId().equals(e.getReservationId()))
                .flatMap(e -> channelEventRepository.deleteById(e.getId()))
                .collectList().block();
        }
        for (var reservation : previousReservations) {
            reservationRepository.deleteById(reservation.getId()).block();
        }

        inventoryRepository.findByRoomTypeIdAndStockDateBetween(
            TEST_ROOM_TYPE_ID, startDate, endDate
        ).flatMap(inv -> inventoryRepository.deleteById(inv.getId()))
            .collectList().block();
    }
}
