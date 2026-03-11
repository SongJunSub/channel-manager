package com.channelmanager.java.repository; // 테스트 패키지

import com.channelmanager.java.domain.EventType; // 이벤트 타입 enum
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import reactor.test.StepVerifier; // Reactor 스트림 검증 도구

@SpringBootTest
class ChannelEventRepositoryTest {

  @Autowired // Spring이 ChannelEventRepository 구현체를 자동 주입한다
  private ChannelEventRepository channelEventRepository;

  @Test // findByEventType - 이벤트 타입별 조회 테스트
  void 예약_생성_이벤트를_조회한다() {
    StepVerifier.create(
        channelEventRepository.findByEventType(EventType.RESERVATION_CREATED)
    )
        .expectNextCount(2) // 홍길동, 김철수 예약 생성 이벤트 2건
        .verifyComplete();
  }

  @Test // findByEventType - 재고 변경 이벤트 조회 테스트
  void 재고_변경_이벤트를_조회한다() {
    StepVerifier.create(
        channelEventRepository.findByEventType(EventType.INVENTORY_UPDATED)
    )
        .expectNextMatches(event ->
            event.getChannelId() == null // 재고 변경은 특정 채널과 무관
                && event.getEventPayload() != null) // JSON 페이로드 존재
        .verifyComplete();
  }

  @Test // findAllByOrderByCreatedAtDesc - 최신순 이벤트 조회 테스트
  void 이벤트를_최신순으로_조회한다() {
    StepVerifier.create(channelEventRepository.findAllByOrderByCreatedAtDesc())
        .expectNextCount(5) // 전체 이벤트 5건
        .verifyComplete();
  }
}
