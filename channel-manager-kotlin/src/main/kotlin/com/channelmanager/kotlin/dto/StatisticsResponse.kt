package com.channelmanager.kotlin.dto // DTO 패키지

import com.channelmanager.kotlin.domain.EventType // 이벤트 타입 enum
import java.math.BigDecimal // 금액 타입

// ==============================
// Phase 8: 통계 응답 DTO 모음
// ==============================
// 각 통계 API에 대응하는 응답 DTO를 정의한다
// data class로 불변 객체를 생성하며, JSON 직렬화가 자동으로 수행된다

// 채널별 통계 DTO
// GET /api/statistics/channels 의 응답 요소
// groupBy(channelId) + reduce로 채널별 예약/매출을 집계한 결과이다
data class ChannelStatistics(
    val channelId: Long,          // 채널 ID
    val channelCode: String,      // 채널 코드 (DIRECT, BOOKING, AGODA 등)
    val channelName: String,      // 채널 표시명 (자사 홈페이지, Booking.com 등)
    val reservationCount: Long,   // 확정 예약 건수
    val cancelledCount: Long,     // 취소 예약 건수
    val totalRevenue: BigDecimal  // 총 매출 (확정 예약의 totalPrice 합계)
)

// 이벤트 타입별 통계 DTO
// GET /api/statistics/events 의 응답 요소
// groupBy(eventType) + count()로 이벤트 타입별 발생 건수를 집계한 결과이다
data class EventStatistics(
    val eventType: EventType,     // 이벤트 타입 (RESERVATION_CREATED, INVENTORY_UPDATED 등)
    val count: Long               // 해당 타입의 이벤트 발생 건수
)

// 객실 타입별 통계 DTO
// GET /api/statistics/rooms 의 응답 요소
// groupBy(roomTypeId) + reduce로 객실 타입별 예약/매출을 집계한 결과이다
data class RoomTypeStatistics(
    val roomTypeId: Long,         // 객실 타입 ID
    val roomTypeName: String,     // 객실 타입 이름 (Superior Double 등)
    val reservationCount: Long,   // 예약 건수 (확정 + 취소 포함)
    val totalRevenue: BigDecimal  // 총 매출 (확정 예약의 totalPrice 합계)
)

// 전체 요약 통계 DTO
// GET /api/statistics/summary 의 응답
// count(), reduce() 등 기본 집계 연산자로 전체 시스템 현황을 요약한 결과이다
data class SummaryStatistics(
    val totalReservations: Long,      // 전체 예약 수 (확정 + 취소)
    val confirmedCount: Long,         // 확정 예약 수
    val cancelledCount: Long,         // 취소 예약 수
    val totalRevenue: BigDecimal,     // 총 매출 (확정 예약 기준)
    val totalEvents: Long,            // 전체 이벤트 수
    val activeChannels: Long          // 활성 채널 수
)
