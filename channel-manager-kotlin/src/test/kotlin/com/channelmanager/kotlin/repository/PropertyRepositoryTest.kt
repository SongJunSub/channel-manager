package com.channelmanager.kotlin.repository // 테스트 패키지

import com.channelmanager.kotlin.domain.Property // 숙소 엔티티
import org.junit.jupiter.api.Test // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest // 전체 애플리케이션 컨텍스트 로드
import reactor.test.StepVerifier // Reactor 스트림을 단계별로 검증하는 도구

// @SpringBootTest는 실제 DB(Docker PostgreSQL)에 연결하여 통합 테스트를 수행한다
// Flyway가 V7에서 삽입한 샘플 데이터를 기반으로 검증한다
@SpringBootTest
class PropertyRepositoryTest {

    @Autowired // Spring이 PropertyRepository 구현체를 자동 주입한다
    private lateinit var propertyRepository: PropertyRepository

    @Test // findAll - 전체 숙소 목록 조회 테스트
    fun `전체 숙소 목록을 조회한다`() {
        // StepVerifier.create()로 Flux 스트림 검증을 시작한다
        StepVerifier.create(propertyRepository.findAll())
            .expectNextCount(2) // 샘플 데이터로 2개의 숙소가 삽입되어 있다
            .verifyComplete() // 스트림이 정상 완료되었는지 검증한다
    }

    @Test // findById - ID로 단건 조회 테스트
    fun `ID로 숙소를 조회한다`() {
        StepVerifier.create(propertyRepository.findById(1L))
            .expectNextMatches { property -> // 반환된 엔티티의 필드를 검증한다
                property.propertyCode == "SEOUL_GRAND" &&
                    property.propertyName == "서울 그랜드 호텔" &&
                    property.propertyAddress == "서울특별시 중구 을지로 30"
            }
            .verifyComplete()
    }

    @Test // findByPropertyNameContaining - 숙소명 검색 테스트
    fun `숙소명으로 검색한다`() {
        // "서울"이 포함된 숙소를 검색한다
        StepVerifier.create(propertyRepository.findByPropertyNameContaining("서울"))
            .expectNextMatches { it.propertyCode == "SEOUL_GRAND" } // 서울 그랜드 호텔 1건
            .verifyComplete()
    }

    @Test // save - 새 숙소 저장 테스트
    fun `새 숙소를 저장한다`() {
        val newProperty = Property( // id = null이면 INSERT
            propertyCode = "JEJU_BEACH",
            propertyName = "제주 비치 호텔",
            propertyAddress = "제주특별자치도 서귀포시 중문로 100"
        )

        StepVerifier.create(propertyRepository.save(newProperty))
            .expectNextMatches { saved ->
                saved.id != null && // 저장 후 자동 생성된 ID가 존재해야 한다
                    saved.propertyCode == "JEJU_BEACH" &&
                    saved.propertyName == "제주 비치 호텔"
            }
            .verifyComplete()

        // 저장 후 삭제하여 다른 테스트에 영향을 주지 않도록 한다
        StepVerifier.create(
            propertyRepository.findByPropertyNameContaining("제주")
                .flatMap { propertyRepository.delete(it) }
        ).verifyComplete()
    }
}
