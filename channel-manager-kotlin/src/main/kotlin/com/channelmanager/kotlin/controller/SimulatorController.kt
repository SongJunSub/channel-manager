package com.channelmanager.kotlin.controller // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.kotlin.simulator.ChannelSimulator // 채널 시뮬레이터
import org.springframework.web.bind.annotation.GetMapping // GET 메서드 매핑
import org.springframework.web.bind.annotation.PostMapping // POST 메서드 매핑
import org.springframework.web.bind.annotation.RestController // REST 컨트롤러 선언
import reactor.core.publisher.Mono // 0~1개 비동기 스트림

// 시뮬레이터 제어 REST 컨트롤러
// 채널 시뮬레이터(ChannelSimulator)의 시작/중지/상태 조회를 제어하는 API를 제공한다
// 서버 기동 시 시뮬레이터는 자동 시작하지 않고, 이 API를 통해 수동으로 제어한다
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
@RestController
class SimulatorController(
    private val channelSimulator: ChannelSimulator // 시뮬레이터 빈 주입
) {

    // 시뮬레이터 시작
    // POST /api/simulator/start
    // 이미 실행 중이면 ChannelSimulator.start() 내부에서 무시한다
    // Mono.fromRunnable: 동기적인 void 메서드를 Reactive 체인에 포함시킨다
    // thenReturn: Mono<Void> → Mono<Map>으로 변환하여 응답 본문을 반환한다
    @PostMapping("/api/simulator/start")
    fun startSimulator(): Mono<Map<String, Any>> =
        Mono.fromRunnable<Void> { channelSimulator.start() } // 시뮬레이터 시작 (동기 → Mono)
            .thenReturn( // 시작 완료 후 상태 응답
                mapOf( // Kotlin의 mapOf로 간결하게 Map 생성
                    "message" to "시뮬레이터가 시작되었습니다", // 메시지
                    "running" to true // 실행 상태
                )
            )

    // 시뮬레이터 중지
    // POST /api/simulator/stop
    // 이미 중지 상태이면 ChannelSimulator.stop() 내부에서 무시한다
    // Mono.fromRunnable: 동기적인 void 메서드를 Reactive 체인에 포함시킨다
    @PostMapping("/api/simulator/stop")
    fun stopSimulator(): Mono<Map<String, Any>> =
        Mono.fromRunnable<Void> { channelSimulator.stop() } // 시뮬레이터 중지 (동기 → Mono)
            .thenReturn( // 중지 완료 후 상태 응답
                mapOf(
                    "message" to "시뮬레이터가 중지되었습니다", // 메시지
                    "running" to false // 실행 상태
                )
            )

    // 시뮬레이터 상태 조회
    // GET /api/simulator/status
    // 현재 시뮬레이터의 실행 여부를 반환한다
    // Mono.just: 즉시 값을 발행하는 Mono (DB 호출 없음)
    @GetMapping("/api/simulator/status")
    fun getSimulatorStatus(): Mono<Map<String, Any>> =
        Mono.just( // 즉시 응답 (비동기 작업 없음)
            mapOf(
                "running" to channelSimulator.running // 현재 실행 상태
            )
        )
}
