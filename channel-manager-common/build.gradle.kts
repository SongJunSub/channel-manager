// common 모듈 - Flyway 마이그레이션 SQL 및 공유 리소스를 담는 모듈
// 실행 가능한 애플리케이션이 아니므로 Spring Boot 플러그인을 적용하지 않는다.

// 이 모듈은 Java 플러그인만 적용 (루트 subprojects 블록에서 이미 적용됨)
// src/main/resources/db/migration/ 에 Flyway SQL 파일을 위치시킨다.
// Kotlin/Java 모듈이 이 모듈을 의존하여 리소스를 공유한다.
