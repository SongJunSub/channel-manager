package com.channelmanager.kotlin.security // 보안 패키지

import org.springframework.context.annotation.Bean // 빈 등록
import org.springframework.context.annotation.Configuration // 설정 클래스
import org.springframework.http.HttpMethod // HTTP 메서드 상수
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity // WebFlux Security 활성화
import org.springframework.security.config.web.server.SecurityWebFiltersOrder // 필터 순서 상수
import org.springframework.security.config.web.server.ServerHttpSecurity // WebFlux Security 설정 빌더
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder // BCrypt 비밀번호 인코더
import org.springframework.security.crypto.password.PasswordEncoder // 비밀번호 인코더 인터페이스
import org.springframework.security.web.server.SecurityWebFilterChain // Security 필터 체인

// Spring Security WebFlux 설정
// @EnableWebFluxSecurity: WebFlux 환경에서 Spring Security를 활성화한다
//   — Spring MVC의 @EnableWebSecurity에 대응한다
// SecurityWebFilterChain: 모든 HTTP 요청에 적용되는 보안 필터 체인을 정의한다
// CSRF 비활성화: REST API는 상태가 없으므로(Stateless) CSRF 토큰이 불필요하다
// JWT 필터: SecurityWebFiltersOrder.AUTHENTICATION 위치에 추가하여 인증을 처리한다
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter // JWT 인증 필터
) {

    // SecurityWebFilterChain — 보안 정책 정의
    // ServerHttpSecurity: WebFlux 전용 Security 설정 빌더
    //   — Spring MVC의 HttpSecurity에 대응한다
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            // CSRF 비활성화 — REST API는 Stateless이므로 CSRF 보호가 불필요하다
            .csrf { it.disable() }
            // HTTP Basic 인증 비활성화 — JWT 토큰 기반 인증을 사용한다
            .httpBasic { it.disable() }
            // 폼 로그인 비활성화 — REST API는 폼 로그인을 사용하지 않는다
            .formLogin { it.disable() }
            // URL 기반 접근 제어 정의
            .authorizeExchange { exchanges ->
                exchanges
                    // 공개 경로 — 인증 없이 접근 가능
                    .pathMatchers("/api/auth/**").permitAll()       // 로그인/회원가입
                    .pathMatchers("/actuator/**").permitAll()       // 모니터링 (Prometheus 스크래핑)
                    .pathMatchers("/webjars/**").permitAll()        // Swagger UI 정적 리소스
                    .pathMatchers("/v3/api-docs/**").permitAll()    // OpenAPI 스펙
                    .pathMatchers("/swagger-ui/**").permitAll()     // Swagger UI
                    .pathMatchers("/swagger-ui.html").permitAll()   // Swagger UI 리다이렉트
                    .pathMatchers("/index.html").permitAll()        // 대시보드
                    .pathMatchers("/css/**", "/js/**").permitAll()  // 정적 리소스
                    .pathMatchers("/").permitAll()                  // 루트 경로
                    .pathMatchers("/api/events/stream").permitAll() // SSE 스트림 (장기 연결)
                    .pathMatchers("/ws/**").permitAll()             // Phase 23: WebSocket 엔드포인트
                    // ADMIN 전용 경로
                    .pathMatchers("/api/simulator/**").hasRole("ADMIN") // 시뮬레이터 제어
                    // 인증된 사용자 (USER 또는 ADMIN)
                    .pathMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                    // 그 외 모든 요청 — 인증 필요
                    .anyExchange().authenticated()
            }
            // JWT 인증 필터를 AUTHENTICATION 위치에 추가
            // SecurityWebFiltersOrder.AUTHENTICATION: Spring Security의 인증 처리 위치
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()

    // BCrypt 비밀번호 인코더 빈
    // BCryptPasswordEncoder: 비밀번호를 BCrypt 해시로 인코딩/검증한다
    //   — encode("password") → "$2a$10$..." (해시)
    //   — matches("password", hash) → true/false (검증)
    // Cost Factor 10 (기본값): 2^10 = 1024회 해시 반복
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
