package com.channelmanager.java.config; // 설정 패키지

import io.github.resilience4j.circuitbreaker.CircuitBreaker; // 서킷 브레이커 인스턴스
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry; // 서킷 브레이커 레지스트리
import io.github.resilience4j.retry.Retry; // 재시도 인스턴스
import io.github.resilience4j.retry.RetryRegistry; // 재시도 레지스트리
import org.slf4j.Logger; // SLF4J 로거
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import org.springframework.context.annotation.Bean; // 빈 등록
import org.springframework.context.annotation.Configuration; // 설정 클래스

// Resilience4j 설정 — 서킷 브레이커와 재시도 인스턴스를 Spring 빈으로 등록한다
// Kotlin에서는 class ResilienceConfig이지만, Java에서는 public class이다
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    // 서킷 브레이커 빈
    @Bean
    public CircuitBreaker reservationApiCircuitBreaker(CircuitBreakerRegistry registry) {
        var cb = registry.circuitBreaker("reservationApi");
        cb.getEventPublisher()
            .onStateTransition(event ->
                log.info("서킷 브레이커 상태 전이: {} → {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
        return cb;
    }

    // 재시도 빈
    @Bean
    public Retry reservationApiRetry(RetryRegistry registry) {
        var retry = registry.retry("reservationApi");
        retry.getEventPublisher()
            .onRetry(event ->
                log.info("재시도 #{}: {}",
                    event.getNumberOfRetryAttempts(),
                    event.getLastThrowable() != null ? event.getLastThrowable().getMessage() : ""));
        return retry;
    }
}
