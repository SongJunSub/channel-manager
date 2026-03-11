package com.channelmanager.kotlin.repository // 리포지토리 패키지

import com.channelmanager.kotlin.domain.Reservation // 예약 엔티티
import com.channelmanager.kotlin.domain.ReservationStatus // 예약 상태 enum
import org.springframework.data.repository.reactive.ReactiveCrudRepository // 리액티브 CRUD 리포지토리
import reactor.core.publisher.Flux // 0~N개 결과를 비동기로 반환하는 타입

// 예약 리포지토리
interface ReservationRepository : ReactiveCrudRepository<Reservation, Long> {

    // 특정 채널의 예약 목록 조회
    fun findByChannelId(channelId: Long): Flux<Reservation>

    // 특정 객실 타입의 예약 목록 조회
    fun findByRoomTypeId(roomTypeId: Long): Flux<Reservation>

    // 예약 상태별 조회 (CONFIRMED, CANCELLED)
    fun findByStatus(status: ReservationStatus): Flux<Reservation>
}
