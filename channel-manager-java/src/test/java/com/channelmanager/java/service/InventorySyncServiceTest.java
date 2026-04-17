package com.channelmanager.java.service; // 서비스 테스트 패키지

import com.channelmanager.java.domain.EventType; // 이벤트 타입 enum
import com.channelmanager.java.domain.Inventory; // 재고 엔티티
import com.channelmanager.java.dto.ReservationCreateRequest; // 예약 생성 요청 DTO
import com.channelmanager.java.dto.ReservationResponse; // 예약 응답 DTO
import com.channelmanager.java.repository.ChannelEventRepository; // 이벤트 리포지토리
import com.channelmanager.java.repository.InventoryRepository; // 재고 리포지토리
import com.channelmanager.java.repository.ReservationRepository; // 예약 리포지토리
import org.junit.jupiter.api.AfterAll; // 모든 테스트 완료 후 실행
import org.junit.jupiter.api.BeforeAll; // 모든 테스트 시작 전 실행
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance; // 테스트 인스턴스 생명주기 설정
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.java.config.TestcontainersConfig;
import com.channelmanager.java.config.TestSecurityConfig; // Phase 21: 테스트 보안 설정
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림

import java.time.LocalDate; // 날짜 타입
import java.util.ArrayList; // 리스트
import java.util.List; // 리스트 인터페이스

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 정적 임포트

