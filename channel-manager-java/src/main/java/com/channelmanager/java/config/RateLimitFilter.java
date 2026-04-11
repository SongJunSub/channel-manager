package com.channelmanager.java.config; // 설정 패키지

import io.github.bucket4j.Bandwidth; // Bucket4j 대역폭 설정 (토큰 충전 규칙)
import io.github.bucket4j.Bucket; // Token Bucket 구현체
import org.slf4j.Logger; // SLF4J 로거 인터페이스
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import org.springframework.core.annotation.Order; // 필터 실행 순서
import org.springframework.http.HttpStatus; // HTTP 상태 코드
import org.springframework.http.MediaType; // Content-Type
import org.springframework.stereotype.Component; // 빈 등록
import org.springframework.web.server.ServerWebExchange; // HTTP 요청/응답 래퍼
import org.springframework.web.server.WebFilter; // WebFlux 필터 인터페이스
import org.springframework.web.server.WebFilterChain; // 필터 체인
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림

import java.nio.charset.StandardCharsets; // 문자 인코딩
import java.time.Duration; // 시간 간격
import java.util.List; // 리스트
import java.util.Set; // 집합
import java.util.concurrent.ConcurrentHashMap; // 스레드 안전 해시맵

// Rate Limiting 필터 — Token Bucket 알고리즘으로 IP별 API 호출 횟수를 제한한다
// Bucket4j: Java 기반 Token Bucket 구현 라이브러리
// Token Bucket 알고리즘:
//   - 버킷에 토큰이 일정 속도로 충전된다
//   - 요청 시 토큰 1개를 소비한다
//   - 토큰이 없으면 429 Too Many Requests를 반환한다
// @Order(Integer.MIN_VALUE + 1): RequestLoggingFilter(MIN_VALUE) 다음에 실행
//   — 로깅 필터가 먼저 MDC를 설정한 후, Rate Limiting이 적용된다
// Kotlin에서는 @Order(Int.MIN_VALUE + 1)이지만,
// Java에서는 @Order(Integer.MIN_VALUE + 1)이다
@Component
@Order(Integer.MIN_VALUE + 1)
public class RateLimitFilter implements WebFilter {

    // SLF4J 로거
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Rate Limiting 설정값 — application.yml에서 오버라이드 가능
    // 테스트 환경에서는 더 큰 값을 설정하여 테스트 안정성을 확보한다
    @org.springframework.beans.factory.annotation.Value("${rate-limit.capacity:50}")
    private long capacity = 50;          // 버킷 최대 용량 (초당 최대 50건 버스트)

    @org.springframework.beans.factory.annotation.Value("${rate-limit.refill-tokens:50}")
    private long refillTokens = 50;     // 충전할 토큰 수

    private final Duration refillDuration = Duration.ofSeconds(1); // 충전 주기 (1초)

    // IP별 버킷 저장소 — ConcurrentHashMap으로 스레드 안전하게 관리
    // Key: 클라이언트 IP 주소
    // Value: 해당 IP에 할당된 Token Bucket
    // 주의: 이 맵은 무제한 성장한다 (IP별 엔트리가 영구 유지됨)
    //   프로덕션 환경에서는 Caffeine 캐시(TTL + 최대 크기)로 교체하여 메모리 누수를 방지해야 한다
    //   예: Caffeine.newBuilder().expireAfterAccess(10, MINUTES).maximumSize(10_000).build()
    // Kotlin에서는 ConcurrentHashMap<String, Bucket>()이지만,
    // Java에서는 new ConcurrentHashMap<>()이다
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Rate Limiting을 적용하지 않는 경로 목록
    // 정적 리소스, API 문서, SSE 스트림은 제한에서 제외한다
    // Kotlin에서는 listOf()이지만, Java에서는 List.of()이다
    private final List<String> excludedPaths = List.of(
        "/webjars/",         // Swagger UI 정적 리소스
        "/v3/api-docs",      // OpenAPI 스펙 JSON
        "/swagger-ui",       // Swagger UI 페이지
        "/index.html",       // 실시간 대시보드
        "/css/",             // CSS 정적 리소스
        "/js/",              // JS 정적 리소스
        "/api/events/stream", // SSE 스트림 (장기 연결이므로 제외)
        "/actuator"          // Phase 19: Actuator 엔드포인트 (Prometheus 스크래핑, /actuator 및 /actuator/* 모두 매칭)
    );

