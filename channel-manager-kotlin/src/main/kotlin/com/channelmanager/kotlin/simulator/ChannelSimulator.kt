package com.channelmanager.kotlin.simulator // 시뮬레이터 패키지 - 채널 예약 시뮬레이션

import com.channelmanager.kotlin.dto.ReservationCreateRequest // 예약 생성 요청 DTO
import com.channelmanager.kotlin.dto.ReservationResponse // 예약 응답 DTO
import io.github.resilience4j.circuitbreaker.CircuitBreaker // Phase 27: 서킷 브레이커
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator // Phase 27: 서킷 브레이커 Reactor 연산자
import io.github.resilience4j.reactor.retry.RetryOperator // Phase 27: 재시도 Reactor 연산자
import io.github.resilience4j.retry.Retry // Phase 27: 재시도
import org.slf4j.LoggerFactory // SLF4J 로거 팩토리
import org.springframework.stereotype.Service // 서비스 계층 어노테이션
import org.springframework.web.reactive.function.client.WebClient // 논블로킹 HTTP 클라이언트
import reactor.core.Disposable // 구독 핸들 (취소 가능)
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.time.Duration // 시간 간격 표현 (초, 밀리초 등)
import java.time.LocalDate // 날짜 타입
import java.util.concurrent.ThreadLocalRandom // 스레드 안전한 난수 생성기

