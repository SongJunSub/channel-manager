package com.channelmanager.java.repository; // 리포지토리 패키지

import com.channelmanager.java.domain.Channel; // 채널 엔티티
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // 리액티브 CRUD 리포지토리
import reactor.core.publisher.Flux; // 0~N개 결과를 비동기로 반환하는 타입
import reactor.core.publisher.Mono; // 0~1개 결과를 비동기로 반환하는 타입

// 판매 채널 리포지토리
public interface ChannelRepository extends ReactiveCrudRepository<Channel, Long> {

    // 채널 코드로 단건 조회 (DIRECT, OTA_A 등)
    Mono<Channel> findByChannelCode(String channelCode);

    // 활성 채널 목록 조회
    Flux<Channel> findByIsActive(boolean isActive);
}
