package com.channelmanager.java.repository; // 리포지토리 패키지

import com.channelmanager.java.domain.Property; // 숙소 엔티티
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // 리액티브 CRUD 리포지토리
import reactor.core.publisher.Flux; // 0~N개 결과를 비동기로 반환하는 타입

// 숙소 리포지토리 - ReactiveCrudRepository를 상속하면 기본 CRUD가 자동 제공된다
// findAll(), findById(), save(), deleteById() 등을 별도 구현 없이 사용할 수 있다
public interface PropertyRepository extends ReactiveCrudRepository<Property, Long> {

    // 숙소명으로 검색 - Spring Data가 메서드명을 파싱하여 쿼리를 자동 생성한다
    Flux<Property> findByPropertyNameContaining(String propertyName);
}
