package com.channelmanager.kotlin.config // 설정 패키지

import com.fasterxml.jackson.databind.ObjectMapper // JSON 직렬화/역직렬화
import com.fasterxml.jackson.databind.SerializationFeature // 직렬화 옵션
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule // Java 8 날짜/시간 직렬화 지원
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper // Kotlin 전용 ObjectMapper
import org.springframework.context.annotation.Bean // Spring 빈 등록
import org.springframework.context.annotation.Configuration // 설정 클래스 선언
import org.springframework.context.annotation.Primary // 동일 타입 빈 중 우선 사용
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory // Reactive Redis 커넥션 팩토리
import org.springframework.data.redis.core.ReactiveRedisTemplate // Reactive Redis 템플릿
import org.springframework.data.redis.serializer.RedisSerializationContext // 직렬화 컨텍스트
import org.springframework.data.redis.serializer.StringRedisSerializer // 문자열 직렬화

// Redis 설정 클래스
// ReactiveRedisTemplate을 커스터마이징하여 키와 값 모두 문자열로 직렬화한다
// 기본 JDK 직렬화 대신 문자열을 사용하면:
//   - redis-cli에서 데이터를 사람이 읽을 수 있다 (디버깅 용이)
//   - 다른 언어/모듈에서도 동일한 데이터를 읽을 수 있다 (호환성)
//   - 직렬화 크기가 작다 (성능)
@Configuration
class RedisConfig {

    // ReactiveRedisTemplate<String, String> 빈 — 키와 값 모두 문자열
    // 통계 데이터를 JSON 문자열로 직렬화하여 Redis에 저장한다
    // ReactiveRedisConnectionFactory: Spring Boot 자동설정이 Lettuce 기반으로 생성한 커넥션 팩토리
    // Lettuce: Redis 클라이언트 라이브러리 — Netty 기반 비동기/논블로킹 (WebFlux에 적합)
    @Bean
    @Primary // Spring Boot 자동설정의 ReactiveRedisTemplate보다 우선 사용
    fun reactiveStringRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory // 자동 주입되는 커넥션 팩토리
    ): ReactiveRedisTemplate<String, String> {
        // StringRedisSerializer: 문자열을 UTF-8 바이트로 직렬화/역직렬화
        val serializer = StringRedisSerializer()

        // RedisSerializationContext: 키/값/해시키/해시값의 직렬화 방식을 정의
        // 모든 필드를 StringRedisSerializer로 설정하여 일관되게 문자열로 처리
        val context = RedisSerializationContext.newSerializationContext<String, String>(serializer)
            .value(serializer) // 값 직렬화
            .hashKey(serializer) // 해시 키 직렬화
            .hashValue(serializer) // 해시 값 직렬화
            .build()

        return ReactiveRedisTemplate(connectionFactory, context)
    }

    // ObjectMapper 빈 — JSON 직렬화/역직렬화에 사용
    // WebFlux 환경에서는 spring-boot-starter-web이 없으므로
    // ObjectMapper가 자동으로 빈 등록되지 않을 수 있다
    // jacksonObjectMapper(): Kotlin 데이터 클래스를 지원하는 ObjectMapper
    // JavaTimeModule: LocalDate, LocalDateTime 등 Java 8 날짜 타입 직렬화 지원
    @Bean
    fun objectMapper(): ObjectMapper =
        jacksonObjectMapper() // Kotlin 모듈이 포함된 ObjectMapper
            .registerModule(JavaTimeModule()) // Java 8 날짜/시간 직렬화 모듈
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // 날짜를 ISO-8601 문자열로 출력
}
