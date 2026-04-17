package com.channelmanager.kotlin.config // 설정 패키지

import com.channelmanager.kotlin.domain.User // 사용자 엔티티
import com.channelmanager.kotlin.repository.UserRepository // 사용자 리포지토리
import org.slf4j.LoggerFactory // SLF4J 로거
import org.springframework.boot.context.event.ApplicationReadyEvent // 앱 시작 완료 이벤트
import org.springframework.context.event.EventListener // 이벤트 리스너
import org.springframework.security.crypto.password.PasswordEncoder // 비밀번호 인코더
import org.springframework.stereotype.Component // 빈 등록

// 샘플 사용자 초기화 — 앱 시작 시 admin/user 계정을 자동 생성한다
// @EventListener(ApplicationReadyEvent): Spring Boot 앱이 완전히 시작된 후 실행된다
// Flyway가 users 테이블을 생성한 후에 실행되므로 테이블이 보장된다
// BCrypt 해시는 런타임에 생성해야 정확하므로 SQL이 아닌 코드로 삽입한다
@Component
class DataInitializer(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    companion object {
        private val log = LoggerFactory.getLogger(DataInitializer::class.java)
    }

    // 앱 시작 완료 시 실행 — 샘플 사용자 생성
    @EventListener(ApplicationReadyEvent::class)
    fun initUsers() {
        // admin 계정이 없으면 생성 (비밀번호: admin123)
        userRepository.findByUsername("admin")
            .switchIfEmpty(
                userRepository.save(
                    User(
                        username = "admin",
                        password = passwordEncoder.encode("admin123")!!,
                        role = "ADMIN"
                    )
                ).doOnNext { log.info("샘플 admin 계정 생성 완료") }
            )
            .subscribe()

        // user 계정이 없으면 생성 (비밀번호: user123)
        userRepository.findByUsername("user")
            .switchIfEmpty(
                userRepository.save(
                    User(
                        username = "user",
                        password = passwordEncoder.encode("user123")!!,
                        role = "USER"
                    )
                ).doOnNext { log.info("샘플 user 계정 생성 완료") }
            )
            .subscribe()
    }
}