// 채널 시뮬레이터 — 실제 OTA의 예약 요청을 시뮬레이션한다
// Flux.interval로 주기적으로 랜덤 예약 요청을 생성하고,
// WebClient로 자기 자신의 예약 API(POST /api/reservations)를 호출한다
// 서버 기동 시 자동 시작하지 않고, API로 시작/중지를 제어한다
// @Service로 Spring 빈으로 등록한다
@Service
class ChannelSimulator(
    private val webClient: WebClient,                              // WebClientConfig에서 등록한 WebClient 빈 주입
    private val reservationApiCircuitBreaker: CircuitBreaker,      // Phase 27: 서킷 브레이커
    private val reservationApiRetry: Retry                          // Phase 27: 재시도
) {

    // SLF4J 로거 — 시뮬레이터 동작 상황을 로그로 출력한다
    // Kotlin에서는 companion object가 아닌 인스턴스 레벨에서 로거를 생성할 수 있다
    private val log = LoggerFactory.getLogger(ChannelSimulator::class.java)

    // Disposable: subscribe()가 반환하는 구독 핸들
    // dispose()를 호출하면 구독이 취소되어 Flux.interval이 중지된다
    // nullable 타입(?): 시뮬레이터가 시작되지 않았을 때는 null
    private var disposable: Disposable? = null

    // 시뮬레이터 실행 상태 플래그
    // SimulatorController에서 상태를 조회할 때 사용한다
    // kotlin("plugin.spring")이 클래스를 open으로 만들기 때문에 private set 사용 불가
    // 대신 별도의 private 변수(_running)와 public getter(running)를 사용한다
    private var _running: Boolean = false // 실제 상태를 저장하는 private 변수
    val running: Boolean get() = _running // 외부에서 읽기만 가능한 프로퍼티

    // 활성 채널 코드 목록 — 시뮬레이터가 사용하는 채널
    // TRIP(Trip.com)은 비활성 채널이므로 제외한다 (V7 샘플 데이터 기준)
    private val activeChannels = listOf("DIRECT", "BOOKING", "AGODA")

    // 투숙객 이름 풀 — 랜덤으로 선택하여 현실적인 예약을 생성한다
    // 한국어, 영어, 일본어 이름을 혼합하여 다국적 투숙객을 시뮬레이션한다
    private val guestNames = listOf(
        "김민준", "이서윤", "박지호", "최수아", "정도현",   // 한국어 이름
        "James Wilson", "Emily Johnson", "Michael Brown",  // 영어 이름
        "田中太郎", "佐藤花子", "鈴木一郎"                  // 일본어 이름
    )

    // 시뮬레이터 시작
    // 이미 실행 중이면 무시한다 (중복 시작 방지)
    // Flux.interval(3초)로 3초마다 랜덤 예약 요청을 생성한다
    // flatMap 내부에서 onErrorResume으로 개별 예약 실패를 처리하여
    // 하나의 예약이 실패해도 시뮬레이터 전체가 중단되지 않도록 한다
    fun start() {
        if (_running) return // 이미 실행 중이면 무시

        _running = true // 상태 플래그 변경
        log.info("채널 시뮬레이터 시작") // 시작 로그

        disposable = Flux.interval(Duration.ofSeconds(3)) // 3초마다 tick 발행 (0, 1, 2, ...)
            .flatMap { // 각 tick에 대해 랜덤 예약 생성 시도
                val request = generateRandomRequest() // 랜덤 예약 요청 생성
                log.info( // 요청 내용 로그 출력
                    "예약 요청: channel={}, roomType={}, checkIn={}, guest={}",
                    request.channelCode, request.roomTypeId,
                    request.checkInDate, request.guestName
                )

                webClient.post() // POST 요청 시작
                    .uri("/api/reservations") // 예약 API 엔드포인트
                    .bodyValue(request) // 요청 본문 설정
                    .retrieve() // 응답 추출 시작
                    .bodyToMono(ReservationResponse::class.java) // 응답 본문을 DTO로 변환
                    // Phase 27: 재���도 적용 (내부) — 일시적 실패 시 최대 3회까지 1초 간격으로 재시도
                    // RetryOperator가 먼저(내부에) 적용되어 재시도를 수행한다
                    .transformDeferred(RetryOperator.of(reservationApiRetry))
                    // Phase 27: 서킷 브레이커 적용 (외부) — 최종 실패율이 임계값을 넘으면 호출 차단
                    // CircuitBreakerOperator가 나중에(외부에) 적용되어 재시도 후 최종 결과를 기록한다
                    // CB가 OPEN이면 재시도 없이 즉시 CallNotPermittedException → 빠른 실패
                    .transformDeferred(CircuitBreakerOperator.of(reservationApiCircuitBreaker))
                    .doOnNext { response -> // 성공 시 로그 출력
                        log.info(
                            "예약 성공: id={}, channel={}, guest={}, price={}",
                            response.id, response.channelCode,
                            response.guestName, response.totalPrice
                        )
                    }
                    .onErrorResume { e -> // 최종 실패 시 폴백 (재시도 모두 실패 후)
                        // 서킷 OPEN 시: CallNotPermittedException
                        // 재시도 소진 시: 원래 예외
                        log.warn("예약 실패 (폴백): {}", e.message)
                        Mono.empty() // 에러를 무시하고 다음 tick 계속
                    }
            }
            .subscribe() // 백그라운드 구독 시작 — HTTP 요청과 무관한 독립 실행이므로 직접 subscribe
    }

    // 시뮬레이터 중지
    // Disposable.dispose()를 호출하여 구독을 취소한다
    // ?. (safe call): disposable이 null이면 아무것도 실행하지 않는다
    // isDisposed 확인: 이미 취소된 구독을 다시 취소하지 않도록 방어한다
    fun stop() {
        if (!_running) return // 실행 중이 아니면 무시

        _running = false // 상태 플래그 변경
        disposable?.dispose() // 구독 취소 → Flux.interval 중지
        log.info("채널 시뮬레이터 중지") // 중지 로그
    }

    // 랜덤 예약 요청 생성 — 현실적인 호텔 예약 데이터를 생성한다
    // ThreadLocalRandom: 멀티 스레드 환경에서 안전한 난수 생성기
    // Kotlin의 random() 확장 함수와 범위 표현식(IntRange)을 활용하여 간결하게 구현한다
    private fun generateRandomRequest(): ReservationCreateRequest {
        val channelCode = activeChannels.random() // 활성 채널 중 랜덤 선택
        val roomTypeId = (1L..5L).random() // 객실 타입 ID 1~5 중 랜덤 (V7 샘플 데이터 기준)
        val daysAhead = (1..30).random() // 1~30일 후 체크인
        val checkInDate = LocalDate.now().plusDays(daysAhead.toLong()) // 체크인 날짜
        val nights = (1..3).random() // 1~3박 숙박
        val checkOutDate = checkInDate.plusDays(nights.toLong()) // 체크아웃 날짜
        val guestName = guestNames.random() // 투숙객 이름 랜덤 선택
        val roomQuantity = if (ThreadLocalRandom.current().nextInt(100) < 80) 1 else 2
        // 80% 확률로 1실, 20% 확률로 2실 — 실제 예약 패턴을 반영

        return ReservationCreateRequest( // DTO 생성
            channelCode = channelCode,
            roomTypeId = roomTypeId,
            checkInDate = checkInDate,
            checkOutDate = checkOutDate,
            guestName = guestName,
            roomQuantity = roomQuantity
        )
    }
}
