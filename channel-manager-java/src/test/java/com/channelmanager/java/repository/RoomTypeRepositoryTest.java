package com.channelmanager.java.repository; // 테스트 패키지

import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import reactor.test.StepVerifier; // Reactor 스트림 검증 도구

@SpringBootTest
class RoomTypeRepositoryTest {

  @Autowired // Spring이 RoomTypeRepository 구현체를 자동 주입한다
  private RoomTypeRepository roomTypeRepository;

  @Test // findByPropertyId - 숙소별 객실 타입 조회 테스트
  void 서울_그랜드_호텔의_객실_타입_3개를_조회한다() {
    // property_id = 1 (서울 그랜드 호텔)의 객실 타입을 조회한다
    StepVerifier.create(roomTypeRepository.findByPropertyId(1L))
        .expectNextCount(3) // Standard, Deluxe, Suite 3개
        .verifyComplete();
  }

  @Test // findByPropertyId - 부산 오션 리조트 객실 타입 조회
  void 부산_오션_리조트의_객실_타입_2개를_조회한다() {
    StepVerifier.create(roomTypeRepository.findByPropertyId(2L))
        .expectNextCount(2) // Standard, Deluxe 2개
        .verifyComplete();
  }

  @Test // findById - 객실 타입 단건 조회
  void ID로_객실_타입을_조회하여_필드를_검증한다() {
    StepVerifier.create(roomTypeRepository.findById(2L))
        .expectNextMatches(roomType ->
            roomType.getRoomTypeCode().equals("DLX") // 객실 타입 코드
                && roomType.getRoomTypeName().equals("Deluxe") // 객실 타입명
                && roomType.getMaxCapacity() == 3 // 최대 수용 인원
                && roomType.getPropertyId() == 1L) // 서울 그랜드 호텔 소속
        .verifyComplete();
  }
}