    // 루트 경로("/")도 제외 — Spring Boot가 "/" → index.html로 리다이렉트한다
    // startsWith 매칭이므로 별도 정확 일치 체크가 필요하다
    // Kotlin에서는 setOf()이지만, Java에서는 Set.of()이다
    private final Set<String> excludedExactPaths = Set.of("/");

    // filter — 모든 HTTP 요청에 Rate Limiting을 적용한다
    // Kotlin에서는 override fun filter(...)이지만,
    // Java에서는 @Override public Mono<Void> filter(...)이다
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath(); // 요청 경로

        // 제외 경로 체크 — 정적 리소스, API 문서, SSE, 루트 경로는 제한하지 않는다
        // Kotlin에서는 excludedPaths.any { path.startsWith(it) }이지만,
        // Java에서는 stream().anyMatch()를 사용한다
        if (excludedExactPaths.contains(path) || excludedPaths.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange); // Rate Limiting 없이 다음 필터로 전달
        }

        // 클라이언트 IP 추출
        // remoteAddress: 클라이언트의 소켓 주소 (IP + 포트)
        // getHostAddress(): IP 주소만 추출 (포트 제외)
        // 주의: 리버스 프록시(Nginx, ALB) 뒤에서는 모든 요청이 프록시 IP로 보인다
        //   프로덕션에서는 X-Forwarded-For 헤더를 먼저 확인해야 한다
        String clientIp = exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";

        // IP별 버킷 조회 또는 생성
        // computeIfAbsent: 키가 없으면 새 버킷을 생성하여 저장한다
        // 같은 IP의 요청은 동일한 버킷을 공유하여 전체 요청 수를 제한한다
        // Kotlin에서는 { createBucket() } 람다이지만,
        // Java에서는 key -> createBucket() 람다이다
        Bucket bucket = buckets.computeIfAbsent(clientIp, key -> createBucket());

        // 토큰 소비 시도 — 1개의 토큰을 소비한다
        // tryConsume(1): CAS 기반 동기 호출 — 인메모리 로컬 버킷은 나노초 수준으로 논블로킹에 가깝다
        //   분산 버킷(Redis 등)으로 교체 시에는 asAsync().tryConsume(1)을 사용해야 Netty 이벤트 루프 블로킹을 방지한다
        if (bucket.tryConsume(1)) {
            // 토큰이 있으면 요청을 다음 필터/컨트롤러로 전달한다
            return chain.filter(exchange);
        } else {
            // 토큰이 없으면 429 Too Many Requests 응답을 반환한다
            log.warn("Rate limit 초과: IP={}, path={}", clientIp, path);
            return createRateLimitResponse(exchange);
        }
    }

    // 새 Token Bucket 생성
    // Bandwidth.builder(): 대역폭 설정 빌더
    //   - capacity(): 버킷 최대 용량 (최대 토큰 수)
    //   - refillGreedy(): 충전 주기 동안 토큰을 연속적으로 분배하여 충전
    //     - refillGreedy(50, 1초) → 1초 동안 50개를 균등하게 분배 (20ms당 1개씩)
    //     - refillIntervally: 충전 주기 끝에 한꺼번에 충전하는 방식 (더 거친 버스트)
    private Bucket createBucket() {
        return Bucket.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(capacity)                           // 버킷 최대 용량
                    .refillGreedy(refillTokens, refillDuration)   // 1초마다 토큰 충전
                    .build()
            )
            .build();
    }

    // 429 Too Many Requests 응답 생성
    // Retry-After 헤더: 클라이언트가 재시도할 수 있는 시간(초)
    // 응답 본문: 기존 ErrorResponse 형식과 동일한 JSON 구조
    private Mono<Void> createRateLimitResponse(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);          // 429 상태 코드
        response.getHeaders().set("Retry-After", "1");                 // 1초 후 재시도 안내
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON); // JSON 응답

        // JSON 응답 본문 — ErrorResponse 형식과 동일
        String body = "{\"message\":\"요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도해주세요.\"}";
        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8)); // 문자열을 UTF-8 DataBuffer로 변환
        return response.writeWith(Mono.just(buffer));                // 응답 본문 작성
    }
}
