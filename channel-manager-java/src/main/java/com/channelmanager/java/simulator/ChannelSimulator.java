package com.channelmanager.java.simulator; // 시뮬레이터 패키지 - 채널 예약 시뮬레이션

import com.channelmanager.java.dto.ReservationCreateRequest; // 예약 생성 요청 DTO
import com.channelmanager.java.dto.ReservationResponse; // 예약 응답 DTO
import org.slf4j.Logger; // SLF4J 로거 인터페이스
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import io.github.resilience4j.circuitbreaker.CircuitBreaker; // Phase 27: 서킷 브레이커
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator; // Phase 27: Reactor 연산자
import io.github.resilience4j.reactor.retry.RetryOperator; // Phase 27: 재시도 Reactor 연��자
import io.github.resilience4j.retry.Retry; // Phase 27: 재시도
import org.springframework.stereotype.Service; // 서비스 계층 어노테이션
import org.springframework.web.reactive.function.client.WebClient; // 논블로킹 HTTP 클라이언트
import reactor.core.Disposable; // 구독 핸들 (취소 가능)
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.time.Duration; // 시간 간격 표현 (초, 밀리초 등)
import java.time.LocalDate; // 날짜 타입
import java.util.List; // 리스트 컬렉션
import java.util.concurrent.ThreadLocalRandom; // 스레드 안전한 난수 생성기

// 채널 시뮬레이터 — 실제 OTA의 예약 요청을 시뮬레이션한다
// Flux.interval로 주기적으로 랜덤 예약 요청을 생성하고,
// WebClient로 자기 자신의 예약 API(POST /api/reservations)를 호출한다
// 서버 기동 시 자동 시작하지 않고, API로 시작/중지를 제어한다
// @Service로 Spring 빈으로 등록한다
// Kotlin에서는 primary constructor에 val로 의존성을 선언하지만,
// Java에서는 생성자로 final 필드를 주입한다
@Service
public class ChannelSimulator {

    // SLF4J 로거 — 시뮬레이터 동작 상황을 로그로 출력한다
    // Kotlin에서는 인스턴스 레벨에서 LoggerFactory.getLogger()를 호출하지만,
    // Java에서는 static final로 클래스 레벨에서 선언하는 것이 관례이다
    private static final Logger log = LoggerFactory.getLogger(ChannelSimulator.class);

    // WebClientConfig에서 등록한 WebClient 빈
    private final WebClient webClient;
    // Phase 27: Resilience4j 서킷 브레이커 + 재시도
    private final CircuitBreaker reservationApiCircuitBreaker;
    private final Retry reservationApiRetry;

    // Disposable: subscribe()가 반환하는 구독 핸들
    // dispose()를 호출하면 구독이 취소되어 Flux.interval이 중지된다
    // Kotlin에서는 Disposable?으로 nullable을 표현하지만,
    // Java에서는 null 체크를 명시적으로 수행해야 한다
    private Disposable disposable;

    // 시뮬레이터 실행 상태 플래그
    // SimulatorController에서 상태를 조회할 때 사용한다
    // Kotlin에서는 val running + private set으로 외부 읽기 전용을 표현하지만,
    // Java에서는 private 필드 + public getter로 동일한 효과를 얻는다
    private boolean running = false;

    // 활성 채널 코드 목록 — 시뮬레이터가 사용하는 채널
    // TRIP(Trip.com)은 비활성 채널이므로 제외한다 (V7 샘플 데이터 기준)
    // Kotlin에서는 listOf()를 사용하지만, Java에서는 List.of()를 사용한다
    private static final List<String> ACTIVE_CHANNELS = List.of(
        "DIRECT", "BOOKING", "AGODA"
    );

    // 투숙객 이름 풀 — 랜덤으로 선택하여 현실적인 예약을 생성한다
    // 한국어, 영어, 일본어 이름을 혼합하여 다국적 투숙객을 시뮬레이션한다
    private static final List<String> GUEST_NAMES = List.of(
        "김민준", "이서윤", "박지호", "최수아", "정도현",
        "James Wilson", "Emily Johnson", "Michael Brown",
        "田中太郎", "佐藤花子", "鈴木一郎"
    );

    // 생성자 — WebClient를 주입받는다
    // Kotlin에서는 primary constructor에 val로 선언하면 자동 주입되지만,
    // Java에서는 명시적 생성자가 필요하다 (@RequiredArgsConstructor도 가능하지만
    // Disposable 같은 non-final 필드가 있으므로 명시적 생성자를 사용한다)
    public ChannelSimulator(WebClient webClient,
                            CircuitBreaker reservationApiCircuitBreaker,
                            Retry reservationApiRetry) {
        this.webClient = webClient;
        this.reservationApiCircuitBreaker = reservationApiCircuitBreaker;
        this.reservationApiRetry = reservationApiRetry;
    }

    // 시뮬레이터 실행 상태 getter
    public boolean isRunning() {
        return running;
    }

