package com.channelmanager.kotlin.controller // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.kotlin.dto.EventResponse // 이벤트 응답 DTO
import com.channelmanager.kotlin.repository.ChannelEventRepository // 채널 이벤트 리포지토리
import com.channelmanager.kotlin.service.EventPublisher // 이벤트 발행 서비스 (Sinks)
import org.slf4j.LoggerFactory // SLF4J 로거 팩토리
import org.springframework.http.MediaType // HTTP 미디어 타입 상수
import org.springframework.http.codec.ServerSentEvent // SSE 이벤트 래퍼 타입
import org.springframework.web.bind.annotation.GetMapping // GET 메서드 매핑
import org.springframework.web.bind.annotation.RequestParam // 쿼리 파라미터 바인딩
import org.springframework.web.bind.annotation.RestController // REST 컨트롤러 선언
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import java.time.Duration // 시간 간격 표현

// SSE(Server-Sent Events) 이벤트 스트리밍 컨트롤러
// Phase 4에서 구현한 EventPublisher(Sinks)의 이벤트를 브라우저로 실시간 전송한다
// 두 가지 엔드포인트를 제공한다:
//   1. GET /api/events/stream — SSE 실시간 스트림 (무한 연결, text/event-stream)
//   2. GET /api/events — 최근 이벤트 목록 조회 (일반 JSON 응답)
// @RestController: 모든 메서드의 반환값이 자동으로 직렬화된다
@RestController
class EventStreamController(
    private val eventPublisher: EventPublisher, // Sinks 이벤트 허브 — 실시간 이벤트 스트림 제공
    private val channelEventRepository: ChannelEventRepository // DB에서 과거 이벤트 조회
) {

    // SLF4J 로거 — companion object에 선언하여 클래스 레벨 로거로 사용한다
    companion object {
        private val log = LoggerFactory.getLogger(
            EventStreamController::class.java
        ) // 로거 인스턴스
    }

    // SSE 실시간 이벤트 스트림
    // GET /api/events/stream
    // 브라우저의 EventSource가 이 엔드포인트에 연결하여 실시간 이벤트를 수신한다
    // produces = [TEXT_EVENT_STREAM_VALUE]: Content-Type을 text/event-stream으로 설정한다
    //   — Spring WebFlux가 HTTP 연결을 유지하면서 이벤트를 하나씩 전송한다
    // 반환 타입 Flux<ServerSentEvent<EventResponse>>:
    //   — Flux: 무한 스트림 (클라이언트가 연결을 끊을 때까지 이벤트를 계속 전송)
    //   — ServerSentEvent: SSE 프로토콜의 event, id, data 필드를 제어하는 래퍼 타입
    //   — EventResponse: 실제 전송되는 이벤트 데이터 (ChannelEvent → DTO 변환)
    @GetMapping("/api/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(): Flux<ServerSentEvent<EventResponse>> {
        // 실제 이벤트 스트림 — EventPublisher(Sinks)에서 발행되는 이벤트를 SSE로 변환한다
        // eventPublisher.getEventStream(): Sinks.asFlux()를 호출하여 Flux<ChannelEvent>를 반환
        // .map: 각 ChannelEvent를 ServerSentEvent<EventResponse>로 변환한다
        val eventStream = eventPublisher.getEventStream() // Sinks → Flux<ChannelEvent>
            .map { event -> // 각 이벤트를 SSE 형식으로 래핑한다
                // ServerSentEvent.builder<T>(): SSE 이벤트를 빌더 패턴으로 생성한다
                // Kotlin에서는 builder<EventResponse>()로 타입 파라미터를 메서드 뒤에 명시한다
                // Java에서는 ServerSentEvent.<EventResponse>builder()로 메서드 앞에 명시한다
                ServerSentEvent.builder<EventResponse>()
                    // .id(): SSE의 id: 필드 — 클라이언트 재연결 시 Last-Event-ID로 전송된다
                    // toString(): Long → String 변환 (SSE id는 문자열)
                    .id(event.id.toString())
                    // .event(): SSE의 event: 필드 — 클라이언트가 addEventListener로 타입별 구분
                    // eventType.name: enum의 이름 문자열 (RESERVATION_CREATED 등)
                    // Kotlin에서는 .name 프로퍼티, Java에서는 .name() 메서드
                    .event(event.eventType.name)
                    // .data(): SSE의 data: 필드 — JSON으로 직렬화되어 전송된다
                    // EventResponse.from(): ChannelEvent 엔티티 → DTO 변환
                    .data(EventResponse.from(event))
                    .build() // ServerSentEvent 인스턴스 생성
            }
            // doOnSubscribe: 클라이언트가 SSE 스트림에 구독할 때 로그를 출력한다
            // 디버깅 및 모니터링 목적 — 누가 언제 연결했는지 추적할 수 있다
            .doOnSubscribe { log.info("SSE 클라이언트 연결됨") }
            // doOnCancel: 클라이언트가 연결을 끊을 때 로그를 출력한다
            // EventSource.close() 또는 브라우저 탭 닫기 시 호출된다
            .doOnCancel { log.info("SSE 클라이언트 연결 해제됨") }

        // 하트비트 스트림 — 30초마다 빈 comment 이벤트를 전송하여 연결을 유지한다
        // 프록시, 로드밸런서, CDN은 유휴(idle) 연결을 일정 시간 후 끊는다 (보통 60~120초)
        // 하트비트로 주기적으로 데이터를 보내면 "이 연결은 활성 상태"라고 인식시킨다
        // Flux.interval(Duration): 지정한 간격마다 0, 1, 2, ... 숫자를 발행하는 무한 스트림
        val heartbeat = Flux.interval(Duration.ofSeconds(30)) // 30초 간격
            .map { // 숫자 값은 사용하지 않고, SSE comment 이벤트를 생성한다
                // .comment("heartbeat"): SSE의 : heartbeat (콜론으로 시작하는 줄)
                // comment는 클라이언트의 이벤트 핸들러에 전달되지 않지만, HTTP 연결은 유지된다
                // data 없이 comment만 있는 이벤트 — 클라이언트에서 무시되므로 부작용 없음
                ServerSentEvent.builder<EventResponse>()
                    .comment("heartbeat") // SSE 주석 — 클라이언트에서 무시됨
                    .build()
            }

        // Flux.merge(): 두 스트림을 하나로 합친다 (Phase 4에서 학습한 패턴)
        // 실제 이벤트가 없는 동안에도 하트비트가 30초마다 전송되어 연결이 유지된다
        // 이벤트가 발생하면 하트비트와 관계없이 즉시 전송된다
        return Flux.merge(eventStream, heartbeat)
    }

    // 최근 이벤트 목록 조회
    // GET /api/events?limit=50
    // SSE는 구독 이후의 이벤트만 수신하므로, 과거 이벤트는 이 API로 별도 조회한다
    // 대시보드 초기 로딩 시 최근 이벤트를 표시하는 데 사용한다
    // @RequestParam: URL 쿼리 파라미터를 메서드 인자로 바인딩한다
    //   defaultValue = "50": limit 파라미터가 없으면 기본값 50을 사용한다
    //   required = false: 파라미터가 없어도 요청이 유효하다
    @GetMapping("/api/events")
    fun getRecentEvents(
        @RequestParam(defaultValue = "50", required = false) limit: Int // 조회할 이벤트 수
    ): Flux<EventResponse> =
        channelEventRepository.findAllByOrderByCreatedAtDesc() // DB에서 최신순 전체 조회
            // .take(limit.toLong()): 앞에서 limit개만 가져온다
            // toLong(): take()의 파라미터 타입이 Long이므로 Int → Long 변환
            // R2DBC 쿼리 메서드에 LIMIT을 직접 적용하기 어려우므로,
            // Flux 연산자로 스트림 레벨에서 제한한다
            .take(limit.toLong())
            // .map: ChannelEvent 엔티티를 EventResponse DTO로 변환한다
            .map { EventResponse.from(it) }
}
