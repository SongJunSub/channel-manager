package com.channelmanager.java.repository; // 테스트 패키지

import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.java.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import reactor.test.StepVerifier; // Reactor 스트림을 단계별로 검증하는 도구

import com.channelmanager.java.domain.Property; // 숙소 엔티티

// @SpringBootTest는 실제 DB(Docker PostgreSQL)에 연결하여 통합 테스트를 수행한다
// Flyway가 V7에서 삽입한 샘플 데이터를 기반으로 검증한다
@Import(TestcontainersConfig.class)
@SpringBootTest
class PropertyRepositoryTest {

  @Autowired // Spring이 PropertyRepository 구현체를 자동 주입한다
  private PropertyRepository propertyRepository;

  @Test // findAll - 전체 숙소 목록 조회 테스트
  void 전체_숙소_목록을_조회한다() {
    // StepVerifier.create()로 Flux 스트림 검증을 시작한다
    StepVerifier.create(propertyRepository.findAll())
        .expectNextCount(2) // 샘플 데이터로 2개의 숙소가 삽입되어 있다
        .verifyComplete(); // 스트림이 정상 완료되었는지 검증한다
  }

  @Test // findById - ID로 단건 조회 테스트
  void ID로_숙소를_조회한다() {
    StepVerifier.create(propertyRepository.findById(1L))
        .expectNextMatches(property -> // 반환된 엔티티의 필드를 검증한다
            property.getPropertyCode().equals("SHILLA_SEOUL")
                && property.getPropertyName().equals("서울신라호텔")
                && property.getPropertyAddress().equals("서울특별시 중구 동호로 249"))
        .verifyComplete();
  }

  @Test // findByPropertyNameContaining - 숙소명 검색 테스트
  void 숙소명으로_검색한다() {
    // "서울"이 포함된 숙소를 검색한다
    StepVerifier.create(propertyRepository.findByPropertyNameContaining("서울"))
        .expectNextMatches(
            property -> property.getPropertyCode().equals("SHILLA_SEOUL")) // 서울신라호텔 1건
        .verifyComplete();
  }

  @Test // save - 새 숙소 저장 테스트
  void 새_숙소를_저장한다() {
    Property newProperty = Property.builder() // 빌더 패턴으로 엔티티 생성
        .propertyCode("LOTTE_JEJU")
        .propertyName("롯데호텔 제주")
        .propertyAddress("제주특별자치도 서귀포시 중문관광로 72번길 35")
        .build();

    StepVerifier.create(propertyRepository.save(newProperty))
        .expectNextMatches(saved ->
            saved.getId() != null // 저장 후 자동 생성된 ID가 존재해야 한다
                && saved.getPropertyCode().equals("LOTTE_JEJU")
                && saved.getPropertyName().equals("롯데호텔 제주"))
        .verifyComplete();

    // 저장 후 삭제하여 다른 테스트에 영향을 주지 않도록 한다
    StepVerifier.create(
        propertyRepository.findByPropertyNameContaining("롯데")
            .flatMap(property -> propertyRepository.delete(property))
    ).verifyComplete();
  }
}
