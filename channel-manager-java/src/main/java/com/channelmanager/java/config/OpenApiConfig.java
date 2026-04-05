package com.channelmanager.java.config; // 설정 패키지

import io.swagger.v3.oas.models.OpenAPI; // OpenAPI 스펙 루트 객체
import io.swagger.v3.oas.models.info.Contact; // API 연락처 정보
import io.swagger.v3.oas.models.info.Info; // API 정보 (제목, 설명, 버전)
import org.springframework.context.annotation.Bean; // 빈 등록 어노테이션
import org.springframework.context.annotation.Configuration; // 설정 클래스 어노테이션

// OpenAPI (Swagger) 설정 클래스
// SpringDoc이 자동으로 컨트롤러를 분석하여 OpenAPI 3.0 스펙을 생성하지만,
// API 제목, 설명 등 메타데이터는 이 빈에서 설정한다
// @Configuration: Spring이 이 클래스를 설정 클래스로 인식하고 @Bean 메서드를 호출한다
// Kotlin에서는 fun openApi(): OpenAPI = OpenAPI()...로 단일 표현식으로 정의하지만,
// Java에서는 return new OpenAPI()...로 명시적 return을 사용한다
@Configuration
public class OpenApiConfig {

    // OpenAPI 빈 — API 문서의 메타데이터를 설정한다
    // Info: API 제목, 설명, 버전 등을 정의한다
    // 이 빈이 없어도 SpringDoc은 기본 문서를 생성하지만,
    // 제목이 "OpenAPI definition"으로 표시되므로 프로젝트에 맞게 커스터마이징한다
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Channel Manager API") // API 제목 — Swagger UI 상단에 표시
                .description( // API 설명 — 프로젝트 개요
                    "호텔 멀티 채널 예약 동기화 시스템 — " +
                        "Spring WebFlux + R2DBC 기반 리액티브 API (Java 구현)"
                )
                .version("1.0.0") // API 버전
                .contact(new Contact() // 연락처 정보
                    .name("Channel Manager") // 이름
                )
            );
    }
}
