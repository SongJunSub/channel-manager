package com.channelmanager.kotlin.service // 서비스 패키지 - 비즈니스 로직 계층

import com.channelmanager.kotlin.domain.ReservationStatus // 예약 상태 enum
import com.channelmanager.kotlin.dto.ChannelStatistics // 채널별 통계 DTO
import com.channelmanager.kotlin.dto.EventStatistics // 이벤트 타입별 통계 DTO
import com.channelmanager.kotlin.dto.RoomTypeStatistics // 객실 타입별 통계 DTO
import com.channelmanager.kotlin.dto.SummaryStatistics // 전체 요약 통계 DTO
import com.channelmanager.kotlin.repository.ChannelEventRepository // 이벤트 리포지토리
import com.channelmanager.kotlin.repository.ChannelRepository // 채널 리포지토리
import com.channelmanager.kotlin.repository.ReservationRepository // 예약 리포지토리
import com.channelmanager.kotlin.repository.RoomTypeRepository // 객실 타입 리포지토리
import org.springframework.stereotype.Service // 서비스 계층 어노테이션
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.math.BigDecimal // 금액 타입

// 통계 서비스 — Flux 고급 연산자(groupBy, reduce, count)를 활용한 통계 집계
// DB에서 조회한 데이터를 애플리케이션 레벨에서 Flux 연산자로 집계한다
// SQL GROUP BY로도 구현할 수 있지만, Reactive 스트림 학습 목적으로 Flux 연산자를 사용한다
// @Service로 Spring 빈으로 등록하여 Controller에서 주입받는다
@Service
class StatisticsService(
    private val reservationRepository: ReservationRepository,     // 예약 DB 접근
    private val channelRepository: ChannelRepository,             // 채널 DB 접근
    private val roomTypeRepository: RoomTypeRepository,           // 객실 타입 DB 접근
    private val channelEventRepository: ChannelEventRepository    // 이벤트 DB 접근
) {

    // 채널별 예약/매출 통계
    // 핵심 연산자: groupBy + flatMap + reduce
    // 흐름:
    //   1. 모든 채널을 조회한다
    //   2. 각 채널에 대해 예약을 조회하고 집계한다
    //   3. 확정 예약 건수, 취소 건수, 총 매출을 계산한다
    // flatMap으로 각 채널에 대해 비동기 집계를 수행하고,
    // collectList()로 모든 채널의 통계를 Mono<List>로 수집한다
    fun getChannelStatistics(): Flux<ChannelStatistics> =
        channelRepository.findAll() // 모든 채널 조회
            .flatMap { channel -> // 각 채널에 대해 예약 통계를 집계한다
                // 해당 채널의 모든 예약을 조회한다
                reservationRepository.findByChannelId(channel.id!!)
                    .collectList() // Flux<Reservation> → Mono<List<Reservation>>
                    .map { reservations -> // List<Reservation>에서 통계를 계산한다
                        // 확정 예약 건수: status가 CONFIRMED인 것만 필터링하여 카운트
                        val confirmedCount = reservations
                            .count { it.status == ReservationStatus.CONFIRMED }
                            .toLong()

                        // 취소 예약 건수: status가 CANCELLED인 것만 필터링하여 카운트
                        val cancelledCount = reservations
                            .count { it.status == ReservationStatus.CANCELLED }
                            .toLong()

                        // 총 매출: 확정 예약의 totalPrice를 합산한다
                        // fold: Kotlin의 누적 집계 함수 (reduce와 유사, 초기값 지정 가능)
                        // BigDecimal::add: 두 BigDecimal을 더하는 함수 참조
                        val totalRevenue = reservations
                            .filter { it.status == ReservationStatus.CONFIRMED }
                            .fold(BigDecimal.ZERO) { acc, reservation ->
                                acc.add(reservation.totalPrice ?: BigDecimal.ZERO)
                            }

                        // ChannelStatistics DTO 생성
                        ChannelStatistics(
                            channelId = channel.id,
                            channelCode = channel.channelCode,
                            channelName = channel.channelName,
                            reservationCount = confirmedCount,
                            cancelledCount = cancelledCount,
                            totalRevenue = totalRevenue
                        )
                    }
            }

    // 이벤트 타입별 발생 건수 통계
    // 핵심 연산자: groupBy + count
    // 흐름:
    //   1. 모든 이벤트를 조회한다
    //   2. eventType으로 그룹을 분할한다 (GroupedFlux)
    //   3. 각 그룹의 요소 수를 센다
    // groupBy: Flux를 키 기준으로 여러 GroupedFlux로 분할하는 핵심 연산자
    // GroupedFlux<K, V>: key()로 그룹 키에 접근하고, 내부는 일반 Flux처럼 사용한다
    fun getEventStatistics(): Flux<EventStatistics> =
        channelEventRepository.findAll() // 모든 이벤트 조회
            .groupBy { it.eventType } // eventType 기준으로 그룹 분할
            // groupBy 반환: Flux<GroupedFlux<EventType, ChannelEvent>>
            // 각 GroupedFlux는 같은 eventType을 가진 이벤트들의 서브스트림이다
            .flatMap { group -> // 각 그룹(GroupedFlux)에 대해
                group.count() // 그룹 내 요소 개수를 센다 → Mono<Long>
                    .map { count -> // (eventType, count) 쌍으로 DTO 생성
                        EventStatistics(
                            eventType = group.key()!!, // 그룹 키 = eventType
                            count = count               // 해당 타입의 이벤트 수
                        )
                    }
            }

    // 객실 타입별 예약/매출 통계
    // 핵심 연산자: flatMap + collectList + fold (reduce 패턴)
    // 흐름:
    //   1. 모든 객실 타입을 조회한다
    //   2. 각 객실 타입에 대해 예약을 조회하고 집계한다
    //   3. 예약 건수, 확정 예약의 매출 합계를 계산한다
    fun getRoomTypeStatistics(): Flux<RoomTypeStatistics> =
        roomTypeRepository.findAll() // 모든 객실 타입 조회
            .flatMap { roomType -> // 각 객실 타입에 대해 예약 통계를 집계한다
                reservationRepository.findByRoomTypeId(roomType.id!!)
                    .collectList() // Flux → Mono<List>
                    .map { reservations ->
                        // 총 예약 건수 (확정 + 취소 포함)
                        val reservationCount = reservations.size.toLong()

                        // 총 매출: 확정 예약의 totalPrice 합산
                        // fold: 초기값(ZERO)부터 시작하여 각 예약의 금액을 더한다
                        val totalRevenue = reservations
                            .filter { it.status == ReservationStatus.CONFIRMED }
                            .fold(BigDecimal.ZERO) { acc, reservation ->
                                acc.add(reservation.totalPrice ?: BigDecimal.ZERO)
                            }

                        RoomTypeStatistics(
                            roomTypeId = roomType.id,
                            roomTypeName = roomType.roomTypeName,
                            reservationCount = reservationCount,
                            totalRevenue = totalRevenue
                        )
                    }
            }

    // 전체 요약 통계
    // 핵심 연산자: count, reduce, Mono.zip
    // 흐름:
    //   1. 여러 통계 값을 각각 Mono로 조회한다
    //   2. Mono.zip()으로 모든 결과를 합쳐서 하나의 SummaryStatistics로 만든다
    // Mono.zip: 여러 Mono를 병렬로 실행하고, 모두 완료되면 결과를 조합한다
    //   — 각 Mono가 독립적이므로 병렬 실행이 가능하다 (성능 우수)
    fun getSummaryStatistics(): Mono<SummaryStatistics> {
        // 각 통계 값을 독립적인 Mono로 조회한다
        val totalReservations = reservationRepository.count() // 전체 예약 수
        val confirmedCount = reservationRepository // 확정 예약 수
            .findByStatus(ReservationStatus.CONFIRMED)
            .count()
        val cancelledCount = reservationRepository // 취소 예약 수
            .findByStatus(ReservationStatus.CANCELLED)
            .count()
        val totalRevenue = reservationRepository // 총 매출 (확정 예약 기준)
            .findByStatus(ReservationStatus.CONFIRMED)
            .map { it.totalPrice ?: BigDecimal.ZERO } // 금액 추출
            .reduce(BigDecimal.ZERO, BigDecimal::add) // 합산
            // reduce가 빈 스트림이면 Mono.empty()를 반환하므로 기본값을 설정한다
            .defaultIfEmpty(BigDecimal.ZERO)
        val totalEvents = channelEventRepository.count() // 전체 이벤트 수
        val activeChannels = channelRepository // 활성 채널 수
            .findByIsActive(true)
            .count()

        // Mono.zip: 6개의 Mono를 병렬로 실행하고, 모두 완료되면 결합한다
        // TupleN: zip의 결과 타입 — t1, t2, ... tN으로 각 Mono의 결과에 접근한다
        return Mono.zip(
            totalReservations,
            confirmedCount,
            cancelledCount,
            totalRevenue,
            totalEvents,
            activeChannels
        ).map { tuple -> // Tuple6 → SummaryStatistics 변환
            SummaryStatistics(
                totalReservations = tuple.t1, // 전체 예약 수
                confirmedCount = tuple.t2,    // 확정 예약 수
                cancelledCount = tuple.t3,    // 취소 예약 수
                totalRevenue = tuple.t4,      // 총 매출
                totalEvents = tuple.t5,       // 전체 이벤트 수
                activeChannels = tuple.t6     // 활성 채널 수
            )
        }
    }
}
