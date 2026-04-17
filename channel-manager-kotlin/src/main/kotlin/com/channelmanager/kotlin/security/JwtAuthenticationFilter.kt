package com.channelmanager.kotlin.security // 보안 패키지

import org.springframework.http.HttpHeaders // HTTP 헤더 상수
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken // 인증 토큰
import org.springframework.security.core.authority.SimpleGrantedAuthority // 권한 객체
import org.springframework.security.core.context.ReactiveSecurityContextHolder // Reactive 보안 컨텍스트
import org.springframework.stereotype.Component // 빈 등록
import org.springframework.web.server.ServerWebExchange // HTTP 요청/응답 래퍼
import org.springframework.web.server.WebFilter // WebFlux 필터 인터페이스
import org.springframework.web.server.WebFilterChain // 필터 체인
import reactor.core.publisher.Mono // 0~1개 비동기 스트림

// JWT 인증 필터 — 모든 HTTP 요청에서 JWT 토큰을 검증하고 SecurityContext에 인증 정보를 저장한다
// WebFilter: Spring WebFlux의 필터 인터페이스 (Servlet의 Filter에 대응)
// 흐름:
//   1. Authorization 헤더에서 Bearer 토큰 추출
//   2. JwtUtil로 토큰 검증 (서명 + 만료)
//   3. 유효하면 → SecurityContext에 인증 정보 저장 → 다음 필터로 전달
//   4. 무효하면 → 인증 없이 다음 필터로 전달 (SecurityConfig에서 접근 거부)
// @Order를 지정하지 않는다 — SecurityConfig에서 addFilterAt()으로 위치를 지정한다
@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil // JWT 유틸리티 (토큰 검증, 클레임 추출)
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 1. Authorization 헤더에서 JWT 토큰 추출
        val token = extractToken(exchange)

        // 2. 토큰이 없으면 인증 없이 다음 필터로 전달
        if (token == null) {
            return chain.filter(exchange)
        }

        // 3. 토큰 검증 + 클레임 추출을 1회 호출로 수행
        // getClaims(): 서명 검증 + 만료 확인 + 클레임 파싱을 한 번에 수행
        // 실패 시 예외 → catch에서 인증 없이 전달 (SecurityConfig에서 접근 거부)
        val claims = try {
            jwtUtil.getClaims(token)
        } catch (e: Exception) {
            return chain.filter(exchange) // 서명 위변조, 만료, 형식 오류
        }
        val username = claims.subject                // sub 클레임
        val role = claims["role"] as String          // role 클레임

        // 4. Spring Security 인증 객체 생성
        // UsernamePasswordAuthenticationToken: Spring Security의 인증 정보 컨테이너
        //   - principal: 사용자 ID (username)
        //   - credentials: 비밀번호 (JWT 인증에서는 null — 이미 검증됨)
        //   - authorities: 권한 목록 (ROLE_USER, ROLE_ADMIN)
        // SimpleGrantedAuthority("ROLE_ADMIN"): Spring Security가 역할을 "ROLE_" 접두사로 관리
        val auth = UsernamePasswordAuthenticationToken(
            username,
            null, // JWT 인증에서는 비밀번호가 불필요
            listOf(SimpleGrantedAuthority("ROLE_$role")) // ROLE_ 접두사 추가
        )

        // 5. ReactiveSecurityContextHolder에 인증 정보 저장
        // withAuthentication(): Reactor Context에 인증 정보를 주입한다
        // SecurityConfig의 authorizeExchange()가 이 컨텍스트를 읽어 접근 제어를 수행한다
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
    }

    // Authorization 헤더에서 Bearer 토큰 추출
    // "Bearer eyJhbGciOiJIUzI1NiJ9..." → "eyJhbGciOiJIUzI1NiJ9..."
    // Bearer 접두사가 없거나 헤더가 없으면 null 반환
    private fun extractToken(exchange: ServerWebExchange): String? {
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        return if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7) // "Bearer " 이후의 토큰 문자열
        } else {
            null
        }
    }
}
