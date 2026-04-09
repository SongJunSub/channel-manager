// Java 모듈 - Spring WebFlux + R2DBC를 Java로 구현하는 모듈

plugins {
    id("org.springframework.boot")             // Spring Boot 플러그인 - 실행 가능한 JAR 생성
    id("io.spring.dependency-management")      // Spring Boot BOM 기반 의존성 버전 자동 관리
}

dependencies {
    // === common 모듈 의존 (Flyway SQL, 공유 리소스) ===
    implementation(project(":channel-manager-common"))  // common 모듈의 리소스를 클래스패스에 포함

    // === Spring WebFlux ===
    implementation("org.springframework.boot:spring-boot-starter-webflux")  // WebFlux 스타터 (Netty 포함)

    // === Spring Data R2DBC ===
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")  // R2DBC 스타터

    // === PostgreSQL R2DBC 드라이버 ===
    runtimeOnly("org.postgresql:r2dbc-postgresql")  // 런타임에만 필요한 R2DBC PostgreSQL 드라이버

    // === Flyway (DB 마이그레이션) ===
    // Spring Boot 4.x에서는 flyway-core 대신 starter를 사용해야 자동설정이 활성화된다
    implementation("org.springframework.boot:spring-boot-starter-flyway")           // Flyway 스타터 (자동설정 + JDBC DataSource 포함)
    implementation("org.flywaydb:flyway-database-postgresql")                       // Flyway PostgreSQL 지원
    runtimeOnly("org.postgresql:postgresql")                                        // Flyway가 사용할 JDBC 드라이버

    // === OpenAPI (Swagger) ===
    implementation("io.swagger.core.v3:swagger-annotations:2.2.28")  // @Schema 등 API 문서화 어노테이션
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6")  // Phase 11: Swagger UI + OpenAPI 자동 생성

    // === 구조화 로깅 (Phase 12) ===
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")  // JSON 구조화 로그 인코더

    // === Rate Limiting (Phase 17) ===
    implementation("com.bucket4j:bucket4j_jdk17-core:8.16.1")  // Token Bucket 알고리즘 인메모리 구현 (JDK 17+)

    // === Lombok (Java 보일러플레이트 코드 제거) ===
    compileOnly("org.projectlombok:lombok")           // 컴파일 시에만 필요 (getter, setter 등 자동 생성)
    annotationProcessor("org.projectlombok:lombok")   // Lombok 어노테이션 프로세서

    // === 테스트 ===
    testImplementation("org.springframework.boot:spring-boot-starter-test")  // 테스트 스타터 (JUnit 5 포함)
    testImplementation("io.projectreactor:reactor-test")                     // Reactor 테스트 도구 (StepVerifier)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")  // Phase 13: Testcontainers Spring Boot 통합
    testImplementation("org.testcontainers:postgresql:1.21.0")                  // Phase 13: PostgreSQL Testcontainers 모듈
    testImplementation("org.testcontainers:r2dbc:1.21.0")                      // Phase 13: R2DBC Testcontainers 모듈 (@ServiceConnection R2DBC 지원)
}

// 테스트 실행 시 JUnit Platform 사용
tasks.withType<Test> {
    useJUnitPlatform()  // JUnit 5 플랫폼으로 테스트 실행
}
