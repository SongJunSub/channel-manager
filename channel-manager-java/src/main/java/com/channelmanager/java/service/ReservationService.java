package com.channelmanager.java.service; // 서비스 패키지 - 비즈니스 로직 계층

import com.channelmanager.java.domain.ChannelEvent; // 채널 이벤트 엔티티
import com.channelmanager.java.domain.EventType; // 이벤트 타입 enum
import com.channelmanager.java.domain.Reservation; // 예약 엔티티
import com.channelmanager.java.domain.ReservationStatus; // 예약 상태 enum
import com.channelmanager.java.dto.ReservationCreateRequest; // 예약 생성 요청 DTO
import com.channelmanager.java.dto.ReservationResponse; // 예약 응답 DTO
import com.channelmanager.java.exception.BadRequestException; // 400 Bad Request 예외
import com.channelmanager.java.exception.NotFoundException; // 404 Not Found 예외
import com.channelmanager.java.repository.ChannelEventRepository; // 이벤트 리포지토리
import com.channelmanager.java.repository.ChannelRepository; // 채널 리포지토리
import com.channelmanager.java.repository.InventoryRepository; // 재고 리포지토리
import com.channelmanager.java.repository.ReservationRepository; // 예약 리포지토리
import com.channelmanager.java.repository.RoomTypeRepository; // 객실 타입 리포지토리
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 (Lombok)
import org.springframework.stereotype.Service; // 서비스 계층 어노테이션
import org.springframework.transaction.annotation.Transactional; // 트랜잭션 어노테이션
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.math.BigDecimal; // 금액 처리용 정밀 숫자 타입

