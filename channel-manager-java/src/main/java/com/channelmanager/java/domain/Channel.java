package com.channelmanager.java.domain; // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema; // API 문서화용 스키마 어노테이션
import lombok.AllArgsConstructor; // 모든 필드를 인자로 받는 생성자 자동 생성
import lombok.Builder; // 빌더 패턴 자동 생성
import lombok.Data; // getter, setter, toString, equals, hashCode 자동 생성
import lombok.NoArgsConstructor; // 기본 생성자 자동 생성
import org.springframework.data.annotation.CreatedDate; // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id; // PK 필드 지정 어노테이션
import org.springframework.data.relational.core.mapping.Table; // R2DBC 테이블 매핑 어노테이션
import java.time.LocalDateTime; // 날짜+시간 타입

// 판매 채널 엔티티 - channels 테이블과 매핑
// 호텔이 객실을 판매하는 경로를 나타낸다
// 예: DIRECT(자사 홈페이지), OTA_A(온라인 여행사 A), OTA_B(온라인 여행사 B)
@Schema(description = "판매 채널 정보를 나타내는 엔티티")
@Data // getter, setter, toString, equals, hashCode 자동 생성
@Builder // 빌더 패턴 제공
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Table("channels")
public class Channel {

    @Schema(description = "채널 고유 식별자 (PK)", example = "1")
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    private Long id; // null이면 INSERT, 값이 있으면 UPDATE

    @Schema(description = "채널 고유 코드", example = "OTA_A")
    private String channelCode;

    @Schema(description = "채널 표시 이름", example = "온라인 여행사 A")
    private String channelName;

    @Schema(description = "채널 활성 상태", example = "true", defaultValue = "true")
    @Builder.Default // @Builder 사용 시 기본값을 유지하기 위한 어노테이션
    private boolean isActive = true;

    @Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    private LocalDateTime createdAt;
}
