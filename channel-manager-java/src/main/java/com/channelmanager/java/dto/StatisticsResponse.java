package com.channelmanager.java.dto; // DTO 패키지

import com.channelmanager.java.domain.EventType; // 이벤트 타입 enum
import java.math.BigDecimal; // 금액 타입

// ==============================
// Phase 8: 통계 응답 DTO 모음
// ==============================
// 각 통계 API에 대응하는 응답 DTO를 정의한다
// Java record로 불변 객체를 생성하며, JSON 직렬화가 자동으로 수행된다
// Kotlin에서는 data class를 사용하지만, Java에서는 record를 사용한다
// Kotlin에서는 하나의 파일에 여러 data class를 정의할 수 있지만,
// Java에서도 하나의 파일에 여러 public record를 정의할 수 없으므로
// 각 DTO를 별도의 내부 파일이 아닌, 하나의 파일에 패키지 전용으로 정의한다
// → 실제로는 각각의 파일로 분리하는 것이 일반적이지만,
//   학습 목적으로 Kotlin과 대응시키기 위해 하나의 파일에 모은다
public class StatisticsResponse {

    // 채널별 통계 DTO
    // GET /api/statistics/channels 의 응답 요소
    // groupBy(channelId) + reduce로 채널별 예약/매출을 집계한 결과이다
    public record ChannelStatistics(
        Long channelId,          // 채널 ID
        String channelCode,      // 채널 코드 (DIRECT, BOOKING, AGODA 등)
        String channelName,      // 채널 표시명 (자사 홈페이지, Booking.com 등)
        long reservationCount,   // 확정 예약 건수
        long cancelledCount,     // 취소 예약 건수
        BigDecimal totalRevenue  // 총 매출 (확정 예약의 totalPrice 합계)
    ) {}

    // 이벤트 타입별 통계 DTO
    // GET /api/statistics/events 의 응답 요소
    // groupBy(eventType) + count()로 이벤트 타입별 발생 건수를 집계한 결과이다
    public record EventStatistics(
        EventType eventType,     // 이벤트 타입 (RESERVATION_CREATED, INVENTORY_UPDATED 등)
        long count               // 해당 타입의 이벤트 발생 건수
    ) {}

    // 객실 타입별 통계 DTO
    // GET /api/statistics/rooms 의 응답 요소
    // groupBy(roomTypeId) + reduce로 객실 타입별 예약/매출을 집계한 결과이다
    public record RoomTypeStatistics(
        Long roomTypeId,         // 객실 타입 ID
        String roomTypeName,     // 객실 타입 이름 (Superior Double 등)
        long reservationCount,   // 예약 건수 (확정 + 취소 포함)
        BigDecimal totalRevenue  // 총 매출 (확정 예약의 totalPrice 합계)
    ) {}

    // 전체 요약 통계 DTO
    // GET /api/statistics/summary 의 응답
    // count(), reduce() 등 기본 집계 연산자로 전체 시스템 현황을 요약한 결과이다
    public record SummaryStatistics(
        long totalReservations,      // 전체 예약 수 (확정 + 취소)
        long confirmedCount,         // 확정 예약 수
        long cancelledCount,         // 취소 예약 수
        BigDecimal totalRevenue,     // 총 매출 (확정 예약 기준)
        long totalEvents,            // 전체 이벤트 수
        long activeChannels          // 활성 채널 수
    ) {}
}
