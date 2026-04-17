package com.channelmanager.kotlin.controller // 컨트롤러 테스트 패키지

import com.channelmanager.kotlin.dto.InventoryBulkCreateRequest // 일괄 생성 요청 DTO
import com.channelmanager.kotlin.dto.InventoryCreateRequest // 단건 생성 요청 DTO
import com.channelmanager.kotlin.dto.InventoryResponse // 응답 DTO
import com.channelmanager.kotlin.dto.InventoryUpdateRequest // 수정 요청 DTO
import com.channelmanager.kotlin.repository.InventoryRepository // 재고 리포지토리 (테스트 데이터 정리용)
import org.assertj.core.api.Assertions.assertThat // AssertJ 검증 메서드
import org.junit.jupiter.api.AfterAll // 모든 테스트 완료 후 실행
import org.junit.jupiter.api.BeforeAll // 모든 테스트 시작 전 실행
import org.junit.jupiter.api.BeforeEach // 각 테스트 전 실행되는 설정 메서드
import org.junit.jupiter.api.MethodOrderer // 테스트 메서드 실행 순서 제어
import org.junit.jupiter.api.Order // 테스트 실행 순서 지정
import org.junit.jupiter.api.Test // JUnit 5 테스트 어노테이션
import org.junit.jupiter.api.TestInstance // 테스트 인스턴스 생명주기 설정
import org.junit.jupiter.api.TestMethodOrder // 테스트 메서드 정렬 전략 지정
import org.springframework.beans.factory.annotation.Autowired // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.kotlin.config.TestcontainersConfig
import com.channelmanager.kotlin.config.TestSecurityConfig // Phase 21: 테스트 보안 설정
import org.springframework.context.annotation.Import
import org.springframework.boot.test.web.server.LocalServerPort // 랜덤 포트 주입 어노테이션
import org.springframework.http.MediaType // HTTP 미디어 타입 (Content-Type)
import org.springframework.test.web.reactive.server.WebTestClient // WebFlux 테스트용 HTTP 클라이언트
import java.time.LocalDate // 날짜 타입

