package com.channelmanager.kotlin.service // 서비스 패키지 - 비즈니스 로직 계층

import com.channelmanager.kotlin.domain.ChannelEvent // 채널 이벤트 엔티티
import org.slf4j.LoggerFactory // SLF4J 로거 팩토리
import org.springframework.stereotype.Service // 서비스 계층 어노테이션
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Sinks // 프로그래밍 방식의 이벤트 발행기 (Reactor 3.4+)

// 이벤트 발행 서비스 — Sinks를 사용하여 이벤트를 메모리 내 스트림으로 브로드캐스트한다
// Sinks는 "생산자(push) 쪽"이고, asFlux()로 변환한 Flux가 "소비자(subscribe) 쪽"이다
// ReservationService, InventoryService에서 이벤트를 발행하면,
// InventorySyncService, SSE 대시보드(Phase 5) 등 여러 구독자가 실시간으로 수신한다
// @Service로 Spring 빈으로 등록하여 싱글톤으로 관리한다 — 모든 발행/구독이 같은 Sinks를 공유한다
@Service
class EventPublisher {

    // SLF4J 로거 — companion object에 선언하여 클래스 레벨 로거로 사용한다
    // Kotlin에서는 Java의 static final과 달리 companion object 내에 선언하는 것이 관례이다
    companion object {
        private val log = LoggerFactory.getLogger(EventPublisher::class.java) // 로거 인스턴스
    }

    // Sinks.many().multicast().onBackpressureBuffer() — Hot Stream 이벤트 발행기
    // many(): 여러 개의 이벤트를 연속으로 발행할 수 있다 (Sinks.one()은 단일 값만)
    // multicast(): 여러 구독자가 동시에 같은 이벤트를 수신한다 (브로드캐스트)
    //   - 구독 이후에 발행된 이벤트만 수신한다 (구독 전 이벤트는 유실)
    //   - replay()와 달리 과거 이벤트를 캐싱하지 않는다 (DB에 이미 저장되어 있으므로 불필요)
    // onBackpressureBuffer(): 구독자의 처리 속도가 느릴 때 이벤트를 버퍼에 저장한다
    //   - 이벤트 유실을 방지한다 (호텔 예약에서 재고 동기화 이벤트 유실은 오버부킹 위험)
    // <ChannelEvent>: Kotlin에서는 타입 파라미터를 메서드 체인 끝에 명시한다
    //   Java에서는 변수 타입 선언(Sinks.Many<ChannelEvent>)으로 추론되지만,
    //   Kotlin에서는 val 타입 추론 시 제네릭을 명시해야 한다
    private val sinks = Sinks.many().multicast().onBackpressureBuffer<ChannelEvent>()

    // 이벤트 발행 — ReservationService, InventoryService에서 호출한다
    // tryEmitNext: 스레드 안전한(lock-free) 이벤트 발행 메서드
    //   - 성공 시 EmitResult.OK 반환
    //   - 실패 시 EmitResult.FAIL_* 반환 (구독자 없음, 버퍼 초과 등)
    //   - emitNext()와 달리 내부 spin loop 없이 즉시 결과를 반환한다
    // isFailure: EmitResult의 프로퍼티 — 발행 실패 여부를 확인한다
    //   Java에서는 result.isFailure() 메서드 호출이지만,
    //   Kotlin에서는 result.isFailure 프로퍼티 접근 스타일로 사용한다
    fun publish(event: ChannelEvent) {
        val result = sinks.tryEmitNext(event) // 이벤트 발행 시도
        if (result.isFailure) { // 발행 실패 시 경고 로그 (전체 흐름은 중단하지 않음)
            log.warn("이벤트 발행 실패: {}", result)
        }
    }

    // 이벤트 스트림 반환 — 구독자가 Flux로 이벤트를 수신한다
    // asFlux(): Sinks를 구독 가능한 Flux로 변환한다
    // 여러 번 호출해도 같은 Sinks에서 발행한 이벤트를 공유한다 (multicast 특성)
    // InventorySyncService, SSE Controller(Phase 5) 등에서 이 메서드를 호출하여 구독한다
    fun getEventStream(): Flux<ChannelEvent> = sinks.asFlux()
}
