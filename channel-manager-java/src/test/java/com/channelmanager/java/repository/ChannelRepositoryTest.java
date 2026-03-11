package com.channelmanager.java.repository; // 테스트 패키지

import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import reactor.test.StepVerifier; // Reactor 스트림 검증 도구

@SpringBootTest
class ChannelRepositoryTest {

  @Autowired // Spring이 ChannelRepository 구현체를 자동 주입한다
  private ChannelRepository channelRepository;

  @Test // findByChannelCode - 채널 코드로 단건 조회 테스트
  void 채널_코드로_Booking_com을_조회한다() {
    StepVerifier.create(channelRepository.findByChannelCode("BOOKING"))
        .expectNextMatches(channel ->
            channel.getChannelName().equals("Booking.com") // 채널명 검증
                && channel.isActive()) // 활성 상태 검증
        .verifyComplete();
  }

  @Test // findByIsActive - 활성 채널 목록 조회 테스트
  void 활성_채널_목록을_조회한다() {
    // is_active = true인 채널만 조회한다 (DIRECT, OTA_A, OTA_B)
    StepVerifier.create(channelRepository.findByIsActive(true))
        .expectNextCount(3) // 활성 채널 3개
        .verifyComplete();
  }

  @Test // findByIsActive - 비활성 채널 목록 조회 테스트
  void 비활성_채널_목록을_조회한다() {
    // is_active = false인 채널만 조회한다 (Trip.com)
    StepVerifier.create(channelRepository.findByIsActive(false))
        .expectNextMatches(channel ->
            channel.getChannelCode().equals("TRIP")
                && channel.getChannelName().equals("Trip.com"))
        .verifyComplete();
  }
}
