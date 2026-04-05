package com.channelmanager.kotlin.config // 설정 패키지

import org.slf4j.LoggerFactory // SLF4J 로거 팩토리
import org.slf4j.MDC // Mapped Diagnostic Context — 요청별 컨텍스트 데이터
import org.springframework.core.annotation.Order // 필터 실행 순서
import org.springframework.stereotype.Component // 빈 등록
import org.springframework.web.server.ServerWebExchange // HTTP 요청/응답 래퍼
import org.springframework.web.server.WebFilter // WebFlux 필터 인터페이스
import org.springframework.web.server.WebFilterChain // 필터 체인
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.util.UUID // 고유 ID 생성

// 요청 로깅 필터 — 모든 HTTP 요청에 대해 MDC 컨텍스트를 설정하고 요청/응답을 로깅한다
// WebFilter: Spring WebFlux에서 HTTP 요청을 가로채는 필터 인터페이스
//   — Spring MVC의 javax.servlet.Filter에 대응한다
//   — 모든 요청이 이 필터를 통과한 후 컨트롤러에 도달한다
// MDC(Mapped Diagnostic Context): 로그에 요청별 컨텍스트 데이터를 자동으로 포함시킨다
//   — logback-spring.xml의 %X{requestId}가 MDC에서 값을 읽어 로그에 출력한다
//   — JSON 로그(LogstashEncoder)에서는 자동으로 JSON 필드로 포함된다
// @Order(Int.MIN_VALUE): 가장 먼저 실행되는 필터 — MDC가 다른 필터/컨트롤러보다 먼저 설정되어야 한다
@Component
@Order(Int.MIN_VALUE)
class RequestLoggingFilter : WebFilter {

    // SLF4J 로거 — companion object에 선언하여 클래스 레벨 로거로 사용한다
    companion object {
        private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)
    }

    // filter — 모든 HTTP 요청이 이 메서드를 통과한다
    // exchange: HTTP 요청(request)과 응답(response)을 모두 포함하는 래퍼 객체
    // chain: 다음 필터 또는 컨트롤러로 요청을 전달하는 체인
    // 반환: Mono<Void> — 비동기 처리 완료 시그널
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 요청별 고유 ID 생성 — UUID의 앞 8자리만 사용하여 간결하게
        // 이 ID로 하나의 요청에 속한 모든 로그를 추적할 수 있다
        val requestId = UUID.randomUUID().toString().substring(0, 8)

        // HTTP 요청 정보 추출
        val method = exchange.request.method.name() // GET, POST, DELETE 등
        val path = exchange.request.uri.path // /api/reservations 등

        // MDC에 요청 컨텍스트 설정
        // MDC.put(): 현재 스레드의 MDC에 키-값 쌍을 저장한다
        // 이 값들은 이후 이 요청을 처리하는 모든 로그에 자동으로 포함된다
        MDC.put("requestId", requestId) // 요청 추적 ID
        MDC.put("method", method)       // HTTP 메서드
        MDC.put("path", path)           // 요청 경로

        // 요청 시작 로그
        log.info("요청 시작: {} {}", method, path)

        // 요청 처리 시작 시각 기록 — 응답 시간 측정용
        val startTime = System.currentTimeMillis()

        // chain.filter(): 다음 필터 또는 컨트롤러로 요청을 전달한다
        // doFinally: 요청 처리가 완료되면 (성공/실패/취소 관계없이) 실행된다
        //   — MDC를 정리(clear)하여 다른 요청의 컨텍스트가 섞이지 않도록 한다
        //   — 응답 시간을 로깅한다
        return chain.filter(exchange)
            .doFinally {
                // 응답 처리 시간 계산
                val duration = System.currentTimeMillis() - startTime
                // 응답 상태 코드
                val statusCode = exchange.response.statusCode?.value() ?: 0

                // 응답 완료 로그 — 메서드, 경로, 상태 코드, 처리 시간
                log.info("요청 완료: {} {} → {} ({}ms)", method, path, statusCode, duration)

                // MDC 정리 — 이 스레드가 다른 요청을 처리할 때 이전 컨텍스트가 남지 않도록
                MDC.clear()
            }
    }
}
