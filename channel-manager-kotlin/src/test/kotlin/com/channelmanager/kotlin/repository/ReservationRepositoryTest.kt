package com.channelmanager.kotlin.repository // 테스트 패키지

import com.channelmanager.kotlin.domain.ReservationStatus // 예약 상태 enum
import org.junit.jupiter.api.Test // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest // 전체 애플리케이션 컨텍스트 로드
import reactor.test.StepVerifier // Reactor 스트림 검증 도구

@SpringBootTest
class ReservationRepositoryTest {

    @Autowired // Spring이 ReservationRepository 구현체를 자동 주입한다
    private lateinit var reservationRepository: ReservationRepository

    @Test // findByChannelId - 채널별 예약 목록 조회 테스트
    fun `채널별 예약 목록을 조회한다`() {
        // channel_id = 1 (DIRECT 자사 홈페이지)의 예약을 조회한다
        // thenConsumeWhile(true): 다른 테스트가 생성한 추가 예약을 무시한다
        StepVerifier.create(reservationRepository.findByChannelId(1L))
            .expectNextMatches { it.guestName == "김민준" } // 자사 홈페이지 예약 1건
            .thenConsumeWhile { true } // 추가 데이터 소비
            .verifyComplete()
    }

    @Test // findByRoomTypeId - 객실 타입별 예약 목록 조회 테스트
    fun `객실 타입별 예약 목록을 조회한다`() {
        // room_type_id = 1 (Superior Double)의 예약을 조회한다
        // thenConsumeWhile(true): 남은 모든 요소를 소비하여 다른 테스트가 생성한 추가 데이터를 무시한다
        StepVerifier.create(reservationRepository.findByRoomTypeId(1L))
            .expectNextMatches { it.roomTypeId == 1L } // 최소 1건 존재 확인
            .thenConsumeWhile { true } // 나머지 추가 데이터 소비
            .verifyComplete()
    }

    @Test // findByStatus - CONFIRMED 상태 예약 조회 테스트
    fun `확정 상태 예약 목록을 조회한다`() {
        // CONFIRMED 상태의 예약이 최소 2건 존재하는지 확인한다 (V7: 김민준, James Wilson)
        // thenConsumeWhile(true): 다른 테스트가 생성한 추가 CONFIRMED 예약을 무시한다
        StepVerifier.create(reservationRepository.findByStatus(ReservationStatus.CONFIRMED))
            .expectNextCount(2) // V7 샘플 데이터 최소 2건
            .thenConsumeWhile { true } // 추가 데이터 소비
            .verifyComplete()
    }

    @Test // findByStatus - CANCELLED 상태 예약 조회 테스트
    fun `취소 상태 예약 목록을 조회한다`() {
        // CANCELLED 상태의 예약이 최소 1건 존재하는지 확인한다 (V7: 田中太郎)
        StepVerifier.create(reservationRepository.findByStatus(ReservationStatus.CANCELLED))
            .expectNextMatches { it.guestName == "田中太郎" } // Agoda 예약 취소 1건
            .thenConsumeWhile { true } // 추가 데이터 소비
            .verifyComplete()
    }
}
