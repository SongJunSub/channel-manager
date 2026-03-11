package com.channelmanager.java.repository; // 리포지토리 패키지

import com.channelmanager.java.domain.RoomType; // 객실 타입 엔티티
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // 리액티브 CRUD 리포지토리
import reactor.core.publisher.Flux; // 0~N개 결과를 비동기로 반환하는 타입

// 객실 타입 리포지토리
public interface RoomTypeRepository extends ReactiveCrudRepository<RoomType, Long> {

    // 특정 숙소의 객실 타입 목록 조회
    Flux<RoomType> findByPropertyId(Long propertyId);
}
