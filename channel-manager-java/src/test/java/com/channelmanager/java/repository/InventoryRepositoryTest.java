package com.channelmanager.java.repository; // 테스트 패키지

import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.java.config.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import reactor.test.StepVerifier; // Reactor 스트림 검증 도구

import java.time.LocalDate; // 날짜 타입

@Import(TestcontainersConfig.class)
@SpringBootTest
class InventoryRepositoryTest {

  @Autowired // Spring이 InventoryRepository 구현체를 자동 주입한다
  private InventoryRepository inventoryRepository;

  @Test // findByRoomTypeIdAndStockDateBetween - 날짜 범위 재고 조회 테스트
  void 날짜_범위로_재고를_조회한다() {
    // room_type_id = 1 (Standard)의 3월 15일 ~ 17일 재고를 조회한다
    LocalDate startDate = LocalDate.of(2026, 3, 15);
    LocalDate endDate = LocalDate.of(2026, 3, 17);

    StepVerifier.create(
        inventoryRepository.findByRoomTypeIdAndStockDateBetween(1L, startDate, endDate)
    )
        .expectNextCount(3) // 15일, 16일, 17일 총 3건
        .verifyComplete();
  }

  @Test // findByRoomTypeIdAndStockDate - 특정 날짜 재고 단건 조회 테스트
  void 특정_날짜의_재고를_조회한다() {
    LocalDate targetDate = LocalDate.of(2026, 3, 15);

    StepVerifier.create(
        inventoryRepository.findByRoomTypeIdAndStockDate(1L, targetDate)
    )
        .expectNextMatches(inventory ->
            inventory.getTotalQuantity() == 15 // 전체 15개
                && inventory.getAvailableQuantity() == 12) // 예약 가능 12개
        .verifyComplete();
  }

  @Test // findByRoomTypeIdAndStockDate - 데이터 없는 날짜 조회 시 빈 결과
  void 데이터가_없는_날짜를_조회하면_빈_결과를_반환한다() {
    LocalDate noDataDate = LocalDate.of(2026, 1, 1); // 데이터가 없는 날짜

    StepVerifier.create(
        inventoryRepository.findByRoomTypeIdAndStockDate(1L, noDataDate)
    )
        .verifyComplete(); // Mono.empty()이므로 onNext 없이 바로 완료
  }
}
