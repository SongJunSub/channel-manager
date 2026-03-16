package com.channelmanager.java.config; // 설정 패키지

import org.springframework.beans.factory.annotation.Value; // 프로퍼티 값 주입 어노테이션
import org.springframework.context.annotation.Bean; // Spring 빈 등록 어노테이션
import org.springframework.context.annotation.Configuration; // Spring 설정 클래스 어노테이션
import org.springframework.web.reactive.function.client.WebClient; // 논블로킹 HTTP 클라이언트

// WebClient 설정 클래스
// 시뮬레이터(ChannelSimulator)가 내부 예약 API를 호출할 때 사용하는 WebClient를 빈으로 등록한다
// baseUrl을 설정하여 매 요청마다 전체 URL을 지정하지 않아도 되도록 한다
// @Value로 application.yml의 server.port 값을 읽어 baseUrl을 동적으로 설정한다
// Kotlin에서는 단일 표현식 함수(=)를 사용하지만, Java에서는 return문을 사용한다
@Configuration
public class WebClientConfig {

    // WebClient 빈 생성
    // WebClient.builder()로 빌더 패턴을 사용하여 WebClient 인스턴스를 생성한다
    // baseUrl: 모든 요청의 기본 URL을 설정한다 (예: http://localhost:8081)
    // 시뮬레이터는 자기 자신의 API를 호출하므로 localhost + 현재 서버 포트를 사용한다
    // Kotlin에서는 문자열 템플릿 "http://localhost:$port"를 사용하지만,
    // Java에서는 문자열 연결 "http://localhost:" + port를 사용한다
    @Bean
    public WebClient webClient(
            @Value("${server.port:8081}") int port) { // application.yml의 server.port 값
        return WebClient.builder() // WebClient 빌더 시작
            .baseUrl("http://localhost:" + port) // 기본 URL 설정 (자기 자신 서버)
            .build(); // WebClient 인스턴스 생성
    }
}
