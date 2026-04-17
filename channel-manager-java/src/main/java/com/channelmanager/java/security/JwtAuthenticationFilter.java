package com.channelmanager.java.security; // 보안 패키지

import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성
import org.springframework.http.HttpHeaders; // HTTP 헤더 상수
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // 인증 토큰
import org.springframework.security.core.authority.SimpleGrantedAuthority; // 권한 객체
import org.springframework.security.core.context.ReactiveSecurityContextHolder; // Reactive 보안 컨텍스트
import org.springframework.stereotype.Component; // 빈 등록
import org.springframework.web.server.ServerWebExchange; // HTTP 요청/응답 래퍼
import org.springframework.web.server.WebFilter; // WebFlux 필터 인터페이스
import org.springframework.web.server.WebFilterChain; // 필터 체인
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림

import java.util.List; // 리스트

// JWT 인증 필터 — 모든 HTTP 요청에서 JWT 토큰을 검증하고 SecurityContext에 인증 정보를 저장한다
// Kotlin에서는 class JwtAuthenticationFilter(val jwtUtil) : WebFilter이지만,
// Java에서는 @RequiredArgsConstructor + implements WebFilter이다
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtil jwtUtil; // JWT 유틸리티 (토큰 검증, 클레임 추출)

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1. Authorization 헤더에서 JWT 토큰 추출
        String token = extractToken(exchange);

        // 2. 토큰이 없으면 인증 없이 다음 필터로 전달
        if (token == null) {
            return chain.filter(exchange);
        }

        // 3. 토큰 검증 + 클레임 추출을 1회 호출로 수행
        // getClaims(): 서명 검증 + 만료 확인 + 클레임 파싱을 한 번에 수행
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtUtil.getClaims(token);
        } catch (Exception e) {
            return chain.filter(exchange); // 서명 위변조, 만료, 형식 오류
        }
        String username = claims.getSubject();                   // sub 클레임
        String role = claims.get("role", String.class);          // role 클레임

        // 4. Spring Security 인증 객체 생성
        var auth = new UsernamePasswordAuthenticationToken(
            username,
            null, // JWT 인증에서는 비밀번호가 불필요
            List.of(new SimpleGrantedAuthority("ROLE_" + role)) // ROLE_ 접두사 추가
        );

        // 5. ReactiveSecurityContextHolder에 인증 정보 저장
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    // Authorization 헤더에서 Bearer 토큰 추출
    private String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // "Bearer " 이후의 토큰 문자열
        }
        return null;
    }
}
