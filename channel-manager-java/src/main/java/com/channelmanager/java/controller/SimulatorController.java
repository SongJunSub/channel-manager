package com.channelmanager.java.controller; // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.java.simulator.ChannelSimulator; // 채널 시뮬레이터
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 (Lombok)
import org.springframework.web.bind.annotation.GetMapping; // GET 메서드 매핑
import org.springframework.web.bind.annotation.PostMapping; // POST 메서드 매핑
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러 선언
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.util.Map; // Map 컬렉션

// 시뮬레이터 제어 REST 컨트롤러
// 채널 시뮬레이터(ChannelSimulator)의 시작/중지/상태 조회를 제어하는 API를 제공한다
// 서버 기동 시 시뮬레이터는 자동 시작하지 않고, 이 API를 통해 수동으로 제어한다
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
// @RequiredArgsConstructor: Lombok이 final 필드에 대한 생성자를 자동 생성한다
// Kotlin에서는 primary constructor에 val로 선언하지만,
// Java에서는 @RequiredArgsConstructor + private final 필드로 동일한 효과를 얻는다
@RestController
@RequiredArgsConstructor
public class SimulatorController {

    private final ChannelSimulator channelSimulator; // 시뮬레이터 빈 주입

    // 시뮬레이터 시작
    // POST /api/simulator/start
    // 이미 실행 중이면 ChannelSimulator.start() 내부에서 무시한다
    // Mono.fromRunnable: 동기적인 void 메서드를 Reactive 체인에 포함시킨다
    // thenReturn: Mono<Void> → Mono<Map>으로 변환하여 응답 본문을 반환한다
    // Kotlin에서는 mapOf("key" to "value")를 사용하지만,
    // Java에서는 Map.of("key", "value")를 사용한다
    @PostMapping("/api/simulator/start")
    public Mono<Map<String, Object>> startSimulator() {
        return Mono.<Void>fromRunnable(() -> channelSimulator.start()) // 시작 (동기 → Mono)
            .thenReturn( // 시작 완료 후 상태 응답
                Map.of( // Java 9+의 불변 Map 생성
                    "message", "시뮬레이터가 시작되었습니다", // 메시지
                    "running", true // 실행 상태
                )
            );
    }

    // 시뮬레이터 중지
    // POST /api/simulator/stop
    // 이미 중지 상태이면 ChannelSimulator.stop() 내부에서 무시한다
    @PostMapping("/api/simulator/stop")
    public Mono<Map<String, Object>> stopSimulator() {
        return Mono.<Void>fromRunnable(() -> channelSimulator.stop()) // 중지 (동기 → Mono)
            .thenReturn(
                Map.of(
                    "message", "시뮬레이터가 중지되었습니다", // 메시지
                    "running", false // 실행 상태
                )
            );
    }

    // 시뮬레이터 상태 조회
    // GET /api/simulator/status
    // 현재 시뮬레이터의 실행 여부를 반환한다
    // Mono.just: 즉시 값을 발행하는 Mono (DB 호출 없음)
    // Kotlin에서는 channelSimulator.running으로 프로퍼티에 접근하지만,
    // Java에서는 channelSimulator.isRunning()으로 getter를 호출한다
    @GetMapping("/api/simulator/status")
    public Mono<Map<String, Object>> getSimulatorStatus() {
        return Mono.just( // 즉시 응답 (비동기 작업 없음)
            Map.of(
                "running", channelSimulator.isRunning() // 현재 실행 상태
            )
        );
    }
}
