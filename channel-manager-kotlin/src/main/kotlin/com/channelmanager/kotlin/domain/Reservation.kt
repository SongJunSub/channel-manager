package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id // PK 필드 지정 어노테이션
import org.springframework.data.annotation.LastModifiedDate // 수정 시각 자동 기록 어노테이션
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑 어노테이션
import java.math.BigDecimal // 금액 처리용 정밀 숫자 타입
import java.time.LocalDate // 날짜 타입
import java.time.LocalDateTime // 날짜+시간 타입

// 예약 엔티티 - reservations 테이블과 매핑
// 특정 채널(Channel)을 통해 특정 객실 타입(RoomType)을 예약한 정보를 저장한다
// R2DBC는 연관관계 어노테이션이 없으므로 channel_id, room_type_id를 직접 저장한다
@Table("reservations") // 매핑할 데이터베이스 테이블명 지정
data class Reservation(
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    val id: Long? = null, // PK - null이면 INSERT, 값이 있으면 UPDATE
    val channelId: Long, // 채널 FK - 어떤 채널을 통해 예약되었는지 식별
    val roomTypeId: Long, // 객실 타입 FK - 어떤 객실 타입을 예약했는지 식별
    val checkInDate: LocalDate, // 체크인 날짜
    val checkOutDate: LocalDate, // 체크아웃 날짜
    val guestName: String, // 투숙객 이름
    val quantity: Int = 1, // 예약 객실 수 - 기본값 1개
    val status: String, // 예약 상태 - ReservationStatus enum의 name() 값을 저장한다
    val totalPrice: BigDecimal? = null, // 총 금액 - nullable (계산 후 설정)
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    val createdAt: LocalDateTime? = null, // 생성 시각
    @LastModifiedDate // 엔티티 수정 시 현재 시각 자동 기록
    val updatedAt: LocalDateTime? = null // 수정 시각 - 상태 변경(취소 등) 시 갱신된다
)