// 예약 서비스 - 예약 생성 + 재고 차감 + 이벤트 기록을 담당한다
// 여러 테이블(Reservation, Inventory, ChannelEvent)에 걸친 트랜잭션이 필요하다
// @Service로 Spring 빈으로 등록한다
// @RequiredArgsConstructor: Lombok이 final 필드에 대한 생성자를 자동 생성한다
// Kotlin에서는 primary constructor에 val로 선언하지만,
// Java에서는 @RequiredArgsConstructor + private final 필드로 동일한 효과를 얻는다
// Phase 4: EventPublisher를 주입받아 이벤트 저장 후 Sinks에도 발행한다
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;     // 예약 DB 접근
    private final ChannelRepository channelRepository;             // 채널 DB 접근
    private final RoomTypeRepository roomTypeRepository;           // 객실 타입 DB 접근
    private final InventoryRepository inventoryRepository;         // 재고 DB 접근
    private final ChannelEventRepository channelEventRepository;   // 이벤트 DB 접근
    private final EventPublisher eventPublisher;                   // Phase 4: 이벤트 발행 서비스

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
    public Mono<ReservationResponse> createReservation(
            ReservationCreateRequest request) {
        return validateReservationRequest(request) // 1단계: 요청 유효성 검증
            .then(
                channelRepository.findByChannelCode(request.channelCode()) // 2단계: 채널 조회
                    .switchIfEmpty(Mono.error(new NotFoundException(
                        "채널을 찾을 수 없습니다. code=" + request.channelCode()
                    )))
            )
            .filter(channel -> channel.isActive()) // 채널이 활성 상태인지 확인
            .switchIfEmpty(Mono.error(new BadRequestException(
                "비활성 채널입니다. code=" + request.channelCode()
            )))
            .flatMap(channel -> // 채널 조회 완료 → 객실 타입 조회
                roomTypeRepository.findById(request.roomTypeId()) // 3단계: 객실 타입 조회
                    .switchIfEmpty(Mono.error(new NotFoundException(
                        "객실 타입을 찾을 수 없습니다. id=" + request.roomTypeId()
                    )))
                    .flatMap(roomType -> // 객실 타입 조회 완료 → 재고 차감
                        decreaseInventory(request) // 4단계: 체크인~체크아웃 기간 재고 차감
                            .then(Mono.defer(() -> { // 재고 차감 완료 → 예약 저장
                                // 5단계: 총 금액 계산 = basePrice × 숙박일수 × 객실수
                                long nights = request.checkInDate()
                                    .datesUntil(request.checkOutDate()) // 체크인~체크아웃 전날 Stream
                                    .count(); // 숙박일수
                                BigDecimal totalPrice = roomType.getBasePrice() // 1박 기본 가격
                                    .multiply(BigDecimal.valueOf(nights)) // × 숙박일수
                                    .multiply(BigDecimal.valueOf(request.roomQuantity())); // × 객실수

                                return reservationRepository.save( // 예약 저장
                                    Reservation.builder()
                                        .channelId(channel.getId())                   // 채널 ID
                                        .roomTypeId(request.roomTypeId())             // 객실 타입 ID
                                        .checkInDate(request.checkInDate())           // 체크인 날짜
                                        .checkOutDate(request.checkOutDate())         // 체크아웃 날짜
                                        .guestName(request.guestName())               // 투숙객 이름
                                        .roomQuantity(request.roomQuantity())         // 객실 수
                                        .status(ReservationStatus.CONFIRMED)          // 예약 확정
                                        .totalPrice(totalPrice)                       // 총 금액
                                        .build()
                                );
                            }))
                            .flatMap(reservation -> // 예약 저장 완료 → 이벤트 기록
                                // 6단계: ChannelEvent 기록 (RESERVATION_CREATED)
                                channelEventRepository.save(
                                    ChannelEvent.builder()
                                        .eventType(EventType.RESERVATION_CREATED)     // 이벤트 타입
                                        .channelId(channel.getId())                   // 채널 ID
                                        .reservationId(reservation.getId())           // 예약 ID
                                        .roomTypeId(request.roomTypeId())             // 객실 타입 ID
                                        .eventPayload(
                                            "{\"guestName\":\"" + request.guestName() + "\"," +
                                            "\"roomQuantity\":" + request.roomQuantity() + "," +
                                            "\"checkIn\":\"" + request.checkInDate() + "\"," +
                                            "\"checkOut\":\"" + request.checkOutDate() + "\"}"
                                        )
                                        .build()
                                )
                                .doOnNext(savedEvent -> // Phase 4: DB 저장 후 Sinks에 발행
                                    // doOnNext: 스트림 흐름을 변경하지 않는 부수 효과(side-effect)
                                    // DB 저장이 성공한 후에만 Sinks에 발행한다
                                    // 발행 실패가 전체 트랜잭션을 롤백시키지 않도록 fire-and-forget
                                    // Kotlin에서는 { eventPublisher.publish(it) }이지만,
                                    // Java에서는 savedEvent -> eventPublisher.publish(savedEvent)
                                    eventPublisher.publish(savedEvent) // Sinks에 이벤트 발행
                                )
                                .thenReturn(reservation) // 이벤트 저장 결과는 무시하고 예약 반환
                            )
                            .map(reservation -> // 7단계: DTO 변환
                                ReservationResponse.from(
                                    reservation, channel.getChannelCode()
                                )
                            )
                    )
            );
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
    // Kotlin에서는 fun cancelReservation(reservationId: Long): Mono<ReservationResponse>이지만,
    // Java에서는 public Mono<ReservationResponse> cancelReservation(long reservationId)이다
    @Transactional
    public Mono<ReservationResponse> cancelReservation(long reservationId) {
        return reservationRepository.findById(reservationId) // 1단계: 예약 조회
            .switchIfEmpty(Mono.error(new NotFoundException( // 예약이 없으면 404 에러
                "예약을 찾을 수 없습니다. id=" + reservationId
            )))
            .flatMap(reservation -> { // 예약 조회 완료 → 상태 확인
                // 2단계: 상태 확인 — CONFIRMED만 취소 가능
                // 이미 CANCELLED인 예약을 다시 취소하면 400 에러
                // Kotlin에서는 reservation.status != ReservationStatus.CONFIRMED이지만,
                // Java에서는 getter 메서드를 사용한다
                if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
                    return Mono.<ReservationResponse>error(new BadRequestException(
                        "이미 취소된 예약입니다. id=" + reservationId
                    ));
                }

                // 3단계: 채널 조회 — channelCode를 응답 DTO에 포함하기 위해 조회한다
                return channelRepository.findById(reservation.getChannelId())
                    .switchIfEmpty(Mono.error(new NotFoundException(
                        "채널을 찾을 수 없습니다. id=" + reservation.getChannelId()
                    )))
                    .flatMap(channel -> // 채널 조회 완료 → 재고 복구
                        // 4단계: 재고 복구 — decreaseInventory와 대칭적인 increaseInventory
                        increaseInventory(reservation)
                            .then(Mono.defer(() -> { // 재고 복구 완료 → 예약 상태 변경
                                // 5단계: 예약 상태를 CANCELLED로 변경
                                // Kotlin에서는 copy()로 불변 객체를 변경하지만,
                                // Java에서는 setter로 직접 필드를 변경한다 (Lombok @Data)
                                reservation.setStatus(ReservationStatus.CANCELLED);
                                return reservationRepository.save(reservation);
                            }))
                            .flatMap(cancelledReservation -> // 상태 변경 완료 → 이벤트 기록
                                // 6단계: ChannelEvent 기록 (RESERVATION_CANCELLED)
                                // eventPayload에 취소된 예약 정보를 JSON 형태로 기록한다
                                channelEventRepository.save(
                                    ChannelEvent.builder()
                                        .eventType(EventType.RESERVATION_CANCELLED) // 취소 이벤트 타입
                                        .channelId(reservation.getChannelId())       // 채널 ID
                                        .reservationId(reservation.getId())          // 예약 ID
                                        .roomTypeId(reservation.getRoomTypeId())     // 객실 타입 ID
                                        .eventPayload(
                                            "{\"guestName\":\"" + reservation.getGuestName() + "\"," +
                                            "\"roomQuantity\":" + reservation.getRoomQuantity() + "," +
                                            "\"checkIn\":\"" + reservation.getCheckInDate() + "\"," +
                                            "\"checkOut\":\"" + reservation.getCheckOutDate() + "\"}"
                                        )
                                        .build()
                                )
                                .doOnNext(savedEvent -> // DB 저장 후 Sinks에 발행
                                    // SSE를 통해 대시보드에 실시간으로 취소 이벤트를 전달한다
                                    eventPublisher.publish(savedEvent) // Sinks에 이벤트 발행
                                )
                                .thenReturn(cancelledReservation) // 이벤트 저장 결과는 무시하고 예약 반환
                            )
                            .map(cancelledReservation -> // 7단계: DTO 변환
                                ReservationResponse.from(
                                    cancelledReservation, channel.getChannelCode()
                                )
                            )
                    );
            });
    }

    // 재고 복구 — 체크인 ~ 체크아웃 기간의 모든 날짜에서 재고를 증가시킨다
    // decreaseInventory()와 대칭적인 보상 로직이다
    // concatMap: 날짜 순서대로 순차 처리 (동시성 문제 방지)
    // FOR UPDATE 비관적 잠금: 동시에 같은 재고를 복구/차감하는 요청의 충돌 방지
    // Kotlin에서는 private fun increaseInventory(reservation: Reservation): Mono<Void>이지만,
    // Java에서는 private Mono<Void> increaseInventory(Reservation reservation)이다
    private Mono<Void> increaseInventory(Reservation reservation) {
        return Flux.fromStream( // 체크인 ~ 체크아웃 전날까지의 날짜 범위
                reservation.getCheckInDate().datesUntil(reservation.getCheckOutDate())
            )
            .concatMap(date -> // 각 날짜에 대해 순차적으로 재고 복구
                inventoryRepository.findByRoomTypeIdAndStockDateForUpdate( // FOR UPDATE 잠금 조회
                    reservation.getRoomTypeId(), date
                )
                .switchIfEmpty(Mono.error(new NotFoundException( // 재고가 없으면 404 에러
                    date + " 날짜의 재고를 찾을 수 없습니다. roomTypeId=" + reservation.getRoomTypeId()
                )))
                .flatMap(inventory -> { // 재고 조회 완료 → 수량 복구
                    // 방어적 검증: 복구 후 가용 수량이 총 수량을 초과하는지 확인
                    // 정상적인 흐름에서는 발생하지 않지만, 데이터 무결성을 보장한다
                    if (inventory.getAvailableQuantity() + reservation.getRoomQuantity()
                            > inventory.getTotalQuantity()) {
                        return Mono.error(new BadRequestException(
                            date + " 날짜의 재고 복구 시 총 수량을 초과합니다." +
                                " available=" + inventory.getAvailableQuantity() +
                                ", restoring=" + reservation.getRoomQuantity() +
                                ", total=" + inventory.getTotalQuantity()
                        ));
                    }
                    // Kotlin에서는 copy()로 불변 객체를 변경하지만,
                    // Java에서는 setter로 직접 필드를 변경한다 (Lombok @Data)
                    inventory.setAvailableQuantity(
                        inventory.getAvailableQuantity() + reservation.getRoomQuantity()
                    );
                    return inventoryRepository.save(inventory); // 복구된 재고 저장
                })
            )
            .then(); // Flux<Inventory> → Mono<Void>: 모든 날짜 처리 완료 시그널만 반환
    }

    // 재고 차감 — 체크인 ~ 체크아웃 기간의 모든 날짜에서 재고를 차감한다
    // 체크아웃 당일은 숙박하지 않으므로 재고 차감에서 제외한다 (datesUntil은 종료일 미포함)
    // concatMap: 날짜 순서대로 순차 처리하여 동시성 문제를 줄인다
    // Phase 4: findByRoomTypeIdAndStockDateForUpdate로 비관적 잠금 적용
    //   SELECT ... FOR UPDATE로 행 잠금을 걸어 동시에 같은 재고를 차감하는 Lost Update를 방지한다
    // Kotlin에서는 Flux.fromStream(request.checkInDate.datesUntil(...))로 호출하지만,
    // Java에서는 request.checkInDate().datesUntil(...)로 record 접근자를 사용한다
    private Mono<Void> decreaseInventory(
            ReservationCreateRequest request) {
        return Flux.fromStream( // Java Stream을 Flux로 변환 (날짜 범위 생성)
                request.checkInDate().datesUntil(request.checkOutDate()) // 체크인 ~ 체크아웃 전날
            )
            .concatMap(date -> // 각 날짜에 대해 순차적으로 재고 차감
                inventoryRepository.findByRoomTypeIdAndStockDateForUpdate( // Phase 4: FOR UPDATE 잠금 조회
                    request.roomTypeId(), date
                )
                .switchIfEmpty(Mono.error(new NotFoundException( // 재고가 없으면 404 에러
                    date + " 날짜의 재고를 찾을 수 없습니다. roomTypeId=" + request.roomTypeId()
                )))
                .flatMap(inventory -> { // 재고 조회 완료 → 수량 확인 + 차감
                    if (inventory.getAvailableQuantity() < request.roomQuantity()) { // 가용 수량 부족
                        return Mono.error(new BadRequestException(
                            date + " 날짜의 재고가 부족합니다." +
                                " available=" + inventory.getAvailableQuantity() +
                                ", requested=" + request.roomQuantity()
                        ));
                    }
                    // Kotlin에서는 copy()로 불변 객체의 일부 필드를 변경하지만,
                    // Java에서는 setter로 직접 값을 변경한다 (Lombok @Data가 setter 생성)
                    inventory.setAvailableQuantity(
                        inventory.getAvailableQuantity() - request.roomQuantity()
                    );
                    return inventoryRepository.save(inventory); // 차감된 재고 저장
                })
            )
            .then(); // Flux<Inventory> → Mono<Void>: 모든 날짜 처리 완료 시그널만 반환
    }

    // 예약 요청 유효성 검증
    // 체크인 날짜가 체크아웃 날짜 이후이면 400 Bad Request
    // 객실 수가 1 미만이면 400 Bad Request
    // Kotlin에서는 when 표현식을 사용하지만, Java에서는 if-else를 사용한다
    private Mono<Void> validateReservationRequest(
            ReservationCreateRequest request) {
        return Mono.defer(() -> { // 구독 시점에 검증 실행 (지연 평가)
            if (!request.checkInDate().isBefore(request.checkOutDate())) { // 체크인 >= 체크아웃
                return Mono.error(new BadRequestException(
                    "체크인 날짜는 체크아웃 날짜보다 이전이어야 합니다." +
                        " checkIn=" + request.checkInDate() +
                        ", checkOut=" + request.checkOutDate()
                ));
            }
            if (request.roomQuantity() < 1) { // 객실 수 0 이하
                return Mono.error(new BadRequestException(
                    "객실 수량은 1 이상이어야 합니다. roomQuantity=" + request.roomQuantity()
                ));
            }
            return Mono.empty(); // 검증 통과
        });
    }
}
