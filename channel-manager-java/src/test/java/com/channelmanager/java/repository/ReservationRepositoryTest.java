package com.channelmanager.java.repository; // 테스트 패키지

import com.channelmanager.java.domain.ReservationStatus; // 예약 상태 enum
import org.junit.jupiter.api.Test; // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest; // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.java.config.TestcontainersConfig;
import com.channelmanager.java.config.TestSecurityConfig; // Phase 21: 테스트 보안 설정
import org.springframework.context.annotation.Import;
import reactor.test.StepVerifier; // Reactor 스트림 검증 도구

@Import({TestcontainersConfig.class, TestSecurityConfig.class})
@SpringBootTest
class ReservationRepositoryTest {

  @Autowired // Spring이 ReservationRepository 구현체를 자동 주입한다
  private ReservationRepository reservationRepository;

  @Test // findByChannelId - 채널별 예약 목록 조회 테스트
  void 채널별_예약_목록을_조회한다() {
    // channel_id = 1 (DIRECT 자사 홈페이지)의 예약을 조회한다
    // thenConsumeWhile(true): 다른 테스트가 생성한 추가 예약을 무시한다
    StepVerifier.create(reservationRepository.findByChannelId(1L))
        .expectNextMatches(
            reservation -> reservation.getGuestName().equals("김민준")) // 자사 홈페이지 예약 1건
        .thenConsumeWhile(x -> true) // 추가 데이터 소비
        .verifyComplete();
  }

  @Test // findByRoomTypeId - 객실 타입별 예약 목록 조회 테스트
  void 객실_타입별_예약_목록을_조회한다() {
    // room_type_id = 1 (Superior Double)의 예약을 조회한다
    // thenConsumeWhile(true): 다른 테스트가 생성한 추가 데이터를 무시한다
    StepVerifier.create(reservationRepository.findByRoomTypeId(1L))
        .expectNextMatches(reservation -> reservation.getRoomTypeId() == 1L) // 최소 1건 확인
        .thenConsumeWhile(x -> true) // 추가 데이터 소비
        .verifyComplete();
  }

  @Test // findByStatus - CONFIRMED 상태 예약 조회 테스트
  void 확정_상태_예약_목록을_조회한다() {
    // V7 샘플: CONFIRMED 상태 최소 2건 (김민준, James Wilson)
    // thenConsumeWhile(true): 다른 테스트가 생성한 추가 CONFIRMED 예약을 무시한다
    StepVerifier.create(reservationRepository.findByStatus(ReservationStatus.CONFIRMED))
        .expectNextCount(2) // V7 샘플 데이터 최소 2건
        .thenConsumeWhile(x -> true) // 추가 데이터 소비
        .verifyComplete();
  }

  @Test // findByStatus - CANCELLED 상태 예약 조회 테스트
  void 취소_상태_예약_목록을_조회한다() {
    // V7 샘플: CANCELLED 상태 최소 1건 (田中太郎)
    // thenConsumeWhile(true): 다른 테스트가 생성한 추가 데이터를 무시한다
    StepVerifier.create(reservationRepository.findByStatus(ReservationStatus.CANCELLED))
        .expectNextMatches(
            reservation -> reservation.getGuestName().equals("田中太郎"))
        .thenConsumeWhile(x -> true) // 추가 데이터 소비
        .verifyComplete();
  }
}
