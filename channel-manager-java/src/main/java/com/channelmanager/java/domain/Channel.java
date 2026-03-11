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
@Schema(description = "판매 채널 정보를 나타내는 엔티티") // 엔티티 레벨 API 문서화
@Data // getter, setter, toString, equals, hashCode 자동 생성
@Builder // 빌더 패턴 제공
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@Table("channels") // 매핑할 데이터베이스 테이블명 지정
public class Channel {

    @Schema(description = "채널 고유 식별자 (PK)", example = "1") // PK 문서화
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    private Long id; // PK - null이면 INSERT, 값이 있으면 UPDATE

    @Schema(description = "채널 고유 코드", example = "OTA_A") // 코드 문서화
    private String code; // 채널 코드 - 고유 식별 코드 (예: "DIRECT", "OTA_A", "OTA_B")

    @Schema(description = "채널 표시 이름", example = "온라인 여행사 A") // 이름 문서화
    private String name; // 채널명 - 표시용 이름 (예: "자사 직접 예약", "여행사 A")

    @Schema(description = "채널 활성 상태", example = "true", defaultValue = "true") // 활성 상태 문서화
    @Builder.Default // @Builder 사용 시 기본값을 유지하기 위한 어노테이션
    private boolean isActive = true; // 활성 상태 - false이면 해당 채널에서 예약을 받지 않는다

    @Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY) // 읽기 전용 필드 문서화
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    private LocalDateTime createdAt; // 생성 시각
}
