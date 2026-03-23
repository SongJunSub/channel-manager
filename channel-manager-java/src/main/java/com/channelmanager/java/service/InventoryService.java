package com.channelmanager.java.service; // 서비스 패키지 - 비즈니스 로직 계층

import com.channelmanager.java.domain.ChannelEvent; // Phase 4: 채널 이벤트 엔티티
import com.channelmanager.java.domain.EventType; // Phase 4: 이벤트 타입 enum
import com.channelmanager.java.domain.Inventory; // 재고 엔티티
import com.channelmanager.java.dto.InventoryBulkCreateRequest; // 기간별 일괄 생성 요청 DTO
import com.channelmanager.java.dto.InventoryCreateRequest; // 단건 생성 요청 DTO
import com.channelmanager.java.dto.InventoryResponse; // 응답 DTO
import com.channelmanager.java.dto.InventoryUpdateRequest; // 수정 요청 DTO
import com.channelmanager.java.exception.BadRequestException; // 400 Bad Request 예외
import com.channelmanager.java.exception.NotFoundException; // 404 Not Found 예외
import com.channelmanager.java.repository.ChannelEventRepository; // Phase 4: 이벤트 리포지토리
import com.channelmanager.java.repository.InventoryRepository; // 재고 리포지토리
import com.channelmanager.java.repository.RoomTypeRepository; // 객실 타입 리포지토리
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 (Lombok)
import org.springframework.stereotype.Service; // 서비스 계층 어노테이션
import org.springframework.transaction.annotation.Transactional; // 트랜잭션 어노테이션
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.time.LocalDate; // 날짜 타입