// 인벤토리 컨트롤러 통합 테스트
// @SpringBootTest(RANDOM_PORT): 실제 서버를 랜덤 포트로 기동하여 통합 테스트를 수행한다
// WebTestClient는 이 서버에 HTTP 요청을 보내고 응답을 검증한다
// @TestMethodOrder(OrderAnnotation): @Order 어노테이션으로 테스트 실행 순서를 제어한다
// @TestInstance(PER_CLASS): 테스트 클래스 인스턴스를 하나만 생성하여 @AfterAll에서 non-static 메서드 사용 가능
@Import(TestcontainersConfig::class, TestSecurityConfig::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryControllerTest {

    @LocalServerPort // Spring이 실제 기동된 서버의 랜덤 포트 번호를 주입한다
    private var port: Int = 0

    @Autowired // 테스트 데이터 정리를 위해 리포지토리를 주입한다
    private lateinit var inventoryRepository: InventoryRepository

    // WebTestClient를 직접 생성한다
    // Spring Boot 4.x에서는 WebTestClient 자동 주입 대신 포트 기반으로 직접 생성하는 방식을 사용한다
    private lateinit var webTestClient: WebTestClient

    // 테스트 중 생성된 재고 ID를 추적하여 테스트 후 정리한다
    // 실제 DB를 사용하므로 테스트 데이터가 쌓이면 다른 테스트에 영향을 준다
    private val createdIds = mutableListOf<Long>()

    // 테스트에서 사용할 날짜 상수 — 샘플 데이터(3월)와 겹치지 않는 먼 미래 날짜를 사용한다
    // 테스트 격리를 위해 한 곳에서 관리하여 날짜 충돌을 방지한다
    companion object {
        val TEST_SINGLE_DATE: LocalDate = LocalDate.of(2026, 11, 1)     // 단건 생성용
        val TEST_BULK_START: LocalDate = LocalDate.of(2026, 11, 10)     // 일괄 생성 시작일
        val TEST_BULK_END: LocalDate = LocalDate.of(2026, 11, 12)       // 일괄 생성 종료일
        val TEST_DELETE_DATE: LocalDate = LocalDate.of(2026, 11, 25)    // 삭제 테스트용
    }

    @BeforeAll // 모든 테스트 시작 전에 이전 실행의 잔여 테스트 데이터를 정리한다
    fun cleanupBefore() {
        // 이전 테스트 실행에서 남은 데이터를 삭제한다 (반복 실행 보장)
        // 테스트에서 사용하는 날짜 범위의 데이터를 모두 삭제한다
        inventoryRepository
            .findByRoomTypeIdAndStockDateBetween(
                1L,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
            )
            .flatMap { inventoryRepository.deleteById(it.id!!) }
            .blockLast() // 모든 삭제 완료 대기 (테스트 정리에서만 block 사용)
        inventoryRepository
            .findByRoomTypeIdAndStockDateBetween(
                2L,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
            )
            .flatMap { inventoryRepository.deleteById(it.id!!) }
            .blockLast()
    }

    @BeforeEach // 각 테스트 실행 전에 WebTestClient를 초기화한다
    fun setUp() {
        webTestClient = WebTestClient.bindToServer() // 실제 서버에 바인딩
            .baseUrl("http://localhost:$port") // 랜덤 포트로 기본 URL 설정
            .build() // WebTestClient 생성
    }

    @AfterAll // 모든 테스트 완료 후 생성된 테스트 데이터를 정리한다
    fun cleanupAfter() {
        // 테스트에서 생성한 재고를 삭제하여 DB를 원래 상태로 복원한다
        createdIds.forEach { id ->
            inventoryRepository.deleteById(id).block() // 테스트 정리에서만 block() 사용 허용
        }
        // 수정 테스트에서 변경한 ID=1 재고의 availableQuantity를 원복한다
        inventoryRepository.findById(1L).flatMap { inventory ->
            inventoryRepository.save(inventory.copy(availableQuantity = 12))
        }.block()
    }

    // ===== 조회 테스트 =====

    @Test // V7 샘플 데이터로 삽입된 재고 단건 조회 테스트
    @Order(1) // 첫 번째로 실행 — 샘플 데이터 존재 여부를 확인한다
    fun `재고 단건 조회 - 존재하는 ID로 조회하면 200 OK를 반환한다`() {
        webTestClient.get() // GET 요청 생성
            .uri("/api/inventories/{id}", 1L) // URL 경로 변수 바인딩 (id=1)
            .exchange() // 요청 실행 (HTTP 호출 발생)
            .expectStatus().isOk // HTTP 200 OK 확인
            .expectBody(InventoryResponse::class.java) // 응답 본문을 InventoryResponse로 역직렬화
            .consumeWith { result -> // consumeWith: nullable 안전하게 응답 본문 검증
                val response = result.responseBody!! // 테스트에서 !! 사용 허용
                assertThat(response.id).isEqualTo(1L) // ID가 1인지 확인
                assertThat(response.totalQuantity).isGreaterThan(0) // 전체 수량이 양수인지 확인
            }
    }

    @Test // 존재하지 않는 ID로 조회 시 404 에러 확인
    @Order(2)
    fun `재고 단건 조회 - 존재하지 않는 ID로 조회하면 404를 반환한다`() {
        webTestClient.get() // GET 요청 생성
            .uri("/api/inventories/{id}", 99999L) // 존재하지 않는 ID
            .exchange() // 요청 실행
            .expectStatus().isNotFound // HTTP 404 Not Found 확인
    }

    @Test // 기간별 재고 목록 조회 테스트 (V7 샘플 데이터 기준)
    @Order(3)
    fun `기간별 재고 조회 - roomTypeId와 날짜 범위로 목록을 조회한다`() {
        webTestClient.get() // GET 요청 생성
            .uri { uriBuilder -> // URI 빌더로 쿼리 파라미터를 설정한다
                uriBuilder
                    .path("/api/inventories") // 기본 경로
                    .queryParam("roomTypeId", 1L) // 객실 타입 ID
                    .queryParam("startDate", "2026-03-15") // 시작 날짜 (ISO 형식)
                    .queryParam("endDate", "2026-03-17") // 종료 날짜
                    .build() // URI 생성
            }
            .exchange() // 요청 실행
            .expectStatus().isOk // 200 OK 확인
            .expectBodyList(InventoryResponse::class.java) // Flux → JSON 배열을 리스트로 역직렬화
            .hasSize(3) // 15일, 16일, 17일 총 3건 확인
    }

    // ===== 생성 테스트 =====

    @Test // 재고 단건 생성 테스트
    @Order(4) // 조회 테스트 이후에 실행 — 새 데이터를 생성한다
    fun `재고 생성 - 유효한 요청이면 201 Created를 반환한다`() {
        val request = InventoryCreateRequest( // 생성 요청 DTO 구성
            roomTypeId = 1L, // 기존 객실 타입 (V7 샘플 데이터)
            stockDate = TEST_SINGLE_DATE, // 기존 데이터와 겹치지 않는 날짜
            totalQuantity = 20 // 전체 20실
        )

        webTestClient.post() // POST 요청 생성
            .uri("/api/inventories") // 생성 엔드포인트
            .contentType(MediaType.APPLICATION_JSON) // Content-Type: application/json
            .bodyValue(request) // 요청 본문에 DTO를 JSON으로 직렬화하여 포함
            .exchange() // 요청 실행
            .expectStatus().isCreated // HTTP 201 Created 확인
            .expectBody(InventoryResponse::class.java) // 응답 본문 검증
            .consumeWith { result ->
                val response = result.responseBody!!
                assertThat(response.roomTypeId).isEqualTo(1L) // 요청한 객실 타입 ID 확인
                assertThat(response.totalQuantity).isEqualTo(20) // 전체 수량 확인
                assertThat(response.availableQuantity).isEqualTo(20) // 초기 가용 수량 = 전체 수량
                assertThat(response.stockDate).isEqualTo(TEST_SINGLE_DATE) // 날짜 확인
                createdIds.add(response.id) // 정리 대상에 추가
            }
    }

    @Test // 존재하지 않는 객실 타입으로 생성 시 404 에러 확인
    @Order(5)
    fun `재고 생성 - 존재하지 않는 객실 타입이면 404를 반환한다`() {
        val request = InventoryCreateRequest(
            roomTypeId = 99999L, // 존재하지 않는 객실 타입
            stockDate = LocalDate.of(2026, 11, 2),
            totalQuantity = 10
        )

        webTestClient.post() // POST 요청
            .uri("/api/inventories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange() // 요청 실행
            .expectStatus().isNotFound // 404 확인 (객실 타입이 없으므로)
    }

    // ===== 일괄 생성 테스트 =====

    @Test // 기간별 일괄 생성 테스트
    @Order(6)
    fun `재고 일괄 생성 - 날짜 범위만큼 재고가 생성된다`() {
        val request = InventoryBulkCreateRequest(
            roomTypeId = 2L, // Deluxe Twin (V7 샘플 데이터)
            startDate = TEST_BULK_START, // 11월 10일부터
            endDate = TEST_BULK_END, // 11월 12일까지 (3일간)
            totalQuantity = 15 // 각 날짜에 15실
        )

        webTestClient.post() // POST 요청
            .uri("/api/inventories/bulk") // 일괄 생성 엔드포인트
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request) // 일괄 생성 요청 본문
            .exchange() // 요청 실행
            .expectStatus().isCreated // 201 Created 확인
            .expectBodyList(InventoryResponse::class.java) // JSON 배열 → 리스트
            .hasSize(3) // 7/1, 7/2, 7/3 총 3건 생성 확인
            .consumeWith<WebTestClient.ListBodySpec<InventoryResponse>> { result ->
                val responses = result.responseBody!!
                responses.forEach { createdIds.add(it.id) } // 정리 대상에 추가
            }
    }

    @Test // 일괄 생성 시 시작일 > 종료일이면 400 에러
    @Order(7)
    fun `재고 일괄 생성 - 시작일이 종료일보다 이후이면 400을 반환한다`() {
        val request = InventoryBulkCreateRequest(
            roomTypeId = 1L,
            startDate = LocalDate.of(2026, 11, 20), // 시작일이 종료일보다 이후
            endDate = LocalDate.of(2026, 11, 18),  // 종료일이 시작일보다 이전 (잘못된 요청)
            totalQuantity = 10
        )

        webTestClient.post() // POST 요청
            .uri("/api/inventories/bulk")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange() // 요청 실행
            .expectStatus().isBadRequest // 400 Bad Request 확인
    }

    // ===== 수정 테스트 =====

    @Test // 재고 수정 테스트 (Partial Update)
    @Order(8)
    fun `재고 수정 - 가용 수량만 변경하면 전체 수량은 유지된다`() {
        val request = InventoryUpdateRequest(
            totalQuantity = null, // 전체 수량은 수정하지 않음
            availableQuantity = 5 // 가용 수량만 5로 변경
        )

        webTestClient.put() // PUT 요청
            .uri("/api/inventories/{id}", 1L) // 재고 ID=1 수정
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request) // 수정 요청 본문
            .exchange() // 요청 실행
            .expectStatus().isOk // 200 OK 확인
            .expectBody(InventoryResponse::class.java)
            .consumeWith { result ->
                val response = result.responseBody!!
                assertThat(response.availableQuantity).isEqualTo(5) // 가용 수량이 5로 변경됨
                assertThat(response.totalQuantity).isEqualTo(15) // 전체 수량은 V7 샘플 값(15) 유지
            }
    }

    @Test // 가용 수량이 전체 수량을 초과하면 400 에러
    @Order(9)
    fun `재고 수정 - 가용 수량이 전체 수량을 초과하면 400을 반환한다`() {
        val request = InventoryUpdateRequest(
            totalQuantity = null,
            availableQuantity = 999 // 전체 수량(15)보다 훨씬 큰 값
        )

        webTestClient.put() // PUT 요청
            .uri("/api/inventories/{id}", 1L)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange() // 요청 실행
            .expectStatus().isBadRequest // 400 Bad Request 확인
    }

    // ===== 삭제 테스트 =====

    @Test // 재고 삭제 테스트
    @Order(10) // 마지막에 실행 — 삭제 후 다른 테스트에 영향을 주지 않도록
    fun `재고 삭제 - 존재하는 ID를 삭제하면 204 No Content를 반환한다`() {
        // 먼저 삭제용 재고를 생성한다 (다른 테스트에 영향을 주지 않기 위해)
        val createRequest = InventoryCreateRequest(
            roomTypeId = 1L,
            stockDate = TEST_DELETE_DATE, // 충돌하지 않는 날짜
            totalQuantity = 5
        )

        // 생성 → 생성된 ID 추출 → 삭제 순으로 테스트
        val created = webTestClient.post() // 먼저 생성
            .uri("/api/inventories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus().isCreated
            .expectBody(InventoryResponse::class.java)
            .returnResult() // 응답 결과를 반환받는다
            .responseBody!! // null이 아님을 보장 (테스트에서 !! 사용 허용)

        webTestClient.delete() // DELETE 요청
            .uri("/api/inventories/{id}", created.id) // 방금 생성한 재고의 ID
            .exchange() // 요청 실행
            .expectStatus().isNoContent // HTTP 204 No Content 확인
        // 삭제된 데이터는 createdIds에 추가하지 않는다 (이미 삭제됨)
    }

    @Test // 존재하지 않는 ID 삭제 시 404 에러
    @Order(11)
    fun `재고 삭제 - 존재하지 않는 ID를 삭제하면 404를 반환한다`() {
        webTestClient.delete() // DELETE 요청
            .uri("/api/inventories/{id}", 99999L) // 존재하지 않는 ID
            .exchange() // 요청 실행
            .expectStatus().isNotFound // 404 확인
    }
}
