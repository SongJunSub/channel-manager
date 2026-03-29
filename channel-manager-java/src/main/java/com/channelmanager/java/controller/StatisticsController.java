package com.channelmanager.java.controller; // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.java.dto.StatisticsResponse.ChannelStatistics; // 채널별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.EventStatistics; // 이벤트 타입별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.RoomTypeStatistics; // 객실 타입별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.SummaryStatistics; // 전체 요약 통계 DTO
import com.channelmanager.java.service.StatisticsService; // 통계 서비스
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 (Lombok)
import org.springframework.web.bind.annotation.GetMapping; // GET 메서드 매핑
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러 선언
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림

// 통계 REST 컨트롤러
// Phase 8: Flux 고급 연산자(groupBy, reduce, count)를 활용한 통계 API를 제공한다
// 4개의 통계 엔드포인트를 제공한다:
//   1. GET /api/statistics/channels — 채널별 예약/매출 통계
//   2. GET /api/statistics/events — 이벤트 타입별 발생 건수
//   3. GET /api/statistics/rooms — 객실 타입별 예약/매출 통계
//   4. GET /api/statistics/summary — 전체 요약 통계
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
// @RequiredArgsConstructor: Lombok이 final 필드에 대한 생성자를 자동 생성한다
// Kotlin에서는 primary constructor에 val로 의존성을 선언하지만,
// Java에서는 @RequiredArgsConstructor + private final 필드로 동일한 효과를 얻는다
@RestController
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService; // 통계 비즈니스 로직 위임 대상

    // 채널별 예약/매출 통계
    // GET /api/statistics/channels
    // 각 채널(DIRECT, BOOKING, AGODA 등)의 예약 건수, 취소 건수, 총 매출을 반환한다
    // Kotlin에서는 fun getChannelStatistics(): Flux<ChannelStatistics>이지만,
    // Java에서는 public Flux<ChannelStatistics> getChannelStatistics()이다
    @GetMapping("/api/statistics/channels")
    public Flux<ChannelStatistics> getChannelStatistics() {
        return statisticsService.getChannelStatistics();
    }

    // 이벤트 타입별 발생 건수 통계
    // GET /api/statistics/events
    // 각 이벤트 타입의 발생 건수를 반환한다
    @GetMapping("/api/statistics/events")
    public Flux<EventStatistics> getEventStatistics() {
        return statisticsService.getEventStatistics();
    }

    // 객실 타입별 예약/매출 통계
    // GET /api/statistics/rooms
    // 각 객실 타입의 예약 건수와 매출을 반환한다
    @GetMapping("/api/statistics/rooms")
    public Flux<RoomTypeStatistics> getRoomTypeStatistics() {
        return statisticsService.getRoomTypeStatistics();
    }

    // 전체 요약 통계
    // GET /api/statistics/summary
    // 시스템 전체의 예약 수, 매출, 이벤트 수, 활성 채널 수를 하나의 객체로 반환한다
    @GetMapping("/api/statistics/summary")
    public Mono<SummaryStatistics> getSummaryStatistics() {
        return statisticsService.getSummaryStatistics();
    }
}
