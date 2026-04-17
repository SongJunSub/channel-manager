package com.channelmanager.java.security; // 보안 패키지

import io.jsonwebtoken.Claims; // JWT 클레임 (페이로드 데이터)
import io.jsonwebtoken.Jwts; // JWT 빌더/파서
import io.jsonwebtoken.security.Keys; // HMAC 서명 키 생성
import org.springframework.beans.factory.annotation.Value; // 설정값 주입
import org.springframework.stereotype.Component; // 빈 등록

import javax.crypto.SecretKey; // HMAC 비밀 키
import java.util.Date; // 만료 시간용

// JWT 유틸리티 — 토큰 생성, 검증, 클레임 추출을 담당한다
// JJWT 라이브러리를 사용하여 HMAC-SHA256으로 서명된 JWT를 생성한다
// Kotlin에서는 class JwtUtil(val secret, val expirationMs)이지만,
// Java에서는 @Value로 필드에 직접 주입한다
@Component
public class JwtUtil {

    private final SecretKey signingKey; // HMAC-SHA256 서명 키
    private final long expirationMs;   // 토큰 만료 시간 (밀리초)

    // 생성자 — @Value로 설정값을 주입받아 서명 키를 초기화한다
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        // Keys.hmacShaKeyFor(): 바이트 배열로부터 HMAC-SHA 키를 생성한다
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    // JWT 토큰 생성
    public String generateToken(String username, String role) {
        return Jwts.builder()
            .subject(username)                                // sub 클레임: 사용자 ID
            .claim("role", role)                              // 커스텀 클레임: 사용자 역할
            .issuedAt(new Date())                             // iat 클레임: 발급 시각
            .expiration(new Date(System.currentTimeMillis() + expirationMs)) // exp 클레임: 만료 시각
            .signWith(signingKey)                             // HMAC-SHA256 서명
            .compact();                                       // 최종 JWT 문자열 생성
    }

    // JWT 토큰에서 모든 클레임 추출
    public Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)                           // 서명 검증에 사용할 키
            .build()
            .parseSignedClaims(token)                         // 서명 검증 + 파싱
            .getPayload();                                    // Claims 객체 반환
    }

    // 토큰에서 username(subject) 추출
    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    // 토큰에서 역할(role) 추출
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    // 토큰 유효성 검증
    public boolean isValid(String token) {
        try {
            getClaims(token); // 서명 검증 + 만료 확인 (실패 시 예외)
            return true;
        } catch (Exception e) {
            return false; // 서명 위변조, 만료, 형식 오류
        }
    }
}
