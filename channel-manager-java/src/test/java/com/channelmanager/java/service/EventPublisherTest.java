package com.channelmanager.java.service; // 서비스 테스트 패키지

import com.channelmanager.java.domain.ChannelEvent; // 채널 이벤트 엔티티
import com.channelmanager.java.domain.EventType; // 이벤트 타입 enum
import org.assertj.core.api.Assertions; // AssertJ 검증 메서드
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import reactor.core.Disposable; // 구독 핸들
import reactor.test.StepVerifier; // Reactor 스트림 검증 도구

import java.util.ArrayList; // 수신 버퍼용 리스트
import java.util.List; // 리스트 인터페이스

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 정적 임포트

// EventPublisher 단위 테스트
// Spring 컨텍스트 없이 순수하게 Sinks의 발행/구독 동작을 검증한다
// StepVerifier로 비동기 스트림의 이벤트 수신을 검증한다
// Kotlin에서는 companion object 없이 직접 인스턴스를 생성하지만,
// Java에서도 동일하게 new로 생성한다 (외부 의존성 없음)
class EventPublisherTest {

    // 테스트 대상 — Spring 빈이 아닌 직접 생성
    // EventPublisher는 외부 의존성이 없으므로 new로 생성 가능하다
    private final EventPublisher eventPublisher = new EventPublisher();

    @Test // 이벤트 발행 후 구독자가 수신하는지 확인
    void 이벤트를_발행하면_구독자가_수신한다() {
        // 테스트 이벤트 생성 — RESERVATION_CREATED 타입, 채널 ID 1
        // Kotlin에서는 named argument로 생성하지만,
        // Java에서는 Builder 패턴을 사용한다
        ChannelEvent event = ChannelEvent.builder()
            .eventType(EventType.RESERVATION_CREATED) // 예약 생성 이벤트
            .channelId(1L) // 채널 ID
            .roomTypeId(1L) // 객실 타입 ID
            .eventPayload("{\"test\": true}") // 테스트 페이로드
            .build();

        // StepVerifier.create: 비동기 스트림을 구독하고 검증을 시작한다
        // take(1): 첫 번째 이벤트만 수신한 후 구독을 취소한다
        //   Hot Stream(Sinks)은 무한 스트림이므로 take로 제한하지 않으면 완료되지 않는다
        // then: 구독이 시작된 후 이벤트를 발행한다
        //   구독 전에 발행하면 multicast 모드에서 이벤트가 유실된다
        // expectNextMatches: 수신한 이벤트의 조건을 검증한다
        // verifyComplete: take(1)에 의해 스트림이 완료되는지 확인한다
        // Kotlin에서는 { eventPublisher.publish(event) } 중괄호 람다이지만,
        // Java에서는 () -> eventPublisher.publish(event) 화살표 람다이다
        StepVerifier.create(eventPublisher.getEventStream().take(1)) // 1개만 수신
            .then(() -> eventPublisher.publish(event)) // 구독 후 발행
            .expectNextMatches(received -> // 수신 이벤트 검증
                received.getEventType() == EventType.RESERVATION_CREATED // 이벤트 타입 확인
                    && received.getChannelId() == 1L // 채널 ID 확인
            )
            .verifyComplete(); // 스트림 완료 확인
    }

    @Test // 여러 구독자가 동시에 같은 이벤트를 수신하는지 확인 (multicast 검증)
    void 여러_구독자가_동시에_이벤트를_수신한다() throws InterruptedException {
        ChannelEvent event = ChannelEvent.builder()
            .eventType(EventType.INVENTORY_UPDATED) // 재고 변경 이벤트
            .roomTypeId(2L)
            .eventPayload("{\"before\": 10, \"after\": 9}")
            .build();

        // 구독자 1, 2를 먼저 구독한 후 이벤트를 발행한다
        // collectList + take(1): 첫 번째 이벤트를 리스트로 수집한다
        // 구독(subscribe)은 논블로킹이므로 두 구독자가 동시에 대기한다
        // Kotlin에서는 mutableListOf<ChannelEvent>()이지만,
        // Java에서는 new ArrayList<>()로 생성한다
        List<ChannelEvent> received1 = new ArrayList<>(); // 구독자 1 수신 버퍼
        List<ChannelEvent> received2 = new ArrayList<>(); // 구독자 2 수신 버퍼

        // 구독자 1: 이벤트를 수집
        Disposable disposable1 = eventPublisher.getEventStream()
            .take(1)
            .subscribe(received1::add);

        // 구독자 2: 같은 Sinks에서 수신
        Disposable disposable2 = eventPublisher.getEventStream()
            .take(1)
            .subscribe(received2::add);

        // 이벤트 발행 — 두 구독자 모두 같은 이벤트를 수신해야 한다
        eventPublisher.publish(event);

        // 비동기 처리 완료 대기
        Thread.sleep(500);

        // 두 구독자 모두 동일한 이벤트를 수신했는지 검증
        assertThat(received1).hasSize(1); // 구독자 1: 1개 수신
        assertThat(received1.get(0).getEventType()).isEqualTo(EventType.INVENTORY_UPDATED);
        assertThat(received2).hasSize(1); // 구독자 2: 1개 수신
        assertThat(received2.get(0).getRoomTypeId()).isEqualTo(2L);

        // 구독 정리
        disposable1.dispose();
        disposable2.dispose();
    }

    @Test // 필터링을 활용하여 특정 이벤트 타입만 수신하는지 확인
    void 이벤트_스트림에서_특정_타입만_필터링하여_수신한다() {
        // EventPublisher의 getEventStream()에 filter를 적용하여
        // 특정 이벤트 타입만 수신하는 패턴을 검증한다 (InventorySyncService에서 사용하는 패턴)
        ChannelEvent reservationEvent = ChannelEvent.builder()
            .eventType(EventType.RESERVATION_CREATED)
            .channelId(1L)
            .build();
        ChannelEvent inventoryEvent = ChannelEvent.builder()
            .eventType(EventType.INVENTORY_UPDATED)
            .roomTypeId(3L)
            .build();

        // INVENTORY_UPDATED 이벤트만 필터링하여 수신
        // 2개의 이벤트를 발행하지만, 필터에 의해 1개만 수신되어야 한다
        // Kotlin에서는 { it.eventType == EventType.INVENTORY_UPDATED }이지만,
        // Java에서는 event -> event.getEventType() == EventType.INVENTORY_UPDATED이다
        StepVerifier.create(
            eventPublisher.getEventStream()
                .filter(event -> event.getEventType() == EventType.INVENTORY_UPDATED) // 재고 변경만 필터
                .take(1) // 1개만 수신
        )
            .then(() -> {
                eventPublisher.publish(reservationEvent); // 예약 이벤트 — 필터에 의해 무시됨
                eventPublisher.publish(inventoryEvent);    // 재고 이벤트 — 이것만 수신
            })
            .expectNextMatches(event ->
                event.getEventType() == EventType.INVENTORY_UPDATED) // 필터 동작 확인
            .verifyComplete();
    }
}