// 인벤토리 서비스 - 재고 관리 비즈니스 로직을 담당한다
// @Service로 Spring 빈으로 등록한다
// @RequiredArgsConstructor: Lombok이 final 필드에 대한 생성자를 자동 생성한다
// Kotlin에서는 primary constructor에 val로 선언하지만,
// Java에서는 @RequiredArgsConstructor + private final 필드로 동일한 효과를 얻는다
// Phase 4: EventPublisher, ChannelEventRepository를 추가하여 재고 변경 이벤트를 발행한다
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;       // 재고 DB 접근
    private final RoomTypeRepository roomTypeRepository;         // 객실 타입 DB 접근 (존재 여부 검증용)
    private final ChannelEventRepository channelEventRepository; // Phase 4: 이벤트 DB 저장
    private final EventPublisher eventPublisher;                 // Phase 4: 이벤트 발행 서비스

    // ===== 조회 =====

    // 재고 단건 조회
    // findById로 DB 조회 → 없으면 404 에러 → 있으면 DTO로 변환
    // switchIfEmpty: Mono가 비어있을 때(값이 없을 때) 대체 Mono를 실행한다
    // map: 엔티티를 DTO로 동기 변환한다 (DB 호출 없으므로 flatMap이 아닌 map 사용)
    // Kotlin에서는 람다에서 it으로 암시적 파라미터를 사용하지만,
    // Java에서는 메서드 레퍼런스(InventoryResponse::from)를 사용한다
    public Mono<InventoryResponse> getInventory(Long id) {
        return inventoryRepository.findById(id) // Mono<Inventory> — DB에서 PK로 조회
            .switchIfEmpty(Mono.error(new NotFoundException("재고를 찾을 수 없습니다. id=" + id))) // 없으면 404
            .map(InventoryResponse::from); // Inventory → InventoryResponse 변환 (메서드 레퍼런스)
    }

    // 기간별 재고 목록 조회
    // 특정 객실 타입의 시작일 ~ 종료일 범위에 해당하는 재고를 조회한다
    // Flux를 반환하므로 클라이언트에게 JSON 배열로 직렬화된다
    // map: 각 Inventory 요소를 InventoryResponse로 변환한다
    public Flux<InventoryResponse> getInventories(
            Long roomTypeId, LocalDate startDate, LocalDate endDate) {
        return inventoryRepository // 리포지토리의 날짜 범위 조회 메서드 호출
            .findByRoomTypeIdAndStockDateBetween(roomTypeId, startDate, endDate) // Flux<Inventory>
            .map(InventoryResponse::from); // 각 요소를 DTO로 변환
    }

    // ===== 생성 =====

    // 재고 단건 생성
    // 1단계: 요청의 roomTypeId로 객실 타입이 존재하는지 검증한다
    // 2단계: 존재하면 Inventory 엔티티를 생성하여 DB에 저장한다
    // 3단계: 저장된 엔티티를 DTO로 변환하여 반환한다
    // then + Mono.defer: 이전 Mono(검증)의 결과를 무시하고 새로운 Mono(생성)를 실행한다
    // Kotlin에서는 named argument로 Inventory를 생성하지만,
    // Java에서는 Builder 패턴을 사용한다 (@Builder 어노테이션)
    public Mono<InventoryResponse> createInventory(InventoryCreateRequest request) {
        return validateRoomTypeExists(request.roomTypeId()) // 객실 타입 존재 여부 검증 (record 접근자)
            .then(Mono.defer(() -> // 검증 통과 후 재고 생성 (defer로 지연 실행)
                inventoryRepository.save( // DB에 새 Inventory 저장
                    Inventory.builder() // Lombok @Builder로 객체 생성
                        .roomTypeId(request.roomTypeId())           // 객실 타입 ID
                        .stockDate(request.stockDate())             // 재고 날짜
                        .totalQuantity(request.totalQuantity())     // 전체 수량
                        .availableQuantity(request.totalQuantity()) // 최초 생성 시 가용=전체
                        .build() // 빌더로 Inventory 객체 생성
                )
            ))
            .map(InventoryResponse::from); // 저장된 엔티티를 DTO로 변환
    }

    // 재고 기간별 일괄 생성
    // startDate ~ endDate 범위의 모든 날짜에 대해 동일한 수량의 재고를 생성한다
    // Flux.fromStream: Java Stream을 Flux로 변환한다 (날짜 범위 생성)
    // datesUntil: LocalDate의 메서드로, 시작일부터 종료일 전날까지의 Stream<LocalDate>를 생성한다
    // plusDays(1): endDate를 포함하기 위해 하루를 더한다 (datesUntil은 종료일 미포함)
    // concatMap: 각 날짜에 대해 순차적으로 재고를 생성한다 (순서 보장)
    // Kotlin에서는 Flux.defer { } (중괄호 람다)를 사용하지만,
    // Java에서는 Flux.defer(() -> { }) (화살표 람다)를 사용한다
    @Transactional // 일괄 생성 중 하나라도 실패하면 전체 롤백
    public Flux<InventoryResponse> createInventoryBulk(
            InventoryBulkCreateRequest request) {
        return validateBulkCreateRequest(request) // 요청 유효성 검증 (날짜 순서 등)
            .then(validateRoomTypeExists(request.roomTypeId())) // 객실 타입 존재 여부 검증
            .thenMany(Flux.defer(() -> // Flux.defer로 감싸서 검증 통과 후에만 날짜 범위를 생성한다
                // defer가 없으면 Flux.fromStream이 즉시(eager) 평가되어
                // startDate > endDate일 때 datesUntil이 IllegalArgumentException을 던진다
                Flux.fromStream( // Java Stream을 Flux로 변환
                    request.startDate() // 시작 날짜부터
                        .datesUntil(request.endDate().plusDays(1)) // 종료 날짜까지 (포함)
                )
                .concatMap(date -> // 각 날짜에 대해 순차적으로 재고 생성
                    inventoryRepository.save( // DB에 새 Inventory 저장
                        Inventory.builder()
                            .roomTypeId(request.roomTypeId())           // 객실 타입 ID
                            .stockDate(date)                           // 해당 날짜
                            .totalQuantity(request.totalQuantity())    // 전체 수량
                            .availableQuantity(request.totalQuantity()) // 가용 = 전체
                            .build()
                    )
                )
            ))
            .map(InventoryResponse::from); // 각 저장 결과를 DTO로 변환
    }

    // ===== 수정 =====

    // 재고 수정 (Partial Update)
    // 1단계: 기존 재고를 조회한다 (없으면 404)
    // 2단계: 요청에서 null이 아닌 필드만 업데이트한다 (Partial Update 패턴)
    // 3단계: 비즈니스 규칙을 검증한다 (가용 수량 범위 등)
    // 4단계: 수정된 엔티티를 DB에 저장한다
    // Kotlin에서는 data class의 copy() 함수로 일부 필드만 변경하지만,
    // Java에서는 setter로 직접 값을 변경한다 (Lombok @Data가 setter를 생성)
    @Transactional // 조회와 저장을 하나의 트랜잭션으로 묶는다
    public Mono<InventoryResponse> updateInventory(
            Long id, InventoryUpdateRequest request) {
        return inventoryRepository.findById(id) // 기존 재고 조회
            .switchIfEmpty(Mono.error(new NotFoundException("재고를 찾을 수 없습니다. id=" + id))) // 없으면 404
            .flatMap(inventory -> { // 조회된 재고에 대해 수정 작업 수행
                // Phase 4: 변경 전 가용 수량을 보관한다 (이벤트 페이로드에 사용)
                // Kotlin에서는 copy()가 원본을 보존하지만,
                // Java에서는 setter가 원본을 직접 변경하므로 변경 전에 값을 보관해야 한다
                int beforeQuantity = inventory.getAvailableQuantity(); // 변경 전 가용 수량 보관
                // Partial Update: null이 아닌 필드만 업데이트한다
                // Kotlin에서는 copy()와 엘비스 연산자(?:)를 사용하지만,
                // Java에서는 null 체크 후 setter로 값을 변경한다
                if (request.totalQuantity() != null) { // totalQuantity가 null이 아니면 수정
                    inventory.setTotalQuantity(request.totalQuantity()); // setter로 값 변경
                }
                if (request.availableQuantity() != null) { // availableQuantity가 null이 아니면 수정
                    inventory.setAvailableQuantity(request.availableQuantity()); // setter로 값 변경
                }
                // 비즈니스 규칙 검증: 가용 수량은 0 이상, 전체 수량 이하여야 한다
                // Phase 4: 검증 통과 후 저장 → 이벤트 발행 단계가 추가된다
                return validateQuantity(inventory.getAvailableQuantity(), inventory.getTotalQuantity())
                    .then(inventoryRepository.save(inventory)) // 검증 통과 후 저장
                    .flatMap(savedInventory -> // Phase 4: 재고 변경 이벤트 발행
                        // INVENTORY_UPDATED 이벤트를 DB에 저장하고 Sinks에 발행한다
                        // eventPayload에 변경 전/후 가용 수량을 JSON으로 기록한다
                        // Kotlin에서는 copy()로 변경 전 값을 유지하지만,
                        // Java에서는 변경 전 값을 별도 변수(beforeQuantity)에 보관한다
                        channelEventRepository.save(
                            ChannelEvent.builder()
                                .eventType(EventType.INVENTORY_UPDATED)     // 재고 변경 이벤트
                                .roomTypeId(savedInventory.getRoomTypeId()) // 객실 타입 ID
                                .eventPayload(
                                    "{\"before\":" + beforeQuantity + "," +
                                    "\"after\":" + savedInventory.getAvailableQuantity() + "," +
                                    "\"stockDate\":\"" + savedInventory.getStockDate() + "\"}")
                                .build()
                        )
                        .doOnNext(event -> eventPublisher.publish(event)) // Sinks에 발행 (부수 효과)
                        .thenReturn(savedInventory) // 이벤트 저장 결과 무시, 재고 반환
                    );
            })
            .map(InventoryResponse::from); // 저장 결과를 DTO로 변환
    }

    // ===== 삭제 =====

    // 재고 삭제
    // 1단계: 해당 ID의 재고가 존재하는지 확인한다
    // 2단계: 존재하면 삭제한다
    // flatMap: 조회 후 삭제라는 비동기 작업을 체이닝한다
    // Kotlin과 Java 모두 동일한 Reactor 연산자를 사용한다 (언어 차이 없음)
    public Mono<Void> deleteInventory(Long id) {
        return inventoryRepository.findById(id) // 존재 여부 확인
            .switchIfEmpty(Mono.error(new NotFoundException("재고를 찾을 수 없습니다. id=" + id))) // 없으면 404
            .flatMap(inventory -> inventoryRepository.deleteById(id)); // 존재하면 삭제
    }

    // ===== 검증 메서드 =====

    // 객실 타입 존재 여부 검증
    // findById 결과가 비어있으면 404 에러를 발행한다
    // then(): RoomType 값 자체는 필요 없고, 존재 여부만 확인하므로 결과를 무시한다
    private Mono<Void> validateRoomTypeExists(Long roomTypeId) {
        return roomTypeRepository.findById(roomTypeId) // 객실 타입 조회
            .switchIfEmpty(Mono.error(new NotFoundException("객실 타입을 찾을 수 없습니다. id=" + roomTypeId)))
            .then(); // 결과 무시 (존재 확인만)
    }

    // 수량 유효성 검증
    // 가용 수량이 0 미만이거나 전체 수량을 초과하면 400 Bad Request 에러를 발행한다
    // Mono.defer: 동기적인 검증 로직을 Reactive 체인에 포함시키기 위해 사용한다
    // Kotlin에서는 when 표현식을 사용하지만, Java에서는 if-else를 사용한다
    private Mono<Void> validateQuantity(int available, int total) {
        return Mono.defer(() -> { // 구독 시점에 검증 실행
            if (available < 0) { // 가용 수량이 음수
                return Mono.error(new BadRequestException(
                    "가용 수량은 0 이상이어야 합니다. available=" + available));
            }
            if (available > total) { // 가용 수량이 전체 수량 초과
                return Mono.error(new BadRequestException(
                    "가용 수량이 전체 수량을 초과할 수 없습니다. available=" + available + ", total=" + total));
            }
            return Mono.empty(); // 검증 통과 (빈 Mono로 완료)
        });
    }

    // 일괄 생성 요청 유효성 검증
    // 시작 날짜가 종료 날짜보다 이후이면 400 Bad Request 에러를 발행한다
    // Kotlin에서는 request.startDate.isAfter(request.endDate)를 사용하지만,
    // Java에서는 request.startDate().isAfter(request.endDate())로 record 접근자를 호출한다
    private Mono<Void> validateBulkCreateRequest(InventoryBulkCreateRequest request) {
        return Mono.defer(() -> {
            if (request.startDate().isAfter(request.endDate())) { // 시작일 > 종료일이면 에러
                return Mono.error(new BadRequestException(
                    "시작 날짜가 종료 날짜보다 이후일 수 없습니다. startDate=" +
                        request.startDate() + ", endDate=" + request.endDate()));
            }
            return Mono.empty(); // 검증 통과
        });
    }
}