    // 시뮬레이터 시작
    // 이미 실행 중이면 무시한다 (중복 시작 방지)
    // Flux.interval(3초)로 3초마다 랜덤 예약 요청을 생성한다
    // flatMap 내부에서 onErrorResume으로 개별 예약 실패를 처리하여
    // 하나의 예약이 실패해도 시뮬레이터 전체가 중단되지 않도록 한다
    // Kotlin에서는 if (running) return으로 간결하게 작성하지만,
    // Java에서도 동일한 패턴을 사용한다
    public void start() {
        if (running) return; // 이미 실행 중이면 무시

        running = true; // 상태 플래그 변경
        log.info("채널 시뮬레이터 시작"); // 시작 로그

        disposable = Flux.interval(Duration.ofSeconds(3)) // 3초마다 tick 발행
            .flatMap(tick -> { // 각 tick에 대해 랜덤 예약 생성 시도
                ReservationCreateRequest request = generateRandomRequest(); // 랜덤 예약 요청 생성
                log.info(
                    "예약 요청: channel={}, roomType={}, checkIn={}, guest={}",
                    request.channelCode(), request.roomTypeId(),
                    request.checkInDate(), request.guestName()
                );

                return webClient.post() // POST 요청 시작
                    .uri("/api/reservations") // 예약 API 엔드포인트
                    .bodyValue(request) // 요청 본문 설정
                    .retrieve() // 응답 추출 시작
                    .bodyToMono(ReservationResponse.class) // 응답 본문을 DTO로 변환
                    // Phase 27: 재시도 (내부) → 서킷 브레이커 (외부) 순서로 적용
                    // Retry가 먼저 적용되어 일시적 실패를 재시도하고,
                    // CB가 나중에 적용되어 최종 결과를 기록한다 (CB OPEN 시 즉시 실패)
                    .transformDeferred(RetryOperator.of(reservationApiRetry))
                    .transformDeferred(CircuitBreakerOperator.of(reservationApiCircuitBreaker))
                    .doOnNext(response -> // 성공 시 로그 출력
                        log.info(
                            "예약 성공: id={}, channel={}, guest={}, price={}",
                            response.id(), response.channelCode(),
                            response.guestName(), response.totalPrice()
                        )
                    )
                    .onErrorResume(e -> { // 최종 실패 시 폴백 (재시도 모두 실패 후)
                        log.warn("예약 실패 (폴백): {}", e.getMessage());
                        return Mono.empty(); // 에러를 무시하고 다음 tick 계속
                    });
            })
            .subscribe(); // 백그라운드 구독 시작
    }

    // 시뮬레이터 중지
    // Disposable.dispose()를 호출하여 구독을 취소한다
    // Kotlin에서는 disposable?.dispose() (safe call)로 null을 안전하게 처리하지만,
    // Java에서는 명시적 null 체크가 필요하다
    public void stop() {
        if (!running) return; // 실행 중이 아니면 무시

        running = false; // 상태 플래그 변경
        if (disposable != null && !disposable.isDisposed()) { // null 체크 + 이미 취소 확인
            disposable.dispose(); // 구독 취소 → Flux.interval 중지
        }
        log.info("채널 시뮬레이터 중지"); // 중지 로그
    }

    // 랜덤 예약 요청 생성 — 현실적인 호텔 예약 데이터를 생성한다
    // ThreadLocalRandom: 멀티 스레드 환경에서 안전한 난수 생성기
    // Kotlin에서는 listOf().random()과 범위 표현식(1..30)을 사용하지만,
    // Java에서는 ThreadLocalRandom.current().nextInt()를 사용한다
    private ReservationCreateRequest generateRandomRequest() {
        ThreadLocalRandom random = ThreadLocalRandom.current(); // 현재 스레드의 난수 생성기
        String channelCode = ACTIVE_CHANNELS.get( // 활성 채널 중 랜덤 선택
            random.nextInt(ACTIVE_CHANNELS.size())
        );
        long roomTypeId = random.nextLong(1, 6); // 객실 타입 ID 1~5 중 랜덤
        int daysAhead = random.nextInt(1, 31); // 1~30일 후 체크인
        LocalDate checkInDate = LocalDate.now().plusDays(daysAhead); // 체크인 날짜
        int nights = random.nextInt(1, 4); // 1~3박 숙박
        LocalDate checkOutDate = checkInDate.plusDays(nights); // 체크아웃 날짜
        String guestName = GUEST_NAMES.get( // 투숙객 이름 랜덤 선택
            random.nextInt(GUEST_NAMES.size())
        );
        int roomQuantity = random.nextInt(100) < 80 ? 1 : 2;
        // 80% 확률로 1실, 20% 확률로 2실 — 실제 예약 패턴을 반영

        return new ReservationCreateRequest( // record 생성
            channelCode,
            roomTypeId,
            checkInDate,
            checkOutDate,
            guestName,
            roomQuantity
        );
    }
}
