package com.channelmanager.kotlin.controller // 컨트롤러 패키지

import com.channelmanager.kotlin.domain.Channel // 채널 엔티티
import com.channelmanager.kotlin.domain.Reservation // 예약 엔티티
import com.channelmanager.kotlin.dto.ReservationCreateRequest // 예약 생성 요청 DTO
import com.channelmanager.kotlin.dto.SummaryStatistics // 요약 통계 DTO
import com.channelmanager.kotlin.repository.ChannelRepository // 채널 리포지토리
import com.channelmanager.kotlin.repository.ReservationRepository // 예약 리포지토리
import com.channelmanager.kotlin.service.ReservationService // 예약 서비스
import com.channelmanager.kotlin.service.StatisticsService // 통계 서비스
import org.springframework.graphql.data.method.annotation.Argument // GraphQL 인자 바인딩
import org.springframework.graphql.data.method.annotation.MutationMapping // Mutation 리졸버
import org.springframework.graphql.data.method.annotation.QueryMapping // Query 리졸버
import org.springframework.graphql.data.method.annotation.SchemaMapping // 연관 필드 리졸버
import org.springframework.stereotype.Controller // Spring MVC Controller (GraphQL은 @RestController가 아닌 @Controller 사용)
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.time.LocalDate // 날짜 타입

// GraphQL 리졸버 컨트롤러
// Phase 26: REST API와 별도로 GraphQL 엔드포인트를 제공한다
// 기존 REST API를 대체하지 않고 **보완**하는 방식으로 동작한다
// 클라이언트가 필요한 필드만 선택적으로 조회할 수 있다
// @Controller: Spring for GraphQL은 @RestController가 아닌 @Controller를 사용한다
//   — GraphQL 응답은 Spring for GraphQL 엔진이 직접 처리하므로 @ResponseBody가 불필요하다
@Controller
class ReservationGraphqlController(
    private val reservationRepository: ReservationRepository, // 예약 DB 접근
    private val channelRepository: ChannelRepository,         // 채널 DB 접근
    private val reservationService: ReservationService,       // 예약 비즈니스 로직
    private val statisticsService: StatisticsService          // 통계 비즈니스 로직
) {

    // ===== Query 리졸버 (읽기) =====

    // 예약 단건 조회 — schema.graphqls의 Query.reservation(id: ID!)에 매핑
    // @QueryMapping: type Query { reservation(id: ID!): Reservation }
    // @Argument: GraphQL 인자를 메서드 파라미터에 바인딩한다
    // 반환: Mono<Reservation> — WebFlux Reactive 타입을 Spring for GraphQL이 자동 처리
    @QueryMapping
    fun reservation(@Argument id: Long): Mono<Reservation> =
        reservationRepository.findById(id)

    // 전체 예약 목록 — schema.graphqls의 Query.reservations에 매핑
    // @QueryMapping: type Query { reservations: [Reservation!]! }
    // 반환: Flux<Reservation> — 여러 건을 스트리밍으로 반환
    @QueryMapping
    fun reservations(): Flux<Reservation> =
        reservationRepository.findAll()

    // 전체 요약 통계 — schema.graphqls의 Query.statisticsSummary에 매핑
    // 기존 StatisticsService의 getSummaryStatistics()를 재사용한다
    @QueryMapping
    fun statisticsSummary(): Mono<SummaryStatistics> =
        statisticsService.getSummaryStatistics()

    // 채널 목록 — schema.graphqls의 Query.channels에 매핑
    @QueryMapping
    fun channels(): Flux<Channel> =
        channelRepository.findAll()

    // ===== Mutation 리졸버 (쓰기) =====

    // 예약 생성 — schema.graphqls의 Mutation.createReservation(input)에 매핑
    // @MutationMapping: type Mutation { createReservation(input: ...): Reservation! }
    // @Argument: input 객체를 ReservationCreateRequest에 바인딩
    // 기존 ReservationService.createReservation()을 재사용하여 REST API와 동일한 로직 실행
    @MutationMapping
    fun createReservation(@Argument input: ReservationCreateRequest): Mono<Reservation> =
        reservationService.createReservation(input)
            .flatMap { response ->
                // ReservationResponse → Reservation 엔티티를 다시 조회 (GraphQL은 엔티티를 직접 반환)
                reservationRepository.findById(response.id)
            }

    // ===== SchemaMapping 리졸버 (연관 필드) =====

    // Reservation.channel 필드 리졸버
    // @SchemaMapping: type Reservation { channel: Channel }
    // GraphQL 클라이언트가 { reservation(id: 1) { channel { channelName } } } 처럼
    // channel 필드를 요청할 때만 실행된다 (요청하지 않으면 실행 안 됨 — Lazy 로딩)
    // typeName: 부모 타입 이름, field: 필드 이름
    // 첫 번째 파라미터: 부모 객체 (Reservation) — Spring for GraphQL이 자동 주입
    @SchemaMapping(typeName = "Reservation", field = "channel")
    fun channel(reservation: Reservation): Mono<Channel> =
        channelRepository.findById(reservation.channelId)
}
