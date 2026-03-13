package com.channelmanager.kotlin.service // 서비스 패키지 - 비즈니스 로직 계층

import com.channelmanager.kotlin.domain.Inventory // 재고 엔티티
import com.channelmanager.kotlin.dto.InventoryBulkCreateRequest // 기간별 일괄 생성 요청 DTO
import com.channelmanager.kotlin.dto.InventoryCreateRequest // 단건 생성 요청 DTO
import com.channelmanager.kotlin.dto.InventoryResponse // 응답 DTO
import com.channelmanager.kotlin.dto.InventoryUpdateRequest // 수정 요청 DTO
import com.channelmanager.kotlin.exception.BadRequestException // 400 Bad Request 예외
import com.channelmanager.kotlin.exception.NotFoundException // 404 Not Found 예외
import com.channelmanager.kotlin.repository.InventoryRepository // 재고 리포지토리
import com.channelmanager.kotlin.repository.RoomTypeRepository // 객실 타입 리포지토리
import org.springframework.stereotype.Service // 서비스 계층 어노테이션
import org.springframework.transaction.annotation.Transactional // 트랜잭션 어노테이션
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.time.LocalDate // 날짜 타입

// 인벤토리 서비스 - 재고 관리 비즈니스 로직을 담당한다
// @Service로 Spring 빈으로 등록하고, 생성자 주입으로 의존성을 받는다
// Kotlin에서는 primary constructor에 val로 선언하면 자동으로 생성자 주입이 된다
@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository, // 재고 DB 접근
    private val roomTypeRepository: RoomTypeRepository    // 객실 타입 DB 접근 (존재 여부 검증용)
) {

    // ===== 조회 =====

    // 재고 단건 조회
    // findById로 DB 조회 → 없으면 404 에러 → 있으면 DTO로 변환
    // switchIfEmpty: Mono가 비어있을 때(값이 없을 때) 대체 Mono를 실행한다
    // map: 엔티티를 DTO로 동기 변환한다 (DB 호출 없으므로 flatMap이 아닌 map 사용)
    fun getInventory(id: Long): Mono<InventoryResponse> =
        inventoryRepository.findById(id) // Mono<Inventory> — DB에서 PK로 조회
            .switchIfEmpty(Mono.error(NotFoundException("재고를 찾을 수 없습니다. id=$id"))) // 없으면 404
            .map { InventoryResponse.from(it) } // Inventory → InventoryResponse 변환

    // 기간별 재고 목록 조회
    // 특정 객실 타입의 시작일 ~ 종료일 범위에 해당하는 재고를 조회한다
    // Flux를 반환하므로 클라이언트에게 JSON 배열로 직렬화된다
    // map: 각 Inventory 요소를 InventoryResponse로 변환한다 (Flux의 각 요소에 적용)
    fun getInventories(
        roomTypeId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flux<InventoryResponse> =
        inventoryRepository // 리포지토리의 날짜 범위 조회 메서드 호출
            .findByRoomTypeIdAndStockDateBetween(roomTypeId, startDate, endDate) // Flux<Inventory>
            .map { InventoryResponse.from(it) } // 각 요소를 DTO로 변환

    // ===== 생성 =====

    // 재고 단건 생성
    // 1단계: 요청의 roomTypeId로 객실 타입이 존재하는지 검증한다
    // 2단계: 존재하면 Inventory 엔티티를 생성하여 DB에 저장한다
    // 3단계: 저장된 엔티티를 DTO로 변환하여 반환한다
    // flatMap: 검증 후 저장이라는 비동기 작업을 체이닝한다 (DB 호출이 포함되므로 flatMap)
    // then + Mono.defer: 이전 Mono(검증)의 결과를 무시하고 새로운 Mono(생성)를 실행한다
    fun createInventory(request: InventoryCreateRequest): Mono<InventoryResponse> =
        validateRoomTypeExists(request.roomTypeId) // 객실 타입 존재 여부 검증
            .then(Mono.defer { // 검증 통과 후 재고 생성 (defer로 지연 실행)
                inventoryRepository.save( // DB에 새 Inventory 저장
                    Inventory( // 엔티티 생성 (id=null이면 INSERT)
                        roomTypeId = request.roomTypeId,             // 객실 타입 ID
                        stockDate = request.stockDate,               // 재고 날짜
                        totalQuantity = request.totalQuantity,       // 전체 수량
                        availableQuantity = request.totalQuantity    // 최초 생성 시 가용=전체
                    )
                )
            })
            .map { InventoryResponse.from(it) } // 저장된 엔티티를 DTO로 변환

    // 재고 기간별 일괄 생성
    // startDate ~ endDate 범위의 모든 날짜에 대해 동일한 수량의 재고를 생성한다
    // Flux.fromStream: Java Stream을 Flux로 변환한다 (날짜 범위 생성)
    // datesUntil: LocalDate의 메서드로, 시작일부터 종료일 전날까지의 Stream<LocalDate>를 생성한다
    // plusDays(1): endDate를 포함하기 위해 하루를 더한다 (datesUntil은 종료일 미포함)
    // concatMap: 각 날짜에 대해 순차적으로 재고를 생성한다 (순서 보장)
    // flatMap 대신 concatMap을 사용하는 이유: 날짜 순서대로 생성하여 결과의 일관성을 보장한다
    @Transactional // 일괄 생성 중 하나라도 실패하면 전체 롤백
    fun createInventoryBulk(
        request: InventoryBulkCreateRequest
    ): Flux<InventoryResponse> =
        validateBulkCreateRequest(request) // 요청 유효성 검증 (날짜 순서 등)
            .then(validateRoomTypeExists(request.roomTypeId)) // 객실 타입 존재 여부 검증
            .thenMany(Flux.defer { // Flux.defer로 감싸서 검증 통과 후에만 날짜 범위를 생성한다
                // defer가 없으면 Flux.fromStream이 즉시(eager) 평가되어
                // startDate > endDate일 때 datesUntil이 IllegalArgumentException을 던진다
                Flux.fromStream( // Java Stream을 Flux로 변환
                    request.startDate // 시작 날짜부터
                        .datesUntil(request.endDate.plusDays(1)) // 종료 날짜까지 (포함)
                )
                .concatMap { date -> // 각 날짜에 대해 순차적으로 재고 생성
                    inventoryRepository.save( // DB에 새 Inventory 저장
                        Inventory(
                            roomTypeId = request.roomTypeId,          // 객실 타입 ID
                            stockDate = date,                        // 해당 날짜
                            totalQuantity = request.totalQuantity,   // 전체 수량
                            availableQuantity = request.totalQuantity // 가용 = 전체
                        )
                    )
                }
            })
            .map { InventoryResponse.from(it) } // 각 저장 결과를 DTO로 변환

    // ===== 수정 =====

    // 재고 수정 (Partial Update)
    // 1단계: 기존 재고를 조회한다 (없으면 404)
    // 2단계: 요청에서 null이 아닌 필드만 업데이트한다 (Partial Update 패턴)
    // 3단계: 비즈니스 규칙을 검증한다 (가용 수량 범위 등)
    // 4단계: 수정된 엔티티를 DB에 저장한다
    // flatMap: 조회 → 수정 → 저장이라는 비동기 작업 체인을 연결한다
    // copy: Kotlin data class의 copy 함수로 일부 필드만 변경한 새 인스턴스를 생성한다
    @Transactional // 조회와 저장을 하나의 트랜잭션으로 묶는다
    fun updateInventory(
        id: Long,
        request: InventoryUpdateRequest
    ): Mono<InventoryResponse> =
        inventoryRepository.findById(id) // 기존 재고 조회
            .switchIfEmpty(Mono.error(NotFoundException("재고를 찾을 수 없습니다. id=$id"))) // 없으면 404
            .flatMap { inventory -> // 조회된 재고에 대해 수정 작업 수행
                // Partial Update: null이 아닌 필드만 업데이트한다
                // Kotlin data class의 copy()를 사용하여 불변 객체의 일부 필드만 변경한다
                // ?: (엘비스 연산자)로 null이면 기존 값을 유지한다
                val updated = inventory.copy(
                    totalQuantity = request.totalQuantity
                        ?: inventory.totalQuantity,       // null이면 기존 값 유지
                    availableQuantity = request.availableQuantity
                        ?: inventory.availableQuantity    // null이면 기존 값 유지
                )
                // 비즈니스 규칙 검증: 가용 수량은 0 이상, 전체 수량 이하여야 한다
                validateQuantity(updated.availableQuantity, updated.totalQuantity)
                    .then(inventoryRepository.save(updated)) // 검증 통과 후 저장
            }
            .map { InventoryResponse.from(it) } // 저장 결과를 DTO로 변환

    // ===== 삭제 =====

    // 재고 삭제
    // 1단계: 해당 ID의 재고가 존재하는지 확인한다
    // 2단계: 존재하면 삭제한다
    // flatMap: 조회 후 삭제라는 비동기 작업을 체이닝한다
    // then(): deleteById의 결과(Mono<Void>)를 Mono<Void>로 변환한다
    fun deleteInventory(id: Long): Mono<Void> =
        inventoryRepository.findById(id) // 존재 여부 확인
            .switchIfEmpty(Mono.error(NotFoundException("재고를 찾을 수 없습니다. id=$id"))) // 없으면 404
            .flatMap { inventoryRepository.deleteById(id) } // 존재하면 삭제

    // ===== 검증 메서드 =====

    // 객실 타입 존재 여부 검증
    // findById 결과가 비어있으면 404 에러를 발행한다
    // then(): RoomType 값 자체는 필요 없고, 존재 여부만 확인하므로 결과를 무시한다
    private fun validateRoomTypeExists(roomTypeId: Long): Mono<Void> =
        roomTypeRepository.findById(roomTypeId) // 객실 타입 조회
            .switchIfEmpty(Mono.error(NotFoundException("객실 타입을 찾을 수 없습니다. id=$roomTypeId")))
            .then() // 결과 무시 (존재 확인만)

    // 수량 유효성 검증
    // 가용 수량이 0 미만이거나 전체 수량을 초과하면 400 Bad Request 에러를 발행한다
    // Mono.defer: 동기적인 검증 로직을 Reactive 체인에 포함시키기 위해 사용한다
    private fun validateQuantity(available: Int, total: Int): Mono<Void> =
        Mono.defer { // 구독 시점에 검증 실행
            when { // Kotlin when 표현식으로 조건 분기
                available < 0 -> // 가용 수량이 음수
                    Mono.error(BadRequestException("가용 수량은 0 이상이어야 합니다. available=$available"))
                available > total -> // 가용 수량이 전체 수량 초과
                    Mono.error(
                        BadRequestException(
                            "가용 수량이 전체 수량을 초과할 수 없습니다." +
                                " available=$available, total=$total"
                        )
                    )
                else -> Mono.empty() // 검증 통과 (빈 Mono로 완료)
            }
        }

    // 일괄 생성 요청 유효성 검증
    // 시작 날짜가 종료 날짜보다 이후이면 400 Bad Request 에러를 발행한다
    private fun validateBulkCreateRequest(request: InventoryBulkCreateRequest): Mono<Void> =
        Mono.defer {
            if (request.startDate.isAfter(request.endDate)) { // 시작일 > 종료일이면 에러
                Mono.error(
                    BadRequestException(
                        "시작 날짜가 종료 날짜보다 이후일 수 없습니다." +
                            " startDate=${request.startDate}, endDate=${request.endDate}"
                    )
                )
            } else {
                Mono.empty() // 검증 통과
            }
        }
}
