package com.channelmanager.java.repository; // 리포지토리 패키지

import com.channelmanager.java.domain.Reservation; // 예약 엔티티
import com.channelmanager.java.domain.ReservationStatus; // 예약 상태 enum
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // 리액티브 CRUD 리포지토리
import reactor.core.publisher.Flux; // 0~N개 결과를 비동기로 반환하는 타입

// 예약 리포지토리
public interface ReservationRepository extends ReactiveCrudRepository<Reservation, Long> {

    // 특정 채널의 예약 목록 조회
    Flux<Reservation> findByChannelId(Long channelId);

    // 특정 객실 타입의 예약 목록 조회
    Flux<Reservation> findByRoomTypeId(Long roomTypeId);

    // 예약 상태별 조회 (CONFIRMED, CANCELLED)
    Flux<Reservation> findByStatus(ReservationStatus status);
}
