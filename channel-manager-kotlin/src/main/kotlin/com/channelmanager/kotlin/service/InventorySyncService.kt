package com.channelmanager.kotlin.service // 서비스 패키지 - 비즈니스 로직 계층

import com.channelmanager.kotlin.domain.ChannelEvent // 채널 이벤트 엔티티
import com.channelmanager.kotlin.domain.EventType // 이벤트 타입 enum
import com.channelmanager.kotlin.repository.ChannelEventRepository // 이벤트 리포지토리
import com.channelmanager.kotlin.repository.ChannelRepository // 채널 리포지토리
import jakarta.annotation.PostConstruct // 빈 초기화 후 실행 어노테이션
import org.slf4j.LoggerFactory // SLF4J 로거 팩토리
import org.springframework.stereotype.Service // 서비스 계층 어노테이션
import reactor.core.Disposable // 구독 핸들 (취소 가능)
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림

// 재고 동기화 서비스 — 이벤트 스트림을 구독하여 다른 채널에 재고 변경을 동기화한다
// 실제 채널 매니저에서는 Booking.com에서 예약이 발생하면,
// Agoda, Trip.com, 자사 홈페이지 등 다른 모든 채널에 변경된 재고를 알려야 한다
// 이 프로젝트에서는 실제 OTA API를 호출하지 않으므로,
// CHANNEL_SYNCED 이벤트를 DB에 기록하고 Sinks에 발행하여 동기화를 시뮬레이션한다
//
// 동작 흐름:
// 1. @PostConstruct로 애플리케이션 시작 시 이벤트 스트림 구독을 시작한다
// 2. RESERVATION_CREATED 이벤트가 발생하면 syncToOtherChannels()가 호출된다
// 3. 예약이 발생한 채널을 제외한 나머지 활성 채널 목록을 조회한다
// 4. 각 채널에 대해 CHANNEL_SYNCED 이벤트를 생성하여 DB에 저장하고 Sinks에 발행한다
@Service
class InventorySyncService(
    private val eventPublisher: EventPublisher,              // 이벤트 발행/구독 서비스
    private val channelRepository: ChannelRepository,        // 채널 리포지토리
    private val channelEventRepository: ChannelEventRepository // 이벤트 리포지토리
) {

    // SLF4J 로거 — 동기화 동작 상황을 로그로 출력한다
    companion object {
        private val log = LoggerFactory.getLogger(InventorySyncService::class.java)
    }

    // 구독 핸들 — 구독을 취소할 때 사용한다
    // ChannelSimulator와 동일한 패턴: Disposable로 구독 생명주기를 관리한다
    // Kotlin에서는 Disposable?로 nullable을 표현한다
    private var disposable: Disposable? = null

    // @PostConstruct: Spring 빈이 생성되고 의존성 주입이 완료된 후 자동으로 호출된다
    // 애플리케이션 시작 시 이벤트 스트림 구독을 시작하여,
    // 어떤 채널에서든 예약이 발생하면 즉시 다른 채널에 동기화한다
    // Java에서도 @PostConstruct를 동일하게 사용한다
    @PostConstruct
    fun startSync() {
        // Flux.merge로 여러 이벤트 타입을 하나의 스트림으로 통합한다
        // 현재는 RESERVATION_CREATED만 처리하지만,
        // Phase 7에서 RESERVATION_CANCELLED가 추가되면 merge에 포함시킬 수 있다
        // merge: 여러 Flux를 동시에 구독하여 도착 순서대로 합친다
        val reservationEvents = eventPublisher.getEventStream() // Sinks에서 Flux로 변환
            .filter { it.eventType == EventType.RESERVATION_CREATED } // 예약 생성 이벤트만 필터

        val inventoryEvents = eventPublisher.getEventStream() // 같은 Sinks의 Flux
            .filter { it.eventType == EventType.INVENTORY_UPDATED } // 재고 변경 이벤트만 필터

        // Flux.merge: 예약 이벤트와 재고 변경 이벤트를 하나의 스트림으로 통합한다
        // 어느 소스에서든 이벤트가 도착하면 즉시 syncToOtherChannels()를 호출한다
        // flatMap: 각 이벤트에 대해 비동기 동기화 작업을 실행한다 (DB 호출 포함)
        // onErrorResume: 개별 동기화 실패가 전체 스트림을 중단시키지 않도록 한다
        //   ChannelSimulator에서 사용한 것과 동일한 패턴이다
        disposable = Flux.merge(reservationEvents, inventoryEvents) // 두 스트림 통합
            .flatMap { event -> // 각 이벤트에 대해 동기화 실행
                syncToOtherChannels(event) // 다른 채널에 동기화
                    .onErrorResume { e -> // 개별 동기화 실패 시 로그만 남기고 계속
                        log.warn("채널 동기화 실패: eventId={}, error={}", event.id, e.message)
                        Mono.empty() // 에러를 무시하고 다음 이벤트 처리
                    }
            }
            .subscribe() // 백그라운드 구독 시작 — Disposable을 반환하여 생명주기 관리
        log.info("재고 동기화 서비스 시작 — 이벤트 스트림 구독 중")
    }

    // 다른 채널에 재고 동기화 — 예약 발생 채널을 제외한 활성 채널에 CHANNEL_SYNCED 이벤트 발행
    // 1단계: 활성 채널 목록을 조회한다 (findByIsActive(true))
    // 2단계: 이벤트를 발생시킨 채널을 제외한다 (filter { it.id != event.channelId })
    // 3단계: 각 채널에 대해 CHANNEL_SYNCED 이벤트를 생성하여 DB에 저장한다
    // 4단계: 저장된 이벤트를 Sinks에 발행하여 다른 구독자(SSE 등)에게 전달한다
    // then(): Flux<ChannelEvent>의 모든 요소를 소비한 후 Mono<Void>를 반환한다
    private fun syncToOtherChannels(event: ChannelEvent): Mono<Void> =
        channelRepository.findByIsActive(true) // 활성 채널 목록 조회
            .filter { it.id != event.channelId } // 이벤트 발생 채널 제외
            .flatMap { channel -> // 각 채널에 대해 동기화 이벤트 생성
                val syncEvent = ChannelEvent( // CHANNEL_SYNCED 이벤트 생성
                    eventType = EventType.CHANNEL_SYNCED,   // 이벤트 타입: 채널 동기화
                    channelId = channel.id,                 // 동기화 대상 채널 ID
                    reservationId = event.reservationId,    // 원본 예약 ID (추적용)
                    roomTypeId = event.roomTypeId,          // 객실 타입 ID
                    // eventPayload: 동기화 사유를 JSON으로 기록한다
                    // 원본 이벤트 타입과 ID를 포함하여 추적 가능하게 한다
                    eventPayload = """{"syncFrom":"${event.eventType}",""" +
                        """"sourceEventId":${event.id},""" +
                        """"targetChannel":"${channel.channelCode}"}"""
                )
                channelEventRepository.save(syncEvent) // DB에 저장
                    .doOnNext { savedEvent -> // 저장 완료 후 Sinks에 발행 (부수 효과)
                        eventPublisher.publish(savedEvent) // 다른 구독자(SSE 등)에게 전달
                        log.info(
                            "채널 동기화: {} → {} (roomType={}, eventType={})",
                            event.channelId, channel.channelCode,
                            event.roomTypeId, event.eventType
                        )
                    }
            }
            .then() // 모든 채널 동기화 완료 시그널
}
