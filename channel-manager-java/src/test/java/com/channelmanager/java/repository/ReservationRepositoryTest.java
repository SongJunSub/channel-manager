package com.channelmanager.java.repository; // 테스트 패키지

import com.channelmanager.java.domain.ReservationStatus; // 예약 상태 enum
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import reactor.test.StepVerifier; // Reactor 스트림 검증 도구

@SpringBootTest
class ReservationRepositoryTest {

  @Autowired // Spring이 ReservationRepository 구현체를 자동 주입한다
  private ReservationRepository reservationRepository;

  @Test // findByChannelId - 채널별 예약 목록 조회 테스트
  void 채널별_예약_목록을_조회한다() {
    // channel_id = 1 (DIRECT)의 예약을 조회한다
    StepVerifier.create(reservationRepository.findByChannelId(1L))
        .expectNextMatches(
            reservation -> reservation.getGuestName().equals("홍길동")) // DIRECT 채널 예약 1건
        .verifyComplete();
  }

  @Test // findByRoomTypeId - 객실 타입별 예약 목록 조회 테스트
  void 객실_타입별_예약_목록을_조회한다() {
    // room_type_id = 1 (Standard)의 예약을 조회한다
    StepVerifier.create(reservationRepository.findByRoomTypeId(1L))
        .expectNextCount(2) // 홍길동(CONFIRMED), 이영희(CANCELLED)
        .verifyComplete();
  }

  @Test // findByStatus - CONFIRMED 상태 예약 조회 테스트
  void 확정_상태_예약_목록을_조회한다() {
    StepVerifier.create(reservationRepository.findByStatus(ReservationStatus.CONFIRMED))
        .expectNextCount(2) // 홍길동, 김철수
        .verifyComplete();
  }

  @Test // findByStatus - CANCELLED 상태 예약 조회 테스트
  void 취소_상태_예약_목록을_조회한다() {
    StepVerifier.create(reservationRepository.findByStatus(ReservationStatus.CANCELLED))
        .expectNextMatches(
            reservation -> reservation.getGuestName().equals("이영희")) // 취소 예약 1건
        .verifyComplete();
  }
}
