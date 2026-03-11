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
import java.math.BigDecimal; // 금액 처리용 정밀 숫자 타입
import java.time.LocalDate; // 날짜 타입
import java.time.LocalDateTime; // 날짜+시간 타입

// 예약 엔티티 - reservations 테이블과 매핑
// 특정 채널(Channel)을 통해 특정 객실 타입(RoomType)을 예약한 정보를 저장한다
// R2DBC는 연관관계 어노테이션이 없으므로 channelId, roomTypeId를 직접 저장한다
@Schema(description = "예약 정보를 나타내는 엔티티") // 엔티티 레벨 API 문서화
@Data // getter, setter, toString, equals, hashCode 자동 생성
@Builder // 빌더 패턴 제공
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Table("reservations") // 매핑할 데이터베이스 테이블명 지정
public class Reservation {

    @Schema(description = "예약 고유 식별자 (PK)", example = "1") // PK 문서화
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    private Long id; // PK - null이면 INSERT, 값이 있으면 UPDATE

    @Schema(description = "예약 채널 ID (FK)", example = "1") // 채널 외래키 문서화
    private Long channelId; // 채널 FK - 어떤 채널을 통해 예약되었는지 식별

    @Schema(description = "객실 타입 ID (FK)", example = "1") // 객실 타입 외래키 문서화
    private Long roomTypeId; // 객실 타입 FK - 어떤 객실 타입을 예약했는지 식별

    @Schema(description = "체크인 날짜", example = "2026-03-15") // 체크인 문서화
    private LocalDate checkInDate; // 체크인 날짜

    @Schema(description = "체크아웃 날짜", example = "2026-03-17") // 체크아웃 문서화
    private LocalDate checkOutDate; // 체크아웃 날짜

    @Schema(description = "투숙객 이름", example = "홍길동") // 투숙객 문서화
    private String guestName; // 투숙객 이름

    @Schema(description = "예약 객실 수", example = "1", defaultValue = "1") // 객실 수 문서화
    @Builder.Default // @Builder 사용 시 기본값을 유지하기 위한 어노테이션
    private int quantity = 1; // 예약 객실 수 - 기본값 1개

    @Schema(description = "예약 상태 (CONFIRMED, CANCELLED)", example = "CONFIRMED") // 상태 문서화
    private String status; // 예약 상태 - ReservationStatus enum의 name() 값을 저장한다

    @Schema(description = "총 금액", example = "300000", nullable = true) // 금액 문서화
    private BigDecimal totalPrice; // 총 금액 - nullable (계산 후 설정)

    @Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY) // 읽기 전용 필드 문서화
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    private LocalDateTime createdAt; // 생성 시각

    @Schema(description = "마지막 수정 시각", accessMode = Schema.AccessMode.READ_ONLY) // 읽기 전용 필드 문서화
    @LastModifiedDate // 엔티티 수정 시 현재 시각 자동 기록
    private LocalDateTime updatedAt; // 수정 시각 - 상태 변경(취소 등) 시 갱신된다
}
