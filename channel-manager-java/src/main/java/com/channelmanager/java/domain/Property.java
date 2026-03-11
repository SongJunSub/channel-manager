package com.channelmanager.java.domain; // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema; // API 문서화용 스키마 어노테이션
import lombok.AllArgsConstructor; // 모든 필드를 인자로 받는 생성자 자동 생성
import lombok.Builder; // 빌더 패턴 자동 생성 - Property.builder().name("...").build()
import lombok.Data; // getter, setter, toString, equals, hashCode 자동 생성
import lombok.NoArgsConstructor; // 기본 생성자 자동 생성 (Spring Data가 내부적으로 사용)
import org.springframework.data.annotation.CreatedDate; // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id; // PK 필드 지정 어노테이션
import org.springframework.data.relational.core.mapping.Table; // R2DBC 테이블 매핑 어노테이션
import java.time.LocalDateTime; // 날짜+시간 타입

// 숙소 엔티티 - properties 테이블과 매핑
// R2DBC는 JPA의 @Entity 대신 @Table을 사용한다
// Lombok 어노테이션으로 Java의 보일러플레이트 코드(getter, setter 등)를 제거한다
@Schema(description = "숙소 정보를 나타내는 엔티티")
@Data // getter, setter, toString, equals, hashCode 자동 생성
@Builder // 빌더 패턴 제공 - Property.builder().name("서울 그랜드 호텔").build()
@NoArgsConstructor // 기본 생성자 - Spring Data가 리플렉션으로 객체를 생성할 때 필요
@AllArgsConstructor // 전체 필드 생성자 - @Builder가 내부적으로 사용
@Table("properties")
public class Property {

    @Schema(description = "숙소 고유 식별자 (PK)", example = "1")
    @Id // 이 필드가 PK(Primary Key)임을 Spring Data에 알린다
    private Long id; // null이면 새 엔티티(INSERT), 값이 있으면 기존 엔티티(UPDATE)

    @Schema(description = "숙소명", example = "서울 그랜드 호텔")
    private String name;

    @Schema(description = "숙소 주소", example = "서울특별시 중구 을지로 30", nullable = true)
    private String address;

    @Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @CreatedDate // Spring Data가 엔티티 생성 시 현재 시각을 자동으로 채워준다
    private LocalDateTime createdAt;
}
