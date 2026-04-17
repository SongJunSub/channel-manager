package com.channelmanager.kotlin.repository // 테스트 패키지

import com.channelmanager.kotlin.domain.EventType // 이벤트 타입 enum
import org.junit.jupiter.api.Test // JUnit 5 테스트 어노테이션
import org.springframework.beans.factory.annotation.Autowired // 의존성 주입 어노테이션
import org.springframework.boot.test.context.SpringBootTest // 전체 애플리케이션 컨텍스트 로드
import com.channelmanager.kotlin.config.TestcontainersConfig
import com.channelmanager.kotlin.config.TestSecurityConfig // Phase 21: 테스트 보안 설정
import org.springframework.context.annotation.Import
import reactor.test.StepVerifier // Reactor 스트림 검증 도구

@Import(TestcontainersConfig::class, TestSecurityConfig::class)
@SpringBootTest
class ChannelEventRepositoryTest {

    @Autowired // Spring이 ChannelEventRepository 구현체를 자동 주입한다
    private lateinit var channelEventRepository: ChannelEventRepository

    @Test // findByEventType - 이벤트 타입별 조회 테스트
    fun `예약 생성 이벤트를 조회한다`() {
        // V7 샘플: RESERVATION_CREATED 이벤트 최소 2건 (홍길동, 김철수)
        // thenConsumeWhile(true): 다른 테스트가 생성한 추가 이벤트를 무시한다
        StepVerifier.create(
            channelEventRepository.findByEventType(EventType.RESERVATION_CREATED)
        )
            .expectNextCount(2) // V7 샘플 데이터 최소 2건
            .thenConsumeWhile { true } // 추가 데이터 소비
            .verifyComplete()
    }

    @Test // findByEventType - 재고 변경 이벤트 조회 테스트
    fun `재고 변경 이벤트를 조회한다`() {
        StepVerifier.create(
            channelEventRepository.findByEventType(EventType.INVENTORY_UPDATED)
        )
            .expectNextMatches { event ->
                event.channelId == null && // 재고 변경은 특정 채널과 무관
                    event.eventPayload != null // JSON 페이로드 존재
            }
            .thenConsumeWhile { true } // 추가 데이터 소비
            .verifyComplete()
    }

    @Test // findAllByOrderByCreatedAtDesc - 최신순 이벤트 조회 테스트
    fun `이벤트를 최신순으로 조회한다`() {
        // V7 샘플: 전체 이벤트 최소 5건
        // thenConsumeWhile(true): 다른 테스트가 생성한 추가 이벤트를 무시한다
        StepVerifier.create(channelEventRepository.findAllByOrderByCreatedAtDesc())
            .expectNextCount(5) // V7 샘플 데이터 최소 5건
            .thenConsumeWhile { true } // 추가 데이터 소비
            .verifyComplete()
    }
}
