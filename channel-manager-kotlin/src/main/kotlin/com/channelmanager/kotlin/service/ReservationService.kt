package com.channelmanager.kotlin.service // 서비스 패키지 - 비즈니스 로직 계층

import com.channelmanager.kotlin.domain.ChannelEvent // 채널 이벤트 엔티티
import com.channelmanager.kotlin.domain.EventType // 이벤트 타입 enum
import com.channelmanager.kotlin.domain.Reservation // 예약 엔티티
import com.channelmanager.kotlin.domain.ReservationStatus // 예약 상태 enum
import com.channelmanager.kotlin.dto.ReservationCreateRequest // 예약 생성 요청 DTO
import com.channelmanager.kotlin.dto.ReservationResponse // 예약 응답 DTO
import com.channelmanager.kotlin.exception.BadRequestException // 400 Bad Request 예외
import com.channelmanager.kotlin.exception.NotFoundException // 404 Not Found 예외
import com.channelmanager.kotlin.repository.ChannelEventRepository // 이벤트 리포지토리
import com.channelmanager.kotlin.repository.ChannelRepository // 채널 리포지토리
import com.channelmanager.kotlin.repository.InventoryRepository // 재고 리포지토리
import com.channelmanager.kotlin.repository.ReservationRepository // 예약 리포지토리
import com.channelmanager.kotlin.repository.RoomTypeRepository // 객실 타입 리포지토리
import org.springframework.stereotype.Service // 서비스 계층 어노테이션
import org.springframework.transaction.annotation.Transactional // 트랜잭션 어노테이션
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.math.BigDecimal // 금액 처리용 정밀 숫자 타입

