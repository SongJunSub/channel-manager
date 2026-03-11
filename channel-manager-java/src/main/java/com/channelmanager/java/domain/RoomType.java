package com.channelmanager.java.domain; // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema; // API 문서화용 스키마 어노테이션
import lombok.AllArgsConstructor; // 모든 필드를 인자로 받는 생성자 자동 생성
import lombok.Builder; // 빌더 패턴 자동 생성
import lombok.Data; // getter, setter, toString, equals, hashCode 자동 생성
import lombok.NoArgsConstructor; // 기본 생성자 자동 생성
import org.springframework.data.annotation.CreatedDate; // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id; // PK 필드 지정 어노테이션
import org.springframework.data.relational.core.mapping.Table; // R2DBC 테이블 매핑 어노테이션
import java.math.BigDecimal; // 금액 처리용 정밀 숫자 타입 (부동소수점 오차 방지)
import java.time.LocalDateTime; // 날짜+시간 타입

// 객실 타입 엔티티 - room_types 테이블과 매핑
// 하나의 숙소(Property)는 여러 객실 타입(RoomType)을 가진다 (1:N 관계)
// R2DBC는 @ManyToOne 같은 연관관계 어노테이션이 없으므로 propertyId를 직접 저장한다
@Schema(description = "객실 타입 정보를 나타내는 엔티티") // 엔티티 레벨 API 문서화
@Data // getter, setter, toString, equals, hashCode 자동 생성
@Builder // 빌더 패턴 제공
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Table("room_types") // 매핑할 데이터베이스 테이블명 지정
public class RoomType {

    @Schema(description = "객실 타입 고유 식별자 (PK)", example = "1") // PK 문서화
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    private Long id; // PK - null이면 INSERT, 값이 있으면 UPDATE

    @Schema(description = "소속 숙소 ID (FK)", example = "1") // 외래키 문서화
    private Long propertyId; // 숙소 FK - JPA의 @ManyToOne 대신 ID 값을 직접 저장한다

    @Schema(description = "객실 타입명", example = "Deluxe") // 객실 타입 이름 문서화
    private String name; // 객실 타입명 (예: "Standard", "Deluxe", "Suite")

    @Schema(description = "수용 인원", example = "2", defaultValue = "2") // 기본값 문서화
    @Builder.Default // @Builder 사용 시 기본값을 유지하기 위한 어노테이션
    private int capacity = 2; // 수용 인원 - 기본값 2명

    @Schema(description = "기본 가격 (1박 기준)", example = "150000") // 가격 문서화
    private BigDecimal basePrice; // 기본 가격 - BigDecimal로 금액의 정밀도를 보장한다

    @Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY) // 읽기 전용 필드 문서화
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    private LocalDateTime createdAt; // 생성 시각
}
