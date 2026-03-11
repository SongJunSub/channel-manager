package com.channelmanager.java.domain; // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema; // API 문서화용 스키마 어노테이션
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
@Schema(description = "날짜별 객실 재고 정보를 나타내는 엔티티")
@Data // getter, setter, toString, equals, hashCode 자동 생성
@Builder // 빌더 패턴 제공
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Table("inventories")
public class Inventory {

    @Schema(description = "재고 고유 식별자 (PK)", example = "1")
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    private Long id; // null이면 INSERT, 값이 있으면 UPDATE

    @Schema(description = "객실 타입 ID (FK)", example = "1")
    private Long roomTypeId;

    @Schema(description = "재고 날짜", example = "2026-03-15")
    private LocalDate stockDate;

    @Schema(description = "전체 객실 수", example = "10")
    private int totalQuantity;

    @Schema(description = "예약 가능 수량", example = "5")
    private int availableQuantity;

    @Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    private LocalDateTime createdAt;

    @Schema(description = "마지막 수정 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @LastModifiedDate // 엔티티 수정 시 현재 시각 자동 기록
    private LocalDateTime updatedAt;
}