// 재고 동기화 서비스 통합 테스트
// Phase 4의 핵심 기능을 검증한다:
// 1. 예약 생성 시 CHANNEL_SYNCED 이벤트가 다른 활성 채널에 발행되는지
// 2. 동시에 같은 재고를 차감해도 정합성이 유지되는지 (비관적 잠금)
// @SpringBootTest: 전체 애플리케이션 컨텍스트를 로드하여 실제 DB와 연동 테스트
// @TestInstance(PER_CLASS): @BeforeAll/@AfterAll에서 non-static 메서드 사용 가능
//   기본값은 PER_METHOD로 각 테스트마다 새 인스턴스를 생성하지만,
//   PER_CLASS는 하나의 인스턴스를 공유하여 @Autowired 필드를 @BeforeAll에서 사용할 수 있다
// Kotlin에서도 @TestInstance(PER_CLASS)를 동일하게 사용한다
@Import({TestcontainersConfig.class, TestSecurityConfig.class})
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventorySyncServiceTest {

    @Autowired // ReservationService를 주입 — 예약 생성으로 이벤트 발행을 트리거
    private ReservationService reservationService;

    @Autowired // 테스트 데이터 준비/정리를 위한 리포지토리들
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ChannelEventRepository channelEventRepository;

    // 테스트 중 생성된 데이터 ID를 추적하여 정리한다
    // Kotlin에서는 mutableListOf<Long>()이지만,
    // Java에서는 new ArrayList<>()로 생성한다
    private final List<Long> createdInventoryIds = new ArrayList<>();
    private final List<Long> createdReservationIds = new ArrayList<>();

    // 테스트 날짜 — 11월 2026년, 다른 테스트(12월)와 겹치지 않는 범위
    // Kotlin에서는 companion object에 선언하지만,
    // Java에서는 private static final로 선언한다
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 11, 10); // 체크인
    private static final LocalDate TEST_DATE_NEXT = LocalDate.of(2026, 11, 11); // 체크아웃
    private static final long TEST_ROOM_TYPE_ID = 1L; // 테스트용 객실 타입 ID

    @BeforeAll // 테스트 시작 전 — 재고 데이터 준비
    void setupTestData() {
        // 기존 잔여 데이터 정리 (이전 테스트 실행에서 남은 데이터)
        cleanupTestRange();
        // 테스트용 재고 생성 — 11/10 날짜에 15실
        // Kotlin에서는 named argument로 생성하지만,
        // Java에서는 Builder 패턴을 사용한다
        Inventory saved = inventoryRepository.save(
            Inventory.builder()
                .roomTypeId(TEST_ROOM_TYPE_ID)
                .stockDate(TEST_DATE)
                .totalQuantity(15) // 전체 15실
                .availableQuantity(15) // 가용 15실
                .build()
        ).block();
        createdInventoryIds.add(saved.getId());
    }

    @AfterAll // 모든 테스트 완료 후 — 생성된 데이터 정리
    void cleanup() {
        // FK 의존성 순서: 이벤트 → 예약 → 재고
        // 테스트 날짜 범위의 이벤트를 정리한다
        channelEventRepository.findAllByOrderByCreatedAtDesc()
            .filter(event ->
                // 테스트에서 생성한 예약의 이벤트이거나, CHANNEL_SYNCED 이벤트를 정리
                createdReservationIds.contains(event.getReservationId())
                    || (event.getEventType() == EventType.CHANNEL_SYNCED
                    && event.getRoomTypeId() != null
                    && event.getRoomTypeId().equals(TEST_ROOM_TYPE_ID))
            )
            .flatMap(event -> channelEventRepository.deleteById(event.getId()))
            .collectList().block();
        // 예약 삭제
        createdReservationIds.forEach(id -> reservationRepository.deleteById(id).block());
        // 재고 삭제
        createdInventoryIds.forEach(id -> inventoryRepository.deleteById(id).block());
    }

    // 11월 2026년 범위의 잔여 데이터 정리
    // Kotlin에서는 ?.let, ?: emptyList() 등으로 null 안전 처리하지만,
    // Java에서는 명시적 null 체크를 사용한다
    private void cleanupTestRange() {
        LocalDate startDate = LocalDate.of(2026, 11, 1);
        LocalDate endDate = LocalDate.of(2026, 11, 30);
        // 이전 테스트 예약 정리
        var prevReservations = reservationRepository.findByRoomTypeId(TEST_ROOM_TYPE_ID)
            .filter(r -> !r.getCheckInDate().isBefore(startDate) && !r.getCheckInDate().isAfter(endDate))
            .collectList().block();
        if (prevReservations == null) prevReservations = List.of();
        // 이벤트 → 예약 순으로 삭제
        for (var reservation : prevReservations) {
            channelEventRepository.findAllByOrderByCreatedAtDesc()
                .filter(event -> reservation.getId().equals(event.getReservationId()))
                .flatMap(event -> channelEventRepository.deleteById(event.getId()))
                .collectList().block();
        }
        // CHANNEL_SYNCED 이벤트 정리 (reservationId가 null일 수 있는 동기화 이벤트)
        channelEventRepository.findByEventType(EventType.CHANNEL_SYNCED)
            .filter(event -> event.getRoomTypeId() != null && event.getRoomTypeId().equals(TEST_ROOM_TYPE_ID))
            .flatMap(event -> channelEventRepository.deleteById(event.getId()))
            .collectList().block();
        for (var reservation : prevReservations) {
            reservationRepository.deleteById(reservation.getId()).block();
        }
        // 재고 정리
        inventoryRepository.findByRoomTypeIdAndStockDateBetween(
            TEST_ROOM_TYPE_ID, startDate, endDate
        ).flatMap(inv -> inventoryRepository.deleteById(inv.getId())).collectList().block();
    }

    @Test // 예약 생성 시 다른 활성 채널에 CHANNEL_SYNCED 이벤트가 발행되는지 확인
    void 예약_생성_시_다른_채널에_동기화_이벤트가_발생한다() throws InterruptedException {
        // BOOKING 채널로 예약 생성
        // Kotlin에서는 named argument로 DTO를 생성하지만,
        // Java에서는 record의 정규 생성자를 사용한다
        var request = new ReservationCreateRequest(
            "BOOKING",           // Booking.com 채널
            TEST_ROOM_TYPE_ID,   // 테스트 객실 타입
            TEST_DATE,           // 11/10 체크인
            TEST_DATE_NEXT,      // 11/11 체크아웃 (1박)
            "동기화 테스트 투숙객",
            1                    // 1실
        );

        // 예약 생성 — ReservationService가 이벤트를 Sinks에 발행하고,
        // InventorySyncService가 구독하여 CHANNEL_SYNCED 이벤트를 생성한다
        ReservationResponse response = reservationService.createReservation(request).block();
        createdReservationIds.add(response.id()); // 정리 대상에 추가

        // InventorySyncService의 비동기 동기화가 완료될 때까지 잠시 대기
        // Sinks → flatMap → DB 저장까지의 비동기 체인이 완료되어야 이벤트를 조회할 수 있다
        Thread.sleep(1000);

        // CHANNEL_SYNCED 이벤트 확인
        // BOOKING 채널로 예약했으므로, DIRECT, AGODA 채널에 동기화 이벤트가 발생해야 한다
        // (TRIP은 비활성이므로 동기화 대상에서 제외)
        var syncEvents = channelEventRepository.findByEventType(EventType.CHANNEL_SYNCED)
            .filter(event -> event.getRoomTypeId() != null
                && event.getRoomTypeId().equals(TEST_ROOM_TYPE_ID)) // 테스트 객실 타입만 필터
            .filter(event -> response.id().equals(event.getReservationId())) // 이번 테스트 예약만 필터
            .collectList().block();

        // DIRECT(id=1)와 AGODA(id=3)에 각각 동기화 이벤트가 발생해야 한다
        // BOOKING(id=2)은 예약 발생 채널이므로 제외, TRIP(id=4)은 비활성이므로 제외
        assertThat(syncEvents).hasSize(2); // DIRECT + AGODA = 2건
        assertThat(syncEvents.stream().map(e -> e.getChannelId()).toList())
            .doesNotContain(2L); // BOOKING 제외 확인
    }

    @Test // 동시에 같은 재고를 차감해도 정합성이 유지되는지 확인 (비관적 잠금 테스트)
    void 동시에_같은_재고를_차감해도_정합성이_유지된다() {
        // 별도의 재고 데이터 준비 — 동시성 테스트 전용 날짜
        LocalDate concurrencyDate = LocalDate.of(2026, 11, 15); // 동시성 테스트용 날짜
        LocalDate concurrencyDateNext = LocalDate.of(2026, 11, 16);
        Inventory saved = inventoryRepository.save(
            Inventory.builder()
                .roomTypeId(TEST_ROOM_TYPE_ID)
                .stockDate(concurrencyDate)
                .totalQuantity(15)
                .availableQuantity(15) // 초기 가용 15실
                .build()
        ).block();
        createdInventoryIds.add(saved.getId());

        int concurrentCount = 5; // 동시에 5개의 예약을 생성한다

        // Flux.range로 5개의 예약 요청을 동시에 실행한다
        // flatMap: 각 예약을 동시에(비동기 병렬로) 실행한다
        //   concatMap과 달리 flatMap은 동시에 여러 구독을 시작한다
        // collectList: 모든 결과를 리스트로 모은다
        // block: 테스트에서만 블로킹 대기
        // Kotlin에서는 "동시성테스트$i" 문자열 템플릿이지만,
        // Java에서는 "동시성테스트" + i 문자열 연결이다
        var results = Flux.range(1, concurrentCount)
            .flatMap(i ->
                reservationService.createReservation(
                    new ReservationCreateRequest(
                        "DIRECT", // 자사 홈페이지
                        TEST_ROOM_TYPE_ID,
                        concurrencyDate,       // 동일 날짜에 동시 예약
                        concurrencyDateNext,
                        "동시성테스트" + i,      // 각각 다른 투숙객
                        1
                    )
                )
            )
            .collectList()
            .block();

        // 모든 예약이 성공해야 한다
        assertThat(results).hasSize(concurrentCount);
        results.forEach(r -> createdReservationIds.add(r.id())); // 정리 대상 추가

        // 재고 확인: 초기 15 - 5 = 10이어야 한다
        // FOR UPDATE 잠금이 없으면 Lost Update로 인해 10보다 클 수 있다
        Inventory inventory = inventoryRepository
            .findByRoomTypeIdAndStockDate(TEST_ROOM_TYPE_ID, concurrencyDate)
            .block();

        assertThat(inventory.getAvailableQuantity()).isEqualTo(10); // 15 - 5 = 10 정합성 확인
    }
}
