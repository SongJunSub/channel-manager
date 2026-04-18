package com.channelmanager.kotlin.config // 테스트 설정 패키지

import org.mockito.Mockito // Mockito 모킹 프레임워크
import org.springframework.boot.test.context.TestConfiguration // 테스트 전용 설정
import org.springframework.context.annotation.Bean // 빈 등록
import org.springframework.core.annotation.Order // 빈 우선순위
import org.springframework.kafka.core.KafkaTemplate // Kafka 발행 템플릿
import org.springframework.security.config.web.server.ServerHttpSecurity // WebFlux Security 설정
import org.springframework.security.web.server.SecurityWebFilterChain // Security 필터 체인

// 테스트 전용 Security + Kafka 설정
// 기존 통합 테스트가 JWT 토큰 없이도 API를 호출하고,
// Kafka 브로커 없이도 정상 동작할 수 있도록 한다
@TestConfiguration
class TestSecurityConfig {

    // 모든 경로를 permitAll()로 설정 — 테스트에서 인증 우회
    @Bean
    @Order(-1)
    fun testSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange { it.anyExchange().permitAll() }
            .build()

    // Phase 25: 테스트용 KafkaTemplate Mock — Kafka 브로커 없이 동작
    // 테스트 환경에서는 실제 Kafka 연결이 없으므로 Mock 빈을 제공한다
    // KafkaEventProducer가 이 Mock을 주입받아 send() 호출이 무시된다
    @Bean
    @Suppress("UNCHECKED_CAST")
    fun kafkaTemplate(): KafkaTemplate<String, String> =
        Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
}
