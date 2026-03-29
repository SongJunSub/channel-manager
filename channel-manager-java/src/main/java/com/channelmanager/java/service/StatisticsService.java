package com.channelmanager.java.service; // 서비스 패키지 - 비즈니스 로직 계층

import com.channelmanager.java.domain.ReservationStatus; // 예약 상태 enum
import com.channelmanager.java.dto.StatisticsResponse.ChannelStatistics; // 채널별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.EventStatistics; // 이벤트 타입별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.RoomTypeStatistics; // 객실 타입별 통계 DTO
import com.channelmanager.java.dto.StatisticsResponse.SummaryStatistics; // 전체 요약 통계 DTO
import com.channelmanager.java.repository.ChannelEventRepository; // 이벤트 리포지토리
import com.channelmanager.java.repository.ChannelRepository; // 채널 리포지토리
import com.channelmanager.java.repository.ReservationRepository; // 예약 리포지토리
import com.channelmanager.java.repository.RoomTypeRepository; // 객실 타입 리포지토리
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 (Lombok)
import org.springframework.stereotype.Service; // 서비스 계층 어노테이션
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.math.BigDecimal; // 금액 타입

// 통계 서비스 — Flux 고급 연산자(groupBy, reduce, count)를 활용한 통계 집계
// DB에서 조회한 데이터를 애플리케이션 레벨에서 Flux 연산자로 집계한다
// SQL GROUP BY로도 구현할 수 있지만, Reactive 스트림 학습 목적으로 Flux 연산자를 사용한다
// @Service로 Spring 빈으로 등록하여 Controller에서 주입받는다
// @RequiredArgsConstructor: Lombok이 final 필드에 대한 생성자를 자동 생성한다
// Kotlin에서는 primary constructor에 val로 선언하지만,
// Java에서는 @RequiredArgsConstructor + private final 필드로 동일한 효과를 얻는다
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final ReservationRepository reservationRepository;     // 예약 DB 접근
    private final ChannelRepository channelRepository;             // 채널 DB 접근
    private final RoomTypeRepository roomTypeRepository;           // 객실 타입 DB 접근
    private final ChannelEventRepository channelEventRepository;   // 이벤트 DB 접근

    // 채널별 예약/매출 통계
    // 핵심 연산자: groupBy + flatMap + reduce
    // 흐름:
    //   1. 모든 채널을 조회한다
    //   2. 각 채널에 대해 예약을 조회하고 집계한다
    //   3. 확정 예약 건수, 취소 건수, 총 매출을 계산한다
    // Kotlin에서는 it 키워드와 fold()를 사용하지만,
    // Java에서는 명시적 람다 파라미터와 stream().reduce()를 사용한다
    public Flux<ChannelStatistics> getChannelStatistics() {
        return channelRepository.findAll() // 모든 채널 조회
            .flatMap(channel -> // 각 채널에 대해 예약 통계를 집계한다
                reservationRepository.findByChannelId(channel.getId())
                    .collectList() // Flux<Reservation> → Mono<List<Reservation>>
                    .map(reservations -> { // List<Reservation>에서 통계를 계산한다
                        // 확정 예약 건수
                        // Kotlin에서는 count { it.status == ... }이지만,
                        // Java에서는 stream().filter().count()를 사용한다
                        long confirmedCount = reservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                            .count();

                        // 취소 예약 건수
                        long cancelledCount = reservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.CANCELLED)
                            .count();

                        // 총 매출: 확정 예약의 totalPrice 합산
                        // Kotlin에서는 fold(BigDecimal.ZERO) { acc, r -> ... }이지만,
                        // Java에서는 stream().map().reduce()를 사용한다
                        BigDecimal totalRevenue = reservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                            .map(r -> r.getTotalPrice() != null
                                ? r.getTotalPrice() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                        return new ChannelStatistics(
                            channel.getId(),
                            channel.getChannelCode(),
                            channel.getChannelName(),
                            confirmedCount,
                            cancelledCount,
                            totalRevenue
                        );
                    })
            );
    }

    // 이벤트 타입별 발생 건수 통계
    // 핵심 연산자: groupBy + count
    // 흐름:
    //   1. 모든 이벤트를 조회한다
    //   2. eventType으로 그룹을 분할한다 (GroupedFlux)
    //   3. 각 그룹의 요소 수를 센다
    // groupBy: Flux를 키 기준으로 여러 GroupedFlux로 분할하는 핵심 연산자
    // Kotlin에서는 groupBy { it.eventType }이지만,
    // Java에서는 groupBy(event -> event.getEventType())를 사용한다
    public Flux<EventStatistics> getEventStatistics() {
        return channelEventRepository.findAll() // 모든 이벤트 조회
            .groupBy(event -> event.getEventType()) // eventType 기준으로 그룹 분할
            // groupBy 반환: Flux<GroupedFlux<EventType, ChannelEvent>>
            .flatMap(group -> // 각 그룹(GroupedFlux)에 대해
                group.count() // 그룹 내 요소 개수를 센다 → Mono<Long>
                    .map(count -> // (eventType, count) 쌍으로 DTO 생성
                        new EventStatistics(
                            group.key(), // 그룹 키 = eventType
                            count         // 해당 타입의 이벤트 수
                        )
                    )
            );
    }

    // 객실 타입별 예약/매출 통계
    // 핵심 연산자: flatMap + collectList + stream().reduce()
    // Kotlin에서는 fold()를 사용하지만, Java에서는 stream().reduce()를 사용한다
    public Flux<RoomTypeStatistics> getRoomTypeStatistics() {
        return roomTypeRepository.findAll() // 모든 객실 타입 조회
            .flatMap(roomType -> // 각 객실 타입에 대해 예약 통계를 집계한다
                reservationRepository.findByRoomTypeId(roomType.getId())
                    .collectList() // Flux → Mono<List>
                    .map(reservations -> {
                        // 총 예약 건수 (확정 + 취소 포함)
                        long reservationCount = reservations.size();

                        // 총 매출: 확정 예약의 totalPrice 합산
                        BigDecimal totalRevenue = reservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                            .map(r -> r.getTotalPrice() != null
                                ? r.getTotalPrice() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                        return new RoomTypeStatistics(
                            roomType.getId(),
                            roomType.getRoomTypeName(),
                            reservationCount,
                            totalRevenue
                        );
                    })
            );
    }

    // 전체 요약 통계
    // 핵심 연산자: count, reduce, Mono.zip
    // Mono.zip: 여러 Mono를 병렬로 실행하고, 모두 완료되면 결과를 조합한다
    // Kotlin에서는 tuple.t1, tuple.t2로 접근하지만, Java에서도 동일하다
    public Mono<SummaryStatistics> getSummaryStatistics() {
        // 각 통계 값을 독립적인 Mono로 조회한다
        Mono<Long> totalReservations = reservationRepository.count();
        Mono<Long> confirmedCount = reservationRepository
            .findByStatus(ReservationStatus.CONFIRMED)
            .count();
        Mono<Long> cancelledCount = reservationRepository
            .findByStatus(ReservationStatus.CANCELLED)
            .count();
        Mono<BigDecimal> totalRevenue = reservationRepository
            .findByStatus(ReservationStatus.CONFIRMED)
            .map(r -> r.getTotalPrice() != null ? r.getTotalPrice() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .defaultIfEmpty(BigDecimal.ZERO);
        Mono<Long> totalEvents = channelEventRepository.count();
        Mono<Long> activeChannels = channelRepository
            .findByIsActive(true)
            .count();

        // Mono.zip: 6개의 Mono를 병렬로 실행하고, 모두 완료되면 결합한다
        return Mono.zip(
            totalReservations,
            confirmedCount,
            cancelledCount,
            totalRevenue,
            totalEvents,
            activeChannels
        ).map(tuple -> new SummaryStatistics(
            tuple.getT1(), // 전체 예약 수
            tuple.getT2(), // 확정 예약 수
            tuple.getT3(), // 취소 예약 수
            tuple.getT4(), // 총 매출
            tuple.getT5(), // 전체 이벤트 수
            tuple.getT6()  // 활성 채널 수
        ));
    }
}