// 예약 서비스 - 예약 생성 + 재고 차감 + 이벤트 기록을 담당한다
// 여러 테이블(Reservation, Inventory, ChannelEvent)에 걸친 트랜잭션이 필요하다
// @Service로 Spring 빈으로 등록하고, 생성자 주입으로 의존성을 받는다
@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,     // 예약 DB 접근
    private val channelRepository: ChannelRepository,             // 채널 DB 접근
    private val roomTypeRepository: RoomTypeRepository,           // 객실 타입 DB 접근
    private val inventoryRepository: InventoryRepository,         // 재고 DB 접근
    private val channelEventRepository: ChannelEventRepository    // 이벤트 DB 접근
) {

    // 예약 생성 — 핵심 비즈니스 로직
    // 7단계의 Reactive 체인으로 구성된다:
    // 1. 요청 유효성 검증 (체크인 < 체크아웃)
    // 2. channelCode로 Channel 조회 + 활성 상태 확인
    // 3. roomTypeId로 RoomType 조회 + basePrice 가져옴
    // 4. 체크인~체크아웃 기간의 재고 확인 + 차감
    // 5. Reservation 저장 (status=CONFIRMED, totalPrice 계산)
    // 6. ChannelEvent 기록 (RESERVATION_CREATED)
    // 7. DTO 변환하여 반환
    // @Transactional: 재고 차감 + 예약 저장 + 이벤트 기록이 하나의 트랜잭션으로 묶인다
    //   하나라도 실패하면 전체 롤백되어 데이터 정합성을 보장한다
    @Transactional
    fun createReservation(request: ReservationCreateRequest): Mono<ReservationResponse> =
        validateReservationRequest(request) // 1단계: 요청 유효성 검증
            .then(
                channelRepository.findByChannelCode(request.channelCode) // 2단계: 채널 조회
                    .switchIfEmpty(Mono.error(
                        NotFoundException("채널을 찾을 수 없습니다. code=${request.channelCode}")
                    ))
            )
            .filter { it.isActive } // 채널이 활성 상태인지 확인 (filter: false면 빈 Mono)
            .switchIfEmpty(Mono.error(
                BadRequestException("비활성 채널입니다. code=${request.channelCode}")
            ))
            .flatMap { channel -> // 채널 조회 완료 → 객실 타입 조회
                roomTypeRepository.findById(request.roomTypeId) // 3단계: 객실 타입 조회
                    .switchIfEmpty(Mono.error(
                        NotFoundException("객실 타입을 찾을 수 없습니다. id=${request.roomTypeId}")
                    ))
                    .flatMap { roomType -> // 객실 타입 조회 완료 → 재고 차감
                        decreaseInventory(request) // 4단계: 체크인~체크아웃 기간 재고 차감
                            .then(Mono.defer { // 재고 차감 완료 → 예약 저장
                                // 5단계: 총 금액 계산 = basePrice × 숙박일수 × 객실수
                                // 숙박일수: 체크인 ~ 체크아웃 전날까지의 날짜 수
                                // ChronoUnit.DAYS가 아닌 datesUntil().count()로 계산한다
                                val nights = request.checkInDate
                                    .datesUntil(request.checkOutDate) // 체크인~체크아웃 전날 Stream
                                    .count() // 숙박일수
                                val totalPrice = roomType.basePrice // 1박 기본 가격
                                    .multiply(BigDecimal.valueOf(nights)) // × 숙박일수
                                    .multiply(BigDecimal.valueOf(request.roomQuantity.toLong())) // × 객실수

                                reservationRepository.save( // 예약 저장
                                    Reservation(
                                        channelId = channel.id!!,                   // 채널 ID
                                        roomTypeId = request.roomTypeId,            // 객실 타입 ID
                                        checkInDate = request.checkInDate,          // 체크인 날짜
                                        checkOutDate = request.checkOutDate,        // 체크아웃 날짜
                                        guestName = request.guestName,              // 투숙객 이름
                                        roomQuantity = request.roomQuantity,        // 객실 수
                                        status = ReservationStatus.CONFIRMED,       // 예약 확정
                                        totalPrice = totalPrice                     // 총 금액
                                    )
                                )
                            })
                            .flatMap { reservation -> // 예약 저장 완료 → 이벤트 기록
                                // 6단계: ChannelEvent 기록 (RESERVATION_CREATED)
                                // eventPayload에 예약 정보를 JSON 형태로 기록한다
                                channelEventRepository.save(
                                    ChannelEvent(
                                        eventType = EventType.RESERVATION_CREATED,  // 이벤트 타입
                                        channelId = channel.id,                     // 채널 ID
                                        reservationId = reservation.id,             // 예약 ID
                                        roomTypeId = request.roomTypeId,            // 객실 타입 ID
                                        eventPayload = """{"guestName":"${request.guestName}",""" +
                                            """"roomQuantity":${request.roomQuantity},""" +
                                            """"checkIn":"${request.checkInDate}",""" +
                                            """"checkOut":"${request.checkOutDate}"}"""
                                    )
                                ).thenReturn(reservation) // 이벤트 저장 결과는 무시하고 예약 반환
                            }
                            .map { reservation -> // 7단계: DTO 변환
                                ReservationResponse.from(reservation, channel.channelCode)
                            }
                    }
            }

    // 재고 차감 — 체크인 ~ 체크아웃 기간의 모든 날짜에서 재고를 차감한다
    // 체크아웃 당일은 숙박하지 않으므로 재고 차감에서 제외한다 (datesUntil은 종료일 미포함)
    // concatMap: 날짜 순서대로 순차 처리하여 동시성 문제를 줄인다
    // 각 날짜에 대해:
    //   1. 해당 날짜의 재고 조회 (없으면 404)
    //   2. 가용 수량 확인 (부족하면 400)
    //   3. 가용 수량 차감 후 저장
    private fun decreaseInventory(
        request: ReservationCreateRequest
    ): Mono<Void> =
        Flux.fromStream( // Java Stream을 Flux로 변환 (날짜 범위 생성)
            request.checkInDate.datesUntil(request.checkOutDate) // 체크인 ~ 체크아웃 전날
        )
        .concatMap { date -> // 각 날짜에 대해 순차적으로 재고 차감
            inventoryRepository.findByRoomTypeIdAndStockDate( // 해당 날짜의 재고 조회
                request.roomTypeId, date
            )
            .switchIfEmpty(Mono.error( // 재고가 없으면 404 에러
                NotFoundException("$date 날짜의 재고를 찾을 수 없습니다. roomTypeId=${request.roomTypeId}")
            ))
            .flatMap { inventory -> // 재고 조회 완료 → 수량 확인 + 차감
                if (inventory.availableQuantity < request.roomQuantity) { // 가용 수량 부족
                    Mono.error(BadRequestException(
                        "$date 날짜의 재고가 부족합니다." +
                            " available=${inventory.availableQuantity}," +
                            " requested=${request.roomQuantity}"
                    ))
                } else { // 수량 충분 → 차감 후 저장
                    inventoryRepository.save( // copy()로 가용 수량만 변경한 새 인스턴스 저장
                        inventory.copy(
                            availableQuantity = inventory.availableQuantity - request.roomQuantity
                        )
                    )
                }
            }
        }
        .then() // Flux<Inventory> → Mono<Void>: 모든 날짜 처리 완료 시그널만 반환

    // 예약 요청 유효성 검증
    // 체크인 날짜가 체크아웃 날짜 이후이면 400 Bad Request
    // 객실 수가 1 미만이면 400 Bad Request
    private fun validateReservationRequest(
        request: ReservationCreateRequest
    ): Mono<Void> =
        Mono.defer { // 구독 시점에 검증 실행 (지연 평가)
            when {
                !request.checkInDate.isBefore(request.checkOutDate) -> // 체크인 >= 체크아웃
                    Mono.error(BadRequestException(
                        "체크인 날짜는 체크아웃 날짜보다 이전이어야 합니다." +
                            " checkIn=${request.checkInDate}, checkOut=${request.checkOutDate}"
                    ))
                request.roomQuantity < 1 -> // 객실 수 0 이하
                    Mono.error(BadRequestException(
                        "객실 수량은 1 이상이어야 합니다. roomQuantity=${request.roomQuantity}"
                    ))
                else -> Mono.empty() // 검증 통과
            }
        }
}
