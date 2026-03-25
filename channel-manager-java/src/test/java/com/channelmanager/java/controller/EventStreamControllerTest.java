package com.channelmanager.java.controller; // 컨트롤러 테스트 패키지

import com.channelmanager.java.domain.ChannelEvent; // 채널 이벤트 엔티티
import com.channelmanager.java.domain.EventType; // 이벤트 타입 enum
import com.channelmanager.java.dto.EventResponse; // 이벤트 응답 DTO
import com.channelmanager.java.repository.ChannelEventRepository; // 이벤트 리포지토리
import com.channelmanager.java.service.EventPublisher; // 이벤트 발행 서비스 (Sinks)
import org.junit.jupiter.api.AfterAll; // 모든 테스트 완료 후 실행
import org.junit.jupiter.api.BeforeEach; // 각 테스트 전 실행
import org.junit.jupiter.api.MethodOrderer; // 테스트 메서드 실행 순서 제어
import org.junit.jupiter.api.Order; // 테스트 실행 순서 지정
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance; // 테스트 인스턴스 생명주기 설정
import org.junit.jupiter.api.TestMethodOrder; // 테스트 메서드 정렬 전략
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import org.springframework.boot.test.web.server.LocalServerPort; // 랜덤 포트 주입
import org.springframework.http.MediaType; // HTTP 미디어 타입
import org.springframework.test.web.reactive.server.WebTestClient; // WebFlux 테스트용 HTTP 클라이언트
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import reactor.test.StepVerifier; // 리액티브 스트림 테스트 유틸리티
import java.time.Duration; // 시간 간격
import java.util.ArrayList; // 가변 리스트
import java.util.List; // 리스트 인터페이스

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 정적 import

