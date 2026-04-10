package com.channelmanager.kotlin.service // 서비스 패키지

import com.fasterxml.jackson.databind.ObjectMapper // JSON 직렬화/역직렬화
import org.slf4j.LoggerFactory // SLF4J 로거 팩토리
import org.springframework.data.redis.core.ReactiveRedisTemplate // Reactive Redis 템플릿
import org.springframework.stereotype.Service // 서비스 계층 어노테이션
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.time.Duration // 시간 간격

// 캐시 서비스 — Redis를 사용한 Cache-Aside 패턴 구현
// Cache-Aside 패턴:
//   1. 캐시 조회 → 데이터가 있으면 반환 (Cache Hit)
//   2. 캐시에 없으면 → DB 조회 → 결과를 캐시에 저장 → 반환 (Cache Miss)
//   3. 데이터 변경 시 → 관련 캐시 삭제 (Cache Invalidation)
// ReactiveRedisTemplate: WebFlux 환경에서 논블로킹 Redis 접근을 제공한다
// ObjectMapper: 객체 ↔ JSON 문자열 변환에 사용한다
@Service
class CacheService(
    private val redisTemplate: ReactiveRedisTemplate<String, String>, // Redis 연산 템플릿
    private val objectMapper: ObjectMapper // Jackson JSON 직렬화
) {

    // SLF4J 로거
    companion object {
        private val log = LoggerFactory.getLogger(CacheService::class.java)

        // 캐시 키 접두사 — 통계 데이터용
        private const val CACHE_PREFIX = "cache:statistics:" // Redis 키 네임스페이스

        // 캐시 TTL (Time To Live) — 5분
        // 5분 후 자동 삭제되어 다음 조회 시 DB에서 최신 데이터를 가져온다
        private val CACHE_TTL = Duration.ofMinutes(5)
    }

    // 캐시 조회 또는 DB 로드 — Cache-Aside 패턴의 핵심 메서드
    // key: 캐시 키 (예: "summary")
    // type: 역직렬화할 타입 (예: SummaryStatistics::class.java)
    // loader: 캐시 미스 시 DB에서 데이터를 조회하는 함수 (Mono를 반환)
    // 흐름:
    //   1. Redis에서 "cache:statistics:{key}"로 조회
    //   2. 값이 있으면 JSON → 객체로 역직렬화하여 반환 (Cache Hit)
    //   3. 값이 없으면 loader()로 DB 조회 → JSON으로 직렬화 → Redis에 저장 → 반환 (Cache Miss)
    fun <T : Any> getOrLoad(key: String, type: Class<T>, loader: () -> Mono<T>): Mono<T> {
        val cacheKey = CACHE_PREFIX + key // 전체 캐시 키 생성

        return redisTemplate.opsForValue().get(cacheKey) // Redis에서 값 조회
            .flatMap { json -> // 값이 있으면 (Cache Hit)
                log.debug("Cache Hit: {}", cacheKey)
                try {
                    // JSON 문자열 → 객체로 역직렬화
                    Mono.just(objectMapper.readValue(json, type))
                } catch (e: Exception) {
                    // 역직렬화 실패 시 캐시 무시하고 DB에서 재조회
                    log.warn("캐시 역직렬화 실패: {} — DB에서 재조회", cacheKey, e)
                    Mono.empty()
                }
            }
            .switchIfEmpty(Mono.defer { // 값이 없으면 (Cache Miss)
                // Mono.defer: loader()를 캐시 미스 시에만 지연 호출한다
                //   — defer 없이 loader()를 직접 넘기면 파이프라인 조립 시 즉시 호출됨
                loader() // DB에서 데이터 조회
                    .flatMap { data -> // 조회 결과를 캐시에 저장
                        log.debug("Cache Miss: {} — DB에서 조회 후 캐시 저장", cacheKey)
                        try {
                            // 객체 → JSON 문자열로 직렬화
                            val json = objectMapper.writeValueAsString(data)
                            // Redis에 TTL과 함께 저장
                            redisTemplate.opsForValue()
                                .set(cacheKey, json, CACHE_TTL) // SET key value EX ttl
                                .thenReturn(data) // 저장 결과 무시하고 원본 데이터 반환
                        } catch (e: Exception) {
                            // 직렬화 실패 시 캐시 저장 생략하고 데이터만 반환
                            log.warn("캐시 직렬화 실패: {}", cacheKey, e)
                            Mono.just(data)
                        }
                    }
            })
    }

    // 통계 캐시 전체 무효화
    // 예약 생성/취소 시 호출되어 모든 통계 캐시를 삭제한다
    // 다음 통계 조회 시 DB에서 최신 데이터를 가져오게 된다
    fun evictStatisticsCache(): Mono<Void> {
        log.debug("통계 캐시 무효화 — 모든 통계 캐시를 삭제합니다")
        // 4개의 통계 캐시 키를 모두 삭제
        return redisTemplate.delete(
            "${CACHE_PREFIX}summary",
            "${CACHE_PREFIX}channels",
            "${CACHE_PREFIX}events",
            "${CACHE_PREFIX}rooms"
        ).then() // 삭제 건수 무시, Mono<Void> 반환
    }
}
