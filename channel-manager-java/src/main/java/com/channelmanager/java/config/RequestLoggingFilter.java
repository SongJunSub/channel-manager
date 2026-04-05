package com.channelmanager.java.config; // 설정 패키지

import org.slf4j.Logger; // SLF4J 로거 인터페이스
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import org.slf4j.MDC; // Mapped Diagnostic Context — 요청별 컨텍스트 데이터
import org.springframework.core.annotation.Order; // 필터 실행 순서
import org.springframework.stereotype.Component; // 빈 등록
import org.springframework.web.server.ServerWebExchange; // HTTP 요청/응답 래퍼
import org.springframework.web.server.WebFilter; // WebFlux 필터 인터페이스
import org.springframework.web.server.WebFilterChain; // 필터 체인
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.util.UUID; // 고유 ID 생성

// 요청 로깅 필터 — 모든 HTTP 요청에 대해 MDC 컨텍스트를 설정하고 요청/응답을 로깅한다
// WebFilter: Spring WebFlux에서 HTTP 요청을 가로채는 필터 인터페이스
//   — Spring MVC의 javax.servlet.Filter에 대응한다
//   — 모든 요청이 이 필터를 통과한 후 컨트롤러에 도달한다
// MDC(Mapped Diagnostic Context): 로그에 요청별 컨텍스트 데이터를 자동으로 포함시킨다
//   — logback-spring.xml의 %X{requestId}가 MDC에서 값을 읽어 로그에 출력한다
//   — JSON 로그(LogstashEncoder)에서는 자동으로 JSON 필드로 포함된다
// @Order(Integer.MIN_VALUE): 가장 먼저 실행되는 필터
// Kotlin에서는 companion object에 로거를 선언하지만,
// Java에서는 private static final로 선언한다
@Component
@Order(Integer.MIN_VALUE)
public class RequestLoggingFilter implements WebFilter {

    // SLF4J 로거
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // filter — 모든 HTTP 요청이 이 메서드를 통과한다
    // exchange: HTTP 요청(request)과 응답(response)을 모두 포함하는 래퍼 객체
    // chain: 다음 필터 또는 컨트롤러로 요청을 전달하는 체인
    // Kotlin에서는 override fun filter(...)이지만,
    // Java에서는 @Override public Mono<Void> filter(...)이다
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 요청별 고유 ID 생성 — UUID의 앞 8자리만 사용하여 간결하게
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        // HTTP 요청 정보 추출
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();

        // MDC에 요청 컨텍스트 설정
        // MDC.put(): 현재 스레드의 MDC에 키-값 쌍을 저장한다
        MDC.put("requestId", requestId);
        MDC.put("method", method);
        MDC.put("path", path);

        // 요청 시작 로그
        log.info("요청 시작: {} {}", method, path);

        // 요청 처리 시작 시각 기록
        long startTime = System.currentTimeMillis();

        // chain.filter(): 다음 필터 또는 컨트롤러로 요청을 전달한다
        // doFinally: 요청 처리가 완료되면 MDC를 정리하고 응답 시간을 로깅한다
        // Kotlin에서는 { } 람다, Java에서는 signal -> { } 람다
        return chain.filter(exchange)
            .doFinally(signal -> {
                // 응답 처리 시간 계산
                long duration = System.currentTimeMillis() - startTime;
                // 응답 상태 코드
                int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;

                // 응답 완료 로그
                log.info("요청 완료: {} {} → {} ({}ms)", method, path, statusCode, duration);

                // MDC 정리
                MDC.clear();
            });
    }
}
