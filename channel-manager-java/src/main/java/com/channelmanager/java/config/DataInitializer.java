package com.channelmanager.java.config; // 설정 패키지

import com.channelmanager.java.domain.User; // 사용자 엔티티
import com.channelmanager.java.repository.UserRepository; // 사용자 리포지토리
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성
import org.slf4j.Logger; // SLF4J 로거
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import org.springframework.boot.context.event.ApplicationReadyEvent; // 앱 시작 완료 이벤트
import org.springframework.context.event.EventListener; // 이벤트 리스너
import org.springframework.security.crypto.password.PasswordEncoder; // 비밀번호 인코더
import org.springframework.stereotype.Component; // 빈 등록

// 샘플 사용자 초기화 — 앱 시작 시 admin/user 계정을 자동 생성한다
// @EventListener(ApplicationReadyEvent): Spring Boot 앱이 완전히 시작된 후 실행된다
// Kotlin에서는 class DataInitializer(...) + @EventListener fun initUsers()이지만,
// Java에서는 @RequiredArgsConstructor + @EventListener public void initUsers()이다
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 앱 시작 완료 시 실행 — 샘플 사용자 생성
    @EventListener(ApplicationReadyEvent.class)
    public void initUsers() {
        // admin 계정이 없으면 생성 (비밀번호: admin123)
        userRepository.findByUsername("admin")
            .switchIfEmpty(
                userRepository.save(
                    User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .role("ADMIN")
                        .build()
                ).doOnNext(user -> log.info("샘플 admin 계정 생성 완료"))
            )
            .subscribe();

        // user 계정이 없으면 생성 (비밀번호: user123)
        userRepository.findByUsername("user")
            .switchIfEmpty(
                userRepository.save(
                    User.builder()
                        .username("user")
                        .password(passwordEncoder.encode("user123"))
                        .role("USER")
                        .build()
                ).doOnNext(user -> log.info("샘플 user 계정 생성 완료"))
            )
            .subscribe();
    }
}
