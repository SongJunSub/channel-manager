package com.channelmanager.kotlin.controller // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.kotlin.dto.ChannelStatistics // 채널별 통계 DTO
import com.channelmanager.kotlin.dto.EventStatistics // 이벤트 타입별 통계 DTO
import com.channelmanager.kotlin.dto.RoomTypeStatistics // 객실 타입별 통계 DTO
import com.channelmanager.kotlin.dto.SummaryStatistics // 전체 요약 통계 DTO
import com.channelmanager.kotlin.service.CacheService // Phase 18: Redis 캐시 서비스
import com.channelmanager.kotlin.service.StatisticsService // 통계 서비스
import org.springframework.web.bind.annotation.GetMapping // GET 메서드 매핑
import org.springframework.web.bind.annotation.RestController // REST 컨트롤러 선언
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림

// 통계 REST 컨트롤러
// Phase 8: Flux 고급 연산자(groupBy, reduce, count)를 활용한 통계 API를 제공한다
// 4개의 통계 엔드포인트를 제공한다:
//   1. GET /api/statistics/channels — 채널별 예약/매출 통계
//   2. GET /api/statistics/events — 이벤트 타입별 발생 건수
//   3. GET /api/statistics/rooms — 객실 타입별 예약/매출 통계
//   4. GET /api/statistics/summary — 전체 요약 통계
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
@RestController
class StatisticsController(
    private val statisticsService: StatisticsService, // 통계 비즈니스 로직 위임 대상
    private val cacheService: CacheService            // Phase 18: Redis 캐시 서비스
) {

    // 채널별 예약/매출 통계
    // GET /api/statistics/channels
    // 각 채널(DIRECT, BOOKING, AGODA 등)의 예약 건수, 취소 건수, 총 매출을 반환한다
    // Phase 18: CacheService를 통해 Redis에 캐시된 결과를 우선 반환한다
    // Flux 결과를 collectList()로 List로 변환 → 캐시 → Flux로 다시 변환
    @GetMapping("/api/statistics/channels")
    fun getChannelStatistics(): Flux<ChannelStatistics> =
        cacheService.getOrLoad("channels", Array<ChannelStatistics>::class.java) {
            statisticsService.getChannelStatistics().collectList().map { it.toTypedArray() }
        }.flatMapIterable { it.toList() } // Array → Flux 변환

    // 이벤트 타입별 발생 건수 통계
    // GET /api/statistics/events
    // 각 이벤트 타입(RESERVATION_CREATED, INVENTORY_UPDATED 등)의 발생 건수를 반환한다
    @GetMapping("/api/statistics/events")
    fun getEventStatistics(): Flux<EventStatistics> =
        cacheService.getOrLoad("events", Array<EventStatistics>::class.java) {
            statisticsService.getEventStatistics().collectList().map { it.toTypedArray() }
        }.flatMapIterable { it.toList() }

    // 객실 타입별 예약/매출 통계
    // GET /api/statistics/rooms
    // 각 객실 타입(Superior Double, Deluxe Twin 등)의 예약 건수와 매출을 반환한다
    @GetMapping("/api/statistics/rooms")
    fun getRoomTypeStatistics(): Flux<RoomTypeStatistics> =
        cacheService.getOrLoad("rooms", Array<RoomTypeStatistics>::class.java) {
            statisticsService.getRoomTypeStatistics().collectList().map { it.toTypedArray() }
        }.flatMapIterable { it.toList() }

    // 전체 요약 통계
    // GET /api/statistics/summary
    // 시스템 전체의 예약 수, 매출, 이벤트 수, 활성 채널 수를 하나의 객체로 반환한다
    // Mono.zip()으로 6개의 독립적인 집계를 병렬 실행하여 효율적으로 조합한다
    @GetMapping("/api/statistics/summary")
    fun getSummaryStatistics(): Mono<SummaryStatistics> =
        cacheService.getOrLoad("summary", SummaryStatistics::class.java) {
            statisticsService.getSummaryStatistics()
        }
}
