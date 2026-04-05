package com.channelmanager.java.config; // 테스트 설정 패키지

import org.springframework.boot.test.context.TestConfiguration; // 테스트 전용 설정
import org.springframework.boot.testcontainers.service.connection.ServiceConnection; // 자동 접속 정보 주입
import org.springframework.context.annotation.Bean; // 빈 등록
import org.testcontainers.containers.PostgreSQLContainer; // PostgreSQL 컨테이너

// Testcontainers 테스트 설정 — 테스트 시 PostgreSQL 컨테이너를 자동으로 생성한다
// @TestConfiguration: 테스트에서만 사용되는 설정 클래스 (프로덕션 빌드에 포함되지 않음)
// proxyBeanMethods = false: 프록시 없이 빈을 등록한다 (경량 설정)
// 각 테스트 클래스에서 @Import(TestcontainersConfig.class)로 이 설정을 가져온다
// Kotlin에서는 @TestConfiguration(proxyBeanMethods = false)로 동일하게 설정한다
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    // PostgreSQL 컨테이너 빈 — Testcontainers가 Docker에서 자동으로 기동한다
    // @ServiceConnection: Spring Boot가 컨테이너의 호스트/포트를 자동으로 인식하여
    //   spring.r2dbc.url, spring.flyway.url 등의 설정을 동적으로 주입한다
    // Kotlin에서는 fun postgres(): PostgreSQLContainer<*>이지만,
    // Java에서는 PostgreSQLContainer<?> postgres()이다
    @Bean
    @ServiceConnection // 자동 접속 정보 주입 (R2DBC + Flyway JDBC 모두)
    public PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine") // 프로덕션과 동일한 PostgreSQL 17 이미지
            .withDatabaseName("channel_manager_test") // 테스트 전용 DB 이름
            .withUsername("test")                      // 테스트 전용 사용자
            .withPassword("test");                     // 테스트 전용 비밀번호
    }
}
