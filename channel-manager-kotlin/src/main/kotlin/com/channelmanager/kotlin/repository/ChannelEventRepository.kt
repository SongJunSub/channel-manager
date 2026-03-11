package com.channelmanager.kotlin.repository // 리포지토리 패키지

import com.channelmanager.kotlin.domain.ChannelEvent // 채널 이벤트 엔티티
import com.channelmanager.kotlin.domain.EventType // 이벤트 타입 enum
import org.springframework.data.repository.reactive.ReactiveCrudRepository // 리액티브 CRUD 리포지토리
import reactor.core.publisher.Flux // 0~N개 결과를 비동기로 반환하는 타입

// 채널 이벤트 리포지토리 - Phase 5 SSE 스트리밍에서 핵심적으로 사용된다
interface ChannelEventRepository : ReactiveCrudRepository<ChannelEvent, Long> {

    // 이벤트 타입별 조회
    fun findByEventType(eventType: EventType): Flux<ChannelEvent>

    // 최신 이벤트 조회 (생성 시각 역순)
    fun findAllByOrderByCreatedAtDesc(): Flux<ChannelEvent>
}
