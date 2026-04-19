package com.channelmanager.kotlin.config // 설정 패키지

import io.github.resilience4j.circuitbreaker.CircuitBreaker // 서킷 브레이커 인스턴스
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry // 서킷 브레이커 레지스트리
import io.github.resilience4j.retry.Retry // 재시도 인스턴스
import io.github.resilience4j.retry.RetryRegistry // 재시도 레지스트리
import org.slf4j.LoggerFactory // SLF4J 로거
import org.springframework.context.annotation.Bean // 빈 등록
import org.springframework.context.annotation.Configuration // 설정 클래스

// Resilience4j 설정 — 서킷 브레이커와 재시도 인스턴스를 Spring 빈으로 등록한다
// application.yml의 resilience4j.circuitbreaker.instances.reservationApi 설정이
// CircuitBreakerRegistry를 통해 자동으로 적용된다
// Phase 27: 채널 시뮬레이터의 WebClient 호출에 장애 격리를 적용한다
@Configuration
class ResilienceConfig {

    companion object {
        private val log = LoggerFactory.getLogger(ResilienceConfig::class.java)
    }

    // 서킷 브레이커 빈 — "reservationApi" 인스턴스를 레지스트리에서 가져온다
    // CircuitBreakerRegistry: application.yml 설정을 기반으로 인스턴스를 관리한다
    // circuitBreakerRegistry.circuitBreaker("reservationApi"):
    //   yml의 resilience4j.circuitbreaker.instances.reservationApi 설정이 자동 적용된다
    @Bean
    fun reservationApiCircuitBreaker(
        circuitBreakerRegistry: CircuitBreakerRegistry // Resilience4j가 자동 등록한 레지스트리
    ): CircuitBreaker {
        val cb = circuitBreakerRegistry.circuitBreaker("reservationApi")
        // 상태 전이 이벤트를 로그로 기록한다
        // CLOSED→OPEN, OPEN→HALF_OPEN, HALF_OPEN→CLOSED 등의 전이 시 로그
        cb.eventPublisher
            .onStateTransition { event ->
                log.info("서킷 브레이커 상태 전이: {} → {}",
                    event.stateTransition.fromState, event.stateTransition.toState)
            }
        return cb
    }

    // 재시도 빈 — "reservationApi" 인스턴스를 레지스트리에서 가져온다
    @Bean
    fun reservationApiRetry(
        retryRegistry: RetryRegistry // Resilience4j가 자동 등록한 레지스트리
    ): Retry {
        val retry = retryRegistry.retry("reservationApi")
        // 재시도 이벤트를 로그로 기록한다
        retry.eventPublisher
            .onRetry { event ->
                log.info("재시도 #{}: {}", event.numberOfRetryAttempts, event.lastThrowable?.message)
            }
        return retry
    }
}
