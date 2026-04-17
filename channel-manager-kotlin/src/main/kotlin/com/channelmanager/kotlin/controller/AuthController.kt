package com.channelmanager.kotlin.controller // 컨트롤러 패키지

import com.channelmanager.kotlin.domain.User // 사용자 엔티티
import com.channelmanager.kotlin.dto.AuthRequest // 인증 요청 DTO
import com.channelmanager.kotlin.dto.AuthResponse // 인증 응답 DTO
import com.channelmanager.kotlin.exception.BadRequestException // 400 예외
import com.channelmanager.kotlin.exception.UnauthorizedException // 401 예외
import com.channelmanager.kotlin.repository.UserRepository // 사용자 리포지토리
import com.channelmanager.kotlin.security.JwtUtil // JWT 유틸리티
import org.springframework.http.HttpStatus // HTTP 상태 코드
import org.springframework.security.crypto.password.PasswordEncoder // 비밀번호 인코더
import org.springframework.web.bind.annotation.PostMapping // POST 메서드 매핑
import org.springframework.web.bind.annotation.RequestBody // 요청 본문 바인딩
import org.springframework.web.bind.annotation.ResponseStatus // 응답 상태 코드 설정
import org.springframework.web.bind.annotation.RestController // REST 컨트롤러
import reactor.core.publisher.Mono // 0~1개 비동기 스트림

// 인증 REST 컨트롤러
// Phase 21: 회원가입(register)과 로그인(login) API를 제공한다
// /api/auth/** 경로는 SecurityConfig에서 permitAll()로 설정되어 인증 없이 접근 가능하다
@RestController
class AuthController(
    private val userRepository: UserRepository,   // 사용자 DB 접근
    private val passwordEncoder: PasswordEncoder, // BCrypt 비밀번호 인코더
    private val jwtUtil: JwtUtil                  // JWT 토큰 생성
) {

    // 회원가입 — 새 사용자를 생성한다
    // POST /api/auth/register
    // 요청: { "username": "newuser", "password": "password123" }
    // 응답: { "token": "eyJ...", "username": "newuser", "role": "USER" }
    // 비밀번호는 BCrypt로 해시되어 저장된다 (원문 저장 금지)
    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED) // 201 Created
    fun register(@RequestBody request: AuthRequest): Mono<AuthResponse> =
        // 1. 중복 username 체크
        userRepository.findByUsername(request.username)
            .flatMap<AuthResponse> { // 이미 존재하면 400 에러
                Mono.error(BadRequestException("이미 존재하는 사용자입니다. username=${request.username}"))
            }
            .switchIfEmpty(Mono.defer { // 존재하지 않으면 생성
                // 2. 비밀번호 BCrypt 해시 후 저장
                val user = User(
                    username = request.username,
                    password = passwordEncoder.encode(request.password)!!, // BCrypt 해시
                    role = "USER" // 기본 역할: USER
                )
                userRepository.save(user)
                    .map { savedUser -> // 3. JWT 토큰 생성 후 응답
                        val token = jwtUtil.generateToken(savedUser.username, savedUser.role)
                        AuthResponse(token = token, username = savedUser.username, role = savedUser.role)
                    }
            })

    // 로그인 — 사용자 인증 후 JWT 토큰을 발급한다
    // POST /api/auth/login
    // 요청: { "username": "admin", "password": "admin123" }
    // 응답: { "token": "eyJ...", "username": "admin", "role": "ADMIN" }
    @PostMapping("/api/auth/login")
    fun login(@RequestBody request: AuthRequest): Mono<AuthResponse> =
        // 1. username으로 사용자 조회
        userRepository.findByUsername(request.username)
            .filter { user -> // 2. 비밀번호 검증 — BCrypt matches()
                passwordEncoder.matches(request.password, user.password)
            }
            .map { user -> // 3. JWT 토큰 생성 후 응답
                val token = jwtUtil.generateToken(user.username, user.role)
                AuthResponse(token = token, username = user.username, role = user.role)
            }
            .switchIfEmpty( // 사용자가 없거나 비밀번호 불일치 → 401 Unauthorized
                Mono.error(UnauthorizedException("잘못된 사용자명 또는 비밀번호입니다"))
            )
}
