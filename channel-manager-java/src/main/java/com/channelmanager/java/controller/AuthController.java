package com.channelmanager.java.controller; // 컨트롤러 패키지

import com.channelmanager.java.domain.User; // 사용자 엔티티
import com.channelmanager.java.dto.AuthDto.AuthRequest; // 인증 요청 DTO
import com.channelmanager.java.dto.AuthDto.AuthResponse; // 인증 응답 DTO
import com.channelmanager.java.exception.BadRequestException; // 400 예외
import com.channelmanager.java.exception.UnauthorizedException; // 401 예외
import com.channelmanager.java.repository.UserRepository; // 사용자 리포지토리
import com.channelmanager.java.security.JwtUtil; // JWT 유틸리티
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성
import org.springframework.http.HttpStatus; // HTTP 상태 코드
import org.springframework.security.crypto.password.PasswordEncoder; // 비밀번호 인코더
import org.springframework.web.bind.annotation.PostMapping; // POST 메서드 매핑
import org.springframework.web.bind.annotation.RequestBody; // 요청 본문 바인딩
import org.springframework.web.bind.annotation.ResponseStatus; // 응답 상태 코드 설정
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림

// 인증 REST 컨트롤러
// Phase 21: 회원가입(register)과 로그인(login) API를 제공한다
// Kotlin에서는 class AuthController(val userRepository, val passwordEncoder, val jwtUtil)이지만,
// Java에서는 @RequiredArgsConstructor + private final 필드를 사용한다
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;   // 사용자 DB 접근
    private final PasswordEncoder passwordEncoder; // BCrypt 비밀번호 인코더
    private final JwtUtil jwtUtil;                 // JWT 토큰 생성

    // 회원가입 — 새 사용자를 생성한다
    // POST /api/auth/register
    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED) // 201 Created
    public Mono<AuthResponse> register(@RequestBody AuthRequest request) {
        return userRepository.findByUsername(request.username())
            .<AuthResponse>flatMap(existing -> // 이미 존재하면 400 에러
                Mono.error(new BadRequestException("이미 존재하는 사용자입니다. username=" + request.username()))
            )
            .switchIfEmpty(Mono.defer(() -> { // 존재하지 않으면 생성
                var user = User.builder()
                    .username(request.username())
                    .password(passwordEncoder.encode(request.password())) // BCrypt 해시
                    .role("USER") // 기본 역할: USER
                    .build();
                return userRepository.save(user)
                    .map(savedUser -> { // JWT 토큰 생성 후 응답
                        var token = jwtUtil.generateToken(savedUser.getUsername(), savedUser.getRole());
                        return new AuthResponse(token, savedUser.getUsername(), savedUser.getRole());
                    });
            }));
    }

    // 로그인 — 사용자 인증 후 JWT 토큰을 발급한다
    // POST /api/auth/login
    @PostMapping("/api/auth/login")
    public Mono<AuthResponse> login(@RequestBody AuthRequest request) {
        return userRepository.findByUsername(request.username())
            .filter(user -> // 비밀번호 검증 — BCrypt matches()
                passwordEncoder.matches(request.password(), user.getPassword())
            )
            .map(user -> { // JWT 토큰 생성 후 응답
                var token = jwtUtil.generateToken(user.getUsername(), user.getRole());
                return new AuthResponse(token, user.getUsername(), user.getRole());
            })
            .switchIfEmpty( // 사용자가 없거나 비밀번호 불일치 → 401 Unauthorized
                Mono.error(new UnauthorizedException("잘못된 사용자명 또는 비밀번호입니다"))
            );
    }
}
