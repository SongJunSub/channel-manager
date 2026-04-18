package com.channelmanager.java.security; // 보안 패키지

import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성
import org.springframework.context.annotation.Bean; // 빈 등록
import org.springframework.context.annotation.Configuration; // 설정 클래스
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity; // WebFlux Security 활성화
import org.springframework.security.config.web.server.SecurityWebFiltersOrder; // 필터 순서 상수
import org.springframework.security.config.web.server.ServerHttpSecurity; // WebFlux Security 설정 빌더
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt 비밀번호 인코더
import org.springframework.security.crypto.password.PasswordEncoder; // 비밀번호 인코더 인터페이스
import org.springframework.security.web.server.SecurityWebFilterChain; // Security 필터 체인

// Spring Security WebFlux 설정
// Kotlin에서는 @Configuration + @EnableWebFluxSecurity로 동일하게 설정한다
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter; // JWT 인증 필터

    // SecurityWebFilterChain — 보안 정책 정의
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // CSRF 비활성화 — REST API는 Stateless이므로 CSRF 보호가 불필요하다
            .csrf(csrf -> csrf.disable())
            // HTTP Basic 인증 비활성화 — JWT 토큰 기반 인증을 사용한다
            .httpBasic(httpBasic -> httpBasic.disable())
            // 폼 로그인 비활성화 — REST API는 폼 로그인을 사용하지 않는다
            .formLogin(formLogin -> formLogin.disable())
            // URL 기반 접근 제어 정의
            .authorizeExchange(exchanges -> exchanges
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
            )
            // JWT 인증 필터를 AUTHENTICATION 위치에 추가
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }

    // BCrypt 비밀번호 인코더 빈
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
