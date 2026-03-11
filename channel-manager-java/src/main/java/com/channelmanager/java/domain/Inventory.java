package com.channelmanager.java.domain; // 도메인 엔티티 패키지

import lombok.AllArgsConstructor; // 모든 필드를 인자로 받는 생성자 자동 생성
import lombok.Builder; // 빌더 패턴 자동 생성
import lombok.Data; // getter, setter, toString, equals, hashCode 자동 생성
import lombok.NoArgsConstructor; // 기본 생성자 자동 생성
import org.springframework.data.annotation.CreatedDate; // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id; // PK 필드 지정 어노테이션
import org.springframework.data.annotation.LastModifiedDate; // 수정 시각 자동 기록 어노테이션
import org.springframework.data.relational.core.mapping.Table; // R2DBC 테이블 매핑 어노테이션
import java.time.LocalDate; // 날짜 타입 (시간 없이 날짜만)
import java.time.LocalDateTime; // 날짜+시간 타입

// 재고 엔티티 - inventories 테이블과 매핑
// 특정 날짜(stockDate)에 특정 객실 타입(roomTypeId)의 재고를 관리한다
// 예: 2026-03-15 Deluxe 전체 10개, 예약 가능 5개
// 하나의 객실 타입(RoomType)은 날짜별로 여러 재고(Inventory) 레코드를 가진다 (1:N 관계)
@Data // getter, setter, toString, equals, hashCode 자동 생성
@Builder // 빌더 패턴 제공
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Table("inventories") // 매핑할 데이터베이스 테이블명 지정
public class Inventory {

    @Id // 이 필드가 PK임을 Spring Data에 알린다
    private Long id; // PK - null이면 INSERT, 값이 있으면 UPDATE

    private Long roomTypeId; // 객실 타입 FK - 어떤 객실 타입의 재고인지 식별

    private LocalDate stockDate; // 재고 날짜 - 이 날짜의 재고 현황을 나타낸다

    private int totalQuantity; // 전체 객실 수 - 해당 날짜에 판매 가능한 총 객실 수

    private int availableQuantity; // 예약 가능 수량 - 아직 예약되지 않은 잔여 객실 수

    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    private LocalDateTime createdAt; // 생성 시각

    @LastModifiedDate // 엔티티 수정 시 현재 시각 자동 기록
    private LocalDateTime updatedAt; // 수정 시각 - 재고가 변경될 때마다 갱신된다
}
