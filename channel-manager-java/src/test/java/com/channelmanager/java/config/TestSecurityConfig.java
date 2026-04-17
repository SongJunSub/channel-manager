package com.channelmanager.java.config; // 테스트 설정 패키지

import org.springframework.boot.test.context.TestConfiguration; // 테스트 전용 설정
import org.springframework.context.annotation.Bean; // 빈 등록
import org.springframework.core.annotation.Order; // 빈 우선순위
import org.springframework.security.config.web.server.ServerHttpSecurity; // WebFlux Security 설정
import org.springframework.security.web.server.SecurityWebFilterChain; // Security 필터 체인

// 테스트 전용 Security 설정 — 모든 경로를 인증 없이 접근 가능하게 한다
// 기존 통합 테스트가 JWT 토큰 없이도 API를 호출할 수 있도록
// 프로덕션 SecurityConfig를 오버라이드한다
// Kotlin에서는 @TestConfiguration class TestSecurityConfig이지만,
// Java에서는 public class TestSecurityConfig이다
@TestConfiguration
public class TestSecurityConfig {

    // 모든 경로를 permitAll()로 설정 — 테스트에서 인증 우회
    // @Order(-1): 프로덕션 SecurityConfig보다 우선 적용되어 모든 요청을 허용한다
    @Bean
    @Order(-1)
    public SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }
}
