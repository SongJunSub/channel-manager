package com.channelmanager.kotlin // Kotlin 모듈의 루트 패키지

import org.springframework.boot.autoconfigure.SpringBootApplication // Spring Boot 자동 설정 어노테이션
import org.springframework.boot.runApplication // Spring Boot 애플리케이션 실행 헬퍼 함수 (Kotlin 전용)

// @SpringBootApplication은 아래 3개 어노테이션의 조합이다:
// - @Configuration: 이 클래스가 Spring 설정 클래스임을 선언
// - @EnableAutoConfiguration: 클래스패스의 라이브러리를 기반으로 자동 설정 활성화
// - @ComponentScan: 이 패키지 하위의 @Component, @Service 등을 자동 스캔
@SpringBootApplication
class ChannelManagerKotlinApplication // 클래스 본문이 비어있어도 Spring Boot 진입점으로 동작한다

// 애플리케이션의 메인 함수 - JVM이 이 함수를 실행하여 Spring Boot를 시작한다
fun main(args: Array<String>) {
    runApplication<ChannelManagerKotlinApplication>(*args) // Spring Boot 애플리케이션 시작 (Kotlin 확장 함수 사용)
}
