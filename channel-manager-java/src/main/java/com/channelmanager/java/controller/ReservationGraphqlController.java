package com.channelmanager.java.controller; // 컨트롤러 패키지

import com.channelmanager.java.domain.Channel; // 채널 엔티티
import com.channelmanager.java.domain.Reservation; // 예약 엔티티
import com.channelmanager.java.dto.ReservationCreateRequest; // 예약 생성 요청 DTO
import com.channelmanager.java.dto.StatisticsResponse.SummaryStatistics; // 요약 통계 DTO
import com.channelmanager.java.repository.ChannelRepository; // 채널 리포지토리
import com.channelmanager.java.repository.ReservationRepository; // 예약 리포지토리
import com.channelmanager.java.service.ReservationService; // 예약 서비스
import com.channelmanager.java.service.StatisticsService; // 통계 서비스
import lombok.RequiredArgsConstructor; // final 필드 생성자
import org.springframework.graphql.data.method.annotation.Argument; // GraphQL 인자 바인딩
import org.springframework.graphql.data.method.annotation.MutationMapping; // Mutation 리졸버
import org.springframework.graphql.data.method.annotation.QueryMapping; // Query 리졸버
import org.springframework.graphql.data.method.annotation.SchemaMapping; // 연관 필드 리졸버
import org.springframework.stereotype.Controller; // Spring MVC Controller
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림

// GraphQL 리졸버 컨트롤러
// Phase 26: REST API와 별도로 GraphQL 엔드포인트를 제공한다
// Kotlin에서는 @Controller class ReservationGraphqlController(...)이지만,
// Java에서는 @Controller @RequiredArgsConstructor public class이다
@Controller
@RequiredArgsConstructor
public class ReservationGraphqlController {

    private final ReservationRepository reservationRepository;
    private final ChannelRepository channelRepository;
    private final ReservationService reservationService;
    private final StatisticsService statisticsService;

    // ===== Query 리졸버 =====

    // 예약 단건 조회
    @QueryMapping
    public Mono<Reservation> reservation(@Argument Long id) {
        return reservationRepository.findById(id);
    }

    // 전체 예약 목록
    @QueryMapping
    public Flux<Reservation> reservations() {
        return reservationRepository.findAll();
    }

    // 전체 요약 통계
    @QueryMapping
    public Mono<SummaryStatistics> statisticsSummary() {
        return statisticsService.getSummaryStatistics();
    }

    // 채널 목록
    @QueryMapping
    public Flux<Channel> channels() {
        return channelRepository.findAll();
    }

    // ===== Mutation 리졸버 =====

    // 예약 생성
    @MutationMapping
    public Mono<Reservation> createReservation(@Argument ReservationCreateRequest input) {
        return reservationService.createReservation(input)
            .flatMap(response -> reservationRepository.findById(response.id()));
    }

    // ===== SchemaMapping 리졸버 =====

    // Reservation.channel 연관 필드
    @SchemaMapping(typeName = "Reservation", field = "channel")
    public Mono<Channel> channel(Reservation reservation) {
        return channelRepository.findById(reservation.getChannelId());
    }
}
