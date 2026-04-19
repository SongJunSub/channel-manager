// Kotlin 모듈 - Spring WebFlux + R2DBC를 Kotlin으로 구현하는 모듈

plugins {
    id("org.springframework.boot")             // Spring Boot 플러그인 - 실행 가능한 JAR 생성
    id("io.spring.dependency-management")      // Spring Boot BOM 기반 의존성 버전 자동 관리
    kotlin("jvm")                              // Kotlin JVM 컴파일 플러그인
    kotlin("plugin.spring")                    // Spring 클래스 자동 open 처리 (CGLib 프록시 호환)
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

    // === Security + JWT (Phase 21) ===
    implementation("org.springframework.boot:spring-boot-starter-security")     // Spring Security WebFlux
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")                          // JWT API
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")                            // JWT 구현체
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")                         // JWT Jackson 직렬화

    // === 모니터링 (Phase 19) ===
    implementation("org.springframework.boot:spring-boot-starter-actuator")  // Actuator 엔드포인트 (health, metrics 등)
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")              // Prometheus 메트릭 레지스트리

    // === 분산 추적 (Phase 22) ===
    implementation("io.micrometer:micrometer-tracing-bridge-brave")          // Micrometer Tracing ↔ Brave 브릿지
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")              // Brave → Zipkin HTTP 리포터

    // === Resilience4j (Phase 27) ===
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")   // Resilience4j Spring Boot 통합
    implementation("io.github.resilience4j:resilience4j-reactor:2.3.0")        // Resilience4j Reactor 연산자

    // === GraphQL (Phase 26) ===
    implementation("org.springframework.boot:spring-boot-starter-graphql")      // Spring for GraphQL

    // === Kafka (Phase 25) ===
    implementation("org.springframework.kafka:spring-kafka")                    // Spring Kafka 기본
    // reactor-kafka는 현재 kafka-clients 4.x와 호환되지 않아 spring-kafka만 사용한다

    // === Rate Limiting (Phase 17) ===
    implementation("com.bucket4j:bucket4j_jdk17-core:8.16.1")  // Token Bucket 알고리즘 인메모리 구현 (JDK 17+)

    // === Redis 캐싱 (Phase 18) ===
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")  // Reactive Redis (Lettuce 드라이버)

    // === Kotlin 지원 ===
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")  // Jackson Kotlin 모듈 (JSON 직렬화)
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")  // Reactor Kotlin 확장 함수
    implementation("org.jetbrains.kotlin:kotlin-reflect")                 // Kotlin 리플렉션 (Spring이 내부적으로 사용)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")   // 코루틴-Reactor 브릿지

    // === 테스트 ===
    testImplementation("org.springframework.boot:spring-boot-starter-test")  // 테스트 스타터 (JUnit 5 포함)
    testImplementation("io.projectreactor:reactor-test")                     // Reactor 테스트 도구 (StepVerifier)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")  // Phase 13: Testcontainers Spring Boot 통합
    testImplementation("org.testcontainers:postgresql:1.21.0")                  // Phase 13: PostgreSQL Testcontainers 모듈
    testImplementation("org.testcontainers:r2dbc:1.21.0")                      // Phase 13: R2DBC Testcontainers 모듈 (@ServiceConnection R2DBC 지원)
    testImplementation("com.redis:testcontainers-redis:2.2.4")                 // Phase 18: Redis Testcontainers 모듈
}

// Kotlin 컴파일 옵션 설정
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")  // JSR-305 null 안전성 어노테이션을 엄격하게 처리
    }
}

// 테스트 실행 시 JUnit Platform 사용
tasks.withType<Test> {
    useJUnitPlatform()  // JUnit 5 플랫폼으로 테스트 실행
}
