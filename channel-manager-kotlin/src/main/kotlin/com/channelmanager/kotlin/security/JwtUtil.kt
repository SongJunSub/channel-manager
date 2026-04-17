package com.channelmanager.kotlin.security // 보안 패키지

import io.jsonwebtoken.Claims // JWT 클레임 (페이로드 데이터)
import io.jsonwebtoken.Jwts // JWT 빌더/파서
import io.jsonwebtoken.security.Keys // HMAC 서명 키 생성
import org.springframework.beans.factory.annotation.Value // 설정값 주입
import org.springframework.stereotype.Component // 빈 등록
import java.util.Date // 만료 시간용
import javax.crypto.SecretKey // HMAC 비밀 키

// JWT 유틸리티 — 토큰 생성, 검증, 클레임 추출을 담당한다
// JJWT 라이브러리를 사용하여 HMAC-SHA256으로 서명된 JWT를 생성한다
// JWT 구조: Header(알고리즘).Payload(클레임).Signature(서명)
//   - Header: {"alg":"HS256","typ":"JWT"}
//   - Payload: {"sub":"admin","role":"ADMIN","iat":..., "exp":...}
//   - Signature: HMAC-SHA256(Header+Payload, secretKey)
@Component
class JwtUtil(
    @Value("\${jwt.secret}") // application.yml에서 JWT 비밀키 주입
    private val secret: String,

    @Value("\${jwt.expiration-ms}") // application.yml에서 만료 시간 주입
    private val expirationMs: Long
) {
    // HMAC-SHA256 서명 키 — 문자열 시크릿을 SecretKey 객체로 변환
    // Keys.hmacShaKeyFor(): 바이트 배열로부터 HMAC-SHA 키를 생성한다
    // 최소 256비트(32바이트) 이상의 시크릿이 필요하다
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    // JWT 토큰 생성
    // subject: 사용자 ID (username)
    // role: 사용자 역할 (USER, ADMIN)
    // 반환: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIi..." 형태의 JWT 문자열
    fun generateToken(username: String, role: String): String =
        Jwts.builder()
            .subject(username)                                // sub 클레임: 사용자 ID
            .claim("role", role)                              // 커스텀 클레임: 사용자 역할
            .issuedAt(Date())                                 // iat 클레임: 발급 시각
            .expiration(Date(System.currentTimeMillis() + expirationMs)) // exp 클레임: 만료 시각
            .signWith(signingKey)                             // HMAC-SHA256 서명
            .compact()                                        // 최종 JWT 문자열 생성

    // JWT 토큰에서 모든 클레임 추출
    // 서명 검증 + 만료 시간 확인을 동시에 수행한다
    // 유효하지 않은 토큰이면 JwtException을 던진다
    fun getClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(signingKey)                           // 서명 검증에 사용할 키
            .build()
            .parseSignedClaims(token)                         // 서명 검증 + 파싱
            .payload                                          // Claims 객체 반환

    // 토큰에서 username(subject) 추출
    fun getUsername(token: String): String = getClaims(token).subject

    // 토큰에서 역할(role) 추출
    fun getRole(token: String): String = getClaims(token)["role"] as String

    // 토큰 유효성 검증
    // 서명이 유효하고, 만료되지 않았으면 true
    // 서명 위변조, 만료, 형식 오류 시 false
    fun isValid(token: String): Boolean =
        try {
            getClaims(token) // 서명 검증 + 만료 확인 (실패 시 예외)
            true
        } catch (e: Exception) {
            false // 서명 위변조, 만료, 형식 오류
        }
}
