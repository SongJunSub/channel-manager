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
import java.time.LocalDate // 날짜 타입 (필터링용)

// 예약 서비스 - 예약 CRUD + 재고 관리 + 이벤트 기록을 담당한다
// Phase 3: 예약 생성 (POST), Phase 7: 예약 취소 (DELETE), Phase 9: 예약 조회 (GET)
// @Service로 Spring 빈으로 등록하고, 생성자 주입으로 의존성을 받는다
@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,     // 예약 DB 접근
    private val channelRepository: ChannelRepository,             // 채널 DB 접근
    private val roomTypeRepository: RoomTypeRepository,           // 객실 타입 DB 접근
    private val inventoryRepository: InventoryRepository,         // 재고 DB 접근
    private val channelEventRepository: ChannelEventRepository,   // 이벤트 DB 접근
    private val eventPublisher: EventPublisher                    // Phase 4: 이벤트 발행 서비스
) {

    // ===== Phase 9: 예약 조회 =====

    // 예약 단건 조회
    // ID로 예약을 조회하고, 채널 코드를 풍부화(enrich)하여 DTO로 반환한다
    // R2DBC는 JOIN을 지원하지 않으므로, 채널을 별도로 조회하여 channelCode를 채운다
    // switchIfEmpty: 예약이 없으면 404 에러
    // flatMap: 예약 조회 후 채널을 비동기로 추가 조회하는 체인
    fun getReservation(reservationId: Long): Mono<ReservationResponse> =
        reservationRepository.findById(reservationId) // 예약 조회
            .switchIfEmpty(Mono.error( // 없으면 404
                NotFoundException("예약을 찾을 수 없습니다. id=$reservationId")
            ))
            .flatMap { reservation -> // 예약 조회 완료 → 채널 코드 풍부화
                channelRepository.findById(reservation.channelId) // 채널 조회
                    .map { channel -> // 채널 코드로 DTO 변환
                        ReservationResponse.from(reservation, channel.channelCode)
                    }
            }

    // 예약 목록 조회 — 선택적 필터링 + 페이징
    // 모든 필터 파라미터는 nullable — null이면 해당 조건을 무시한다
    // 흐름:
    //   1. 채널 정보를 미리 Map으로 로드 (N+1 방지)
    //   2. 전체 예약을 조회한다
    //   3. Flux.filter()로 애플리케이션 레벨 필터링
    //   4. skip()/take()로 페이징 처리
    //   5. DTO 변환 (채널 Map에서 channelCode 참조)
    // collectMap: 채널 정보를 Map<channelId, channelCode>로 미리 수집
    //   — 예약마다 채널을 개별 조회하는 N+1 문제를 방지한다
    // flatMapMany: Mono<Map> → Flux<ReservationResponse> 변환
    fun listReservations(
        channelId: Long?,               // 채널 ID 필터 (null이면 전체)
        status: ReservationStatus?,      // 예약 상태 필터 (null이면 전체)
        startDate: LocalDate?,           // 체크인 시작일 필터 (이후)
        endDate: LocalDate?,             // 체크인 종료일 필터 (이전)
        page: Int,                       // 페이지 번호 (0부터)
        size: Int                        // 페이지 크기
    ): Flux<ReservationResponse> =
        channelRepository.findAll() // 1단계: 모든 채널을 미리 로드
            .collectMap({ it.id!! }, { it.channelCode }) // Map<Long, String> 수집
            .flatMapMany { channelMap -> // Mono<Map> → Flux<Response> 변환
                reservationRepository.findAll() // 2단계: 전체 예약 조회
                    // 3단계: Flux.filter()로 선택적 필터링
                    // channelId가 null이면 조건을 건너뛴다 (전체 통과)
                    .filter { channelId == null || it.channelId == channelId }
                    // status가 null이면 조건을 건너뛴다
                    .filter { status == null || it.status == status }
                    // startDate가 null이면 조건을 건너뛴다
                    // isBefore의 반대인 !isBefore: startDate 이후의 체크인만 통과
                    .filter { startDate == null || !it.checkInDate.isBefore(startDate) }
                    // endDate가 null이면 조건을 건너뛴다
                    // isAfter의 반대인 !isAfter: endDate 이전의 체크인만 통과
                    .filter { endDate == null || !it.checkInDate.isAfter(endDate) }
                    // 4단계: 페이징 — skip()으로 앞부분 건너뛰고, take()로 제한
                    // page=0, size=20 → skip(0), take(20) → 첫 20개
                    // page=1, size=20 → skip(20), take(20) → 21~40번째
                    .skip((page * size).toLong()) // offset
                    .take(size.toLong())           // limit
                    // 5단계: DTO 변환 — 미리 로드한 channelMap에서 코드 참조
                    .map { reservation ->
                        ReservationResponse.from(
                            reservation,
                            channelMap[reservation.channelId] ?: "UNKNOWN"
                        )
                    }
            }

    // ===== Phase 3: 예약 생성 =====

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
                                )
                                .doOnNext { savedEvent -> // Phase 4: DB 저장 후 Sinks에 발행
                                    // doOnNext: 스트림 흐름을 변경하지 않는 부수 효과(side-effect)
                                    // DB 저장이 성공한 후에만 Sinks에 발행한다
                                    // 발행 실패가 전체 트랜잭션을 롤백시키지 않도록 fire-and-forget
                                    eventPublisher.publish(savedEvent) // Sinks에 이벤트 발행
                                }
                                .thenReturn(reservation) // 이벤트 저장 결과는 무시하고 예약 반환
                            }
                            .map { reservation -> // 7단계: DTO 변환
                                ReservationResponse.from(reservation, channel.channelCode)
                            }
                    }
            }

    // 예약 취소 — 보상 트랜잭션 (Compensating Transaction)
    // Phase 4에서 생성한 예약을 취소하고, 차감했던 재고를 복구한다
    // createReservation()과 대칭적인 Reactive 체인으로 구성된다:
    // 1. 예약 ID로 Reservation 조회 (없으면 404)
    // 2. 상태 확인 — CONFIRMED만 취소 가능 (이미 CANCELLED면 400)
    // 3. 채널 조회 — 응답 DTO에 channelCode를 포함하기 위해
    // 4. 재고 복구 — 체크인~체크아웃 기간의 availableQuantity를 증가시킨다
    // 5. 예약 상태 변경 — CONFIRMED → CANCELLED
    // 6. 이벤트 기록 — RESERVATION_CANCELLED + Sinks 발행
    // 7. DTO 변환하여 반환
    // @Transactional: 재고 복구 + 상태 변경 + 이벤트 기록이 하나의 트랜잭션으로 묶인다
    //   재고 복구가 실패하면 상태 변경도 롤백되어 데이터 정합성을 보장한다
    @Transactional
    fun cancelReservation(reservationId: Long): Mono<ReservationResponse> =
        reservationRepository.findById(reservationId) // 1단계: 예약 조회
            .switchIfEmpty(Mono.error( // 예약이 없으면 404 에러
                NotFoundException("예약을 찾을 수 없습니다. id=$reservationId")
            ))
            .flatMap { reservation -> // 예약 조회 완료 → 상태 확인
                // 2단계: 상태 확인 — CONFIRMED만 취소 가능
                // 이미 CANCELLED인 예약을 다시 취소하면 400 에러
                if (reservation.status != ReservationStatus.CONFIRMED) {
                    return@flatMap Mono.error<ReservationResponse>(
                        BadRequestException("이미 취소된 예약입니다. id=$reservationId")
                    )
                }

                // 3단계: 채널 조회 — channelCode를 응답 DTO에 포함하기 위해 조회한다
                channelRepository.findById(reservation.channelId)
                    .switchIfEmpty(Mono.error(
                        NotFoundException("채널을 찾을 수 없습니다. id=${reservation.channelId}")
                    ))
                    .flatMap { channel -> // 채널 조회 완료 → 재고 복구
                        // 4단계: 재고 복구 — decreaseInventory와 대칭적인 increaseInventory
                        increaseInventory(reservation)
                            .then(Mono.defer { // 재고 복구 완료 → 예약 상태 변경
                                // 5단계: 예약 상태를 CANCELLED로 변경
                                // copy(): data class의 일부 필드만 변경한 새 인스턴스를 생성한다
                                // Java에서는 setter로 직접 변경하지만,
                                // Kotlin에서는 불변 객체를 선호하므로 copy()를 사용한다
                                reservationRepository.save(
                                    reservation.copy(status = ReservationStatus.CANCELLED)
                                )
                            })
                            .flatMap { cancelledReservation -> // 상태 변경 완료 → 이벤트 기록
                                // 6단계: ChannelEvent 기록 (RESERVATION_CANCELLED)
                                // eventPayload에 취소된 예약 정보를 JSON 형태로 기록한다
                                channelEventRepository.save(
                                    ChannelEvent(
                                        eventType = EventType.RESERVATION_CANCELLED, // 취소 이벤트 타입
                                        channelId = reservation.channelId,           // 채널 ID
                                        reservationId = reservation.id,              // 예약 ID
                                        roomTypeId = reservation.roomTypeId,         // 객실 타입 ID
                                        eventPayload = """{"guestName":"${reservation.guestName}",""" +
                                            """"roomQuantity":${reservation.roomQuantity},""" +
                                            """"checkIn":"${reservation.checkInDate}",""" +
                                            """"checkOut":"${reservation.checkOutDate}"}"""
                                    )
                                )
                                .doOnNext { savedEvent -> // DB 저장 후 Sinks에 발행
                                    // SSE를 통해 대시보드에 실시간으로 취소 이벤트를 전달한다
                                    eventPublisher.publish(savedEvent) // Sinks에 이벤트 발행
                                }
                                .thenReturn(cancelledReservation) // 이벤트 저장 결과는 무시하고 예약 반환
                            }
                            .map { cancelledReservation -> // 7단계: DTO 변환
                                ReservationResponse.from(cancelledReservation, channel.channelCode)
                            }
                    }
            }

    // 재고 복구 — 체크인 ~ 체크아웃 기간의 모든 날짜에서 재고를 증가시킨다
    // decreaseInventory()와 대칭적인 보상 로직이다
    // concatMap: 날짜 순서대로 순차 처리 (동시성 문제 방지)
    // FOR UPDATE 비관적 잠금: 동시에 같은 재고를 복구/차감하는 요청의 충돌 방지
    private fun increaseInventory(reservation: Reservation): Mono<Void> =
        Flux.fromStream( // 체크인 ~ 체크아웃 전날까지의 날짜 범위
            reservation.checkInDate.datesUntil(reservation.checkOutDate)
        )
        .concatMap { date -> // 각 날짜에 대해 순차적으로 재고 복구
            inventoryRepository.findByRoomTypeIdAndStockDateForUpdate( // FOR UPDATE 잠금 조회
                reservation.roomTypeId, date
            )
            .switchIfEmpty(Mono.error( // 재고가 없으면 404 에러
                NotFoundException("$date 날짜의 재고를 찾을 수 없습니다. roomTypeId=${reservation.roomTypeId}")
            ))
            .flatMap { inventory -> // 재고 조회 완료 → 수량 복구
                // 방어적 검증: 복구 후 가용 수량이 총 수량을 초과하는지 확인
                // 정상적인 흐름에서는 발생하지 않지만, 데이터 무결성을 보장한다
                if (inventory.availableQuantity + reservation.roomQuantity > inventory.totalQuantity) {
                    Mono.error(BadRequestException(
                        "$date 날짜의 재고 복구 시 총 수량을 초과합니다." +
                            " available=${inventory.availableQuantity}," +
                            " restoring=${reservation.roomQuantity}," +
                            " total=${inventory.totalQuantity}"
                    ))
                } else { // 수량 유효 → 복구 후 저장
                    inventoryRepository.save( // copy()로 가용 수량만 증가한 새 인스턴스 저장
                        inventory.copy(
                            availableQuantity = inventory.availableQuantity + reservation.roomQuantity
                        )
                    )
                }
            }
        }
        .then() // Flux<Inventory> → Mono<Void>: 모든 날짜 처리 완료 시그널만 반환

    // 재고 차감 — 체크인 ~ 체크아웃 기간의 모든 날짜에서 재고를 차감한다
    // 체크아웃 당일은 숙박하지 않으므로 재고 차감에서 제외한다 (datesUntil은 종료일 미포함)
    // concatMap: 날짜 순서대로 순차 처리하여 동시성 문제를 줄인다
    // Phase 4: findByRoomTypeIdAndStockDateForUpdate로 비관적 잠금 적용
    //   SELECT ... FOR UPDATE로 행 잠금을 걸어 동시에 같은 재고를 차감하는 Lost Update를 방지한다
    // 각 날짜에 대해:
    //   1. 해당 날짜의 재고 조회 + 행 잠금 (없으면 404)
    //   2. 가용 수량 확인 (부족하면 400)
    //   3. 가용 수량 차감 후 저장
    private fun decreaseInventory(
        request: ReservationCreateRequest
    ): Mono<Void> =
        Flux.fromStream( // Java Stream을 Flux로 변환 (날짜 범위 생성)
            request.checkInDate.datesUntil(request.checkOutDate) // 체크인 ~ 체크아웃 전날
        )
        .concatMap { date -> // 각 날짜에 대해 순차적으로 재고 차감
            inventoryRepository.findByRoomTypeIdAndStockDateForUpdate( // Phase 4: FOR UPDATE 잠금 조회
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