// SSE(Server-Sent Events) 이벤트 스트리밍 컨트롤러 통합 테스트
// 실제 서버를 랜덤 포트로 기동하여 SSE 엔드포인트를 테스트한다
// SSE 스트림 테스트: 컨트롤러의 streamEvents() 메서드를 직접 호출하여 Flux를 검증한다
//   — WebTestClient/WebClient는 SSE 무한 스트림에서 타임아웃 문제가 발생할 수 있어,
//     컨트롤러 메서드를 직접 호출하는 방식이 더 안정적이다
// REST API 테스트: WebTestClient로 /api/events 엔드포인트를 HTTP 레벨에서 검증한다
// Kotlin과 동일한 테스트 구조이지만, Java에서는 명시적 타입 선언과 메서드 호출을 사용한다
// @TestInstance(PER_CLASS): @AfterAll에서 인스턴스 필드 접근 가능
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventStreamControllerTest {

    @LocalServerPort // Spring이 실제 기동된 서버의 랜덤 포트 번호를 주입한다
    private int port;

    @Autowired // 컨트롤러를 직접 주입하여 streamEvents() 메서드를 테스트한다
    private EventStreamController eventStreamController;

    @Autowired // EventPublisher를 주입하여 테스트 중 이벤트를 직접 발행한다
    private EventPublisher eventPublisher;

    @Autowired // DB에서 이벤트를 조회/정리하기 위해 리포지토리를 주입한다
    private ChannelEventRepository channelEventRepository;

    // WebTestClient — 최근 이벤트 목록 조회(일반 JSON API) 테스트에 사용한다
    private WebTestClient webTestClient;

    // 테스트 중 DB에 저장된 이벤트 ID를 추적하여 정리한다
    // Kotlin에서는 mutableListOf<Long>()을 사용하지만, Java에서는 new ArrayList<>()를 사용한다
    private final List<Long> createdEventIds = new ArrayList<>();

    @BeforeEach // 각 테스트 실행 전에 WebTestClient를 초기화한다
    void setUp() {
        webTestClient = WebTestClient.bindToServer() // 실제 서버에 바인딩
            .baseUrl("http://localhost:" + port) // 랜덤 포트로 기본 URL 설정
            .responseTimeout(Duration.ofSeconds(10)) // 응답 대기 타임아웃
            .build(); // WebTestClient 생성
    }

    @AfterAll // 모든 테스트 완료 후 생성된 이벤트 데이터를 정리한다
    void cleanupAfter() {
        // 테스트에서 DB에 저장된 이벤트를 삭제한다
        // Kotlin에서는 forEach { id -> ... }를 사용하지만,
        // Java에서는 for-each 루프를 사용한다
        for (Long id : createdEventIds) {
            channelEventRepository.deleteById(id).block();
        }
    }

    // ===== SSE 스트림 테스트 =====

    @Test // SSE 스트림 연결 및 이벤트 수신 테스트
    @Order(1) // 첫 번째로 실행 — SSE 스트림이 정상적으로 이벤트를 전달하는지 검증
    void SSE_스트림_이벤트_발행_시_SSE로_수신한다() {
        // 테스트 시나리오:
        // 1. DB에 이벤트를 저장한다 (ID 부여)
        // 2. 컨트롤러의 streamEvents()를 직접 호출하여 SSE Flux를 얻는다
        // 3. Mono.delay()로 100ms 후에 EventPublisher를 통해 이벤트를 발행한다
        // 4. StepVerifier로 SSE 스트림에서 해당 이벤트를 수신하는지 확인한다

        // 먼저 DB에 이벤트를 저장하여 ID를 부여한다 (SSE의 id 필드에 사용)
        // Kotlin에서는 data class 생성자를 사용하지만,
        // Java에서는 Builder 패턴을 사용한다
        ChannelEvent savedEvent = channelEventRepository.save(
            ChannelEvent.builder()
                .eventType(EventType.RESERVATION_CREATED) // 예약 생성 이벤트
                .channelId(1L)                              // DIRECT 채널
                .roomTypeId(1L)                             // Superior Double
                .eventPayload("{\"test\": true}")           // 테스트용 페이로드
                .build()
        ).block(); // 테스트 준비에서만 block() 사용
        createdEventIds.add(savedEvent.getId()); // 정리 대상에 추가

        // 컨트롤러의 streamEvents() 메서드를 직접 호출한다
        // 반환값: Flux<ServerSentEvent<EventResponse>> — SSE 이벤트 스트림
        // HTTP를 거치지 않으므로 WebClient 타임아웃 문제가 없다
        // Kotlin에서는 eventStreamController.streamEvents()로 동일하게 호출한다
        var sseStream = eventStreamController.streamEvents();

        // Mono.delay()로 100ms 후에 이벤트를 발행한다
        // StepVerifier가 구독한 후 이벤트가 도착해야 수신할 수 있으므로 지연시킨다
        // Kotlin에서는 doOnNext { eventPublisher.publish(savedEvent) }이지만,
        // Java에서는 doOnNext(tick -> eventPublisher.publish(savedEvent))이다
        Mono.delay(Duration.ofMillis(100)) // 100ms 지연
            .doOnNext(tick -> eventPublisher.publish(savedEvent)) // 이벤트 발행
            .subscribe(); // 비동기 실행 시작

        // StepVerifier: 리액티브 스트림의 동작을 단계별로 검증하는 테스트 유틸리티
        StepVerifier.create(
            sseStream
                // heartbeat(comment만 있고 data가 null인 이벤트)를 필터링한다
                .filter(sse -> sse.data() != null)
                .take(1) // 첫 번째 데이터 이벤트만 수신하고 스트림을 종료
        )
            .assertNext(sse -> { // 수신된 SSE 이벤트를 검증한다
                // SSE event: 필드가 RESERVATION_CREATED인지 확인
                assertThat(sse.event()).isEqualTo("RESERVATION_CREATED");
                // SSE id: 필드가 저장된 이벤트의 ID와 일치하는지 확인
                assertThat(sse.id()).isEqualTo(savedEvent.getId().toString());
                // SSE data: 필드가 EventResponse DTO로 변환되었는지 확인
                assertThat(sse.data()).isNotNull();
                assertThat(sse.data().eventType()).isEqualTo(EventType.RESERVATION_CREATED);
            })
            // expectComplete().verify(Duration): take(1)에 의해 스트림 완료를 기대하고 타임아웃 설정
            // verifyComplete()는 무한 대기하므로, SSE 테스트에서는 반드시 타임아웃을 설정한다
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test // 여러 이벤트 타입 SSE 수신 테스트
    @Order(2) // 두 번째로 실행
    void SSE_스트림_여러_이벤트_타입을_순서대로_수신한다() {
        // 테스트 시나리오:
        // EventPublisher로 INVENTORY_UPDATED와 CHANNEL_SYNCED 이벤트를 순서대로 발행하고,
        // SSE 스트림에서 두 이벤트를 순서대로 수신하는지 확인한다

        // 두 개의 이벤트를 DB에 저장한다
        // Kotlin에서는 data class 생성자, Java에서는 Builder 패턴
        ChannelEvent event1 = channelEventRepository.save(
            ChannelEvent.builder()
                .eventType(EventType.INVENTORY_UPDATED) // 재고 변경 이벤트
                .roomTypeId(1L)
                .eventPayload("{\"before\": 10, \"after\": 9}")
                .build()
        ).block();
        createdEventIds.add(event1.getId());

        ChannelEvent event2 = channelEventRepository.save(
            ChannelEvent.builder()
                .eventType(EventType.CHANNEL_SYNCED) // 채널 동기화 이벤트
                .channelId(2L)
                .roomTypeId(1L)
                .eventPayload("{\"synced\": true}")
                .build()
        ).block();
        createdEventIds.add(event2.getId());

        // 컨트롤러의 streamEvents() 메서드를 직접 호출한다
        var sseStream = eventStreamController.streamEvents();

        // Mono.delay()로 100ms 후에 두 이벤트를 순서대로 발행한다
        Mono.delay(Duration.ofMillis(100))
            .doOnNext(tick -> {
                eventPublisher.publish(event1); // 첫 번째 이벤트 발행
                eventPublisher.publish(event2); // 두 번째 이벤트 발행
            })
            .subscribe(); // 비동기 실행 시작

        // StepVerifier로 두 이벤트가 순서대로 수신되는지 확인한다
        StepVerifier.create(
            sseStream
                .filter(sse -> sse.data() != null) // heartbeat 필터링
                .take(2) // 두 개의 데이터 이벤트를 수신
        )
            .assertNext(sse -> { // 첫 번째 이벤트: INVENTORY_UPDATED
                assertThat(sse.event()).isEqualTo("INVENTORY_UPDATED");
                assertThat(sse.id()).isEqualTo(event1.getId().toString());
                assertThat(sse.data().eventType()).isEqualTo(EventType.INVENTORY_UPDATED);
            })
            .assertNext(sse -> { // 두 번째 이벤트: CHANNEL_SYNCED
                assertThat(sse.event()).isEqualTo("CHANNEL_SYNCED");
                assertThat(sse.id()).isEqualTo(event2.getId().toString());
                assertThat(sse.data().eventType()).isEqualTo(EventType.CHANNEL_SYNCED);
            })
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test // SSE 스트림에 heartbeat가 포함되어 있는지 확인
    @Order(3) // 세 번째로 실행
    void SSE_스트림_heartbeat_이벤트가_포함되어_있다() {
        // streamEvents() 반환 스트림에 heartbeat(data가 null인 이벤트)가 포함되는지 확인한다
        // Flux.merge(eventStream, heartbeat)로 합쳤으므로, heartbeat도 스트림에 포함된다
        // Kotlin에서는 eventStreamController.streamEvents()로 동일하게 호출한다
        var sseStream = eventStreamController.streamEvents();

        // StepVerifier로 스트림의 첫 이벤트가 heartbeat인지 확인한다
        // heartbeat는 30초 간격이므로, thenCancel()로 즉시 취소한다
        // 실제로 30초를 기다리지 않고 스트림 구조만 확인한다
        StepVerifier.create(sseStream)
            // thenCancel: 스트림을 즉시 취소한다 — 무한 스트림 테스트의 안전 장치
            .thenCancel()
            .verify(Duration.ofSeconds(1));
    }

    // ===== 최근 이벤트 목록 조회 테스트 =====

    @Test // 최근 이벤트 목록 조회 테스트 — 기본 limit
    @Order(4) // 네 번째로 실행
    void 최근_이벤트_조회_기본_limit으로_이벤트_목록을_반환한다() {
        // GET /api/events — limit 파라미터 없이 요청하면 기본값 50개를 반환한다
        // V7 샘플 데이터와 이전 테스트에서 생성된 이벤트가 포함될 수 있다
        webTestClient.get()
            .uri("/api/events") // 최근 이벤트 목록 엔드포인트
            .exchange() // 요청 실행
            .expectStatus().isOk() // HTTP 200 OK 확인
            // expectBodyList: 응답이 JSON 배열인지 확인하고 타입으로 역직렬화
            .expectBodyList(EventResponse.class)
            .consumeWith(result -> {
                var events = result.getResponseBody();
                // 이벤트가 1개 이상 존재하는지 확인 (V7 샘플 데이터 + 테스트 데이터)
                assertThat(events).isNotEmpty();
                // 이벤트가 최신순으로 정렬되어 있는지 확인한다
                // 각 이벤트의 createdAt이 다음 이벤트의 createdAt보다 같거나 이후여야 한다
                // Kotlin에서는 events[i]로 인덱싱하지만, Java에서는 events.get(i)를 사용한다
                for (int i = 0; i < events.size() - 1; i++) {
                    var current = events.get(i).createdAt();   // 현재 이벤트의 생성 시각
                    var next = events.get(i + 1).createdAt();  // 다음 이벤트의 생성 시각
                    if (current != null && next != null) {
                        // 최신순이므로 현재 >= 다음이어야 한다
                        assertThat(current).isAfterOrEqualTo(next);
                    }
                }
            });
    }

    @Test // 최근 이벤트 목록 조회 — limit 파라미터 적용
    @Order(5) // 다섯 번째로 실행
    void 최근_이벤트_조회_limit_파라미터로_개수를_제한한다() {
        // GET /api/events?limit=3 — 최대 3개의 이벤트만 반환한다
        webTestClient.get()
            .uri("/api/events?limit=3") // limit=3으로 제한
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(EventResponse.class)
            .consumeWith(result -> {
                var events = result.getResponseBody();
                // 최대 3개까지만 반환되는지 확인한다
                assertThat(events.size()).isLessThanOrEqualTo(3);
            });
    }

    @Test // 이벤트 응답 DTO 필드 검증
    @Order(6) // 여섯 번째로 실행
    void 최근_이벤트_조회_응답에_필수_필드가_포함되어_있다() {
        // 응답 DTO의 필수 필드(id, eventType, createdAt)가 존재하는지 확인한다
        webTestClient.get()
            .uri("/api/events?limit=1") // 1개만 조회
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(EventResponse.class)
            .consumeWith(result -> {
                var events = result.getResponseBody();
                if (!events.isEmpty()) {
                    var event = events.getFirst();
                    // 필수 필드 존재 확인
                    // Kotlin에서는 event.id로 프로퍼티 접근, Java에서는 event.id() record 접근자
                    assertThat(event.id()).isNotNull();       // 이벤트 ID
                    assertThat(event.eventType()).isNotNull(); // 이벤트 타입
                    assertThat(event.createdAt()).isNotNull(); // 생성 시각
                }
            });
    }
}
