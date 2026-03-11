package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id // PK 필드 지정 어노테이션
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑 어노테이션
import java.time.LocalDateTime // 날짜+시간 타입

// 호텔 엔티티 - hotels 테이블과 매핑
// R2DBC는 JPA의 @Entity 대신 @Table을 사용한다
// data class를 사용하여 equals, hashCode, toString, copy를 자동 생성한다
@Table("hotels") // 매핑할 데이터베이스 테이블명 지정
data class Hotel(
    @Id // 이 필드가 PK(Primary Key)임을 Spring Data에 알린다
    val id: Long? = null, // PK - null이면 새 엔티티(INSERT), 값이 있으면 기존 엔티티(UPDATE)
    val name: String, // 호텔명 (예: "서울 그랜드 호텔")
    val address: String? = null, // 호텔 주소 - nullable (선택 입력)
    @CreatedDate // Spring Data가 엔티티 생성 시 현재 시각을 자동으로 채워준다
    val createdAt: LocalDateTime? = null // 생성 시각 - DB에서 DEFAULT NOW()로도 설정됨
)
