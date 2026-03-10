// Spring Boot 플러그인 - 실행 가능한 JAR 생성, 의존성 BOM 관리
// Kotlin JVM 플러그인 - Kotlin 컴파일 지원
// Kotlin Spring 플러그인 - @Component, @Service 등이 붙은 클래스를 자동으로 open 처리
plugins {
    java                                                                // Java 컴파일 지원
    id("org.springframework.boot") version "4.0.3" apply false         // 서브 모듈에서 개별 적용
    id("io.spring.dependency-management") version "1.1.7" apply false  // 서브 모듈에서 개별 적용
    kotlin("jvm") version "2.3.10" apply false                         // 서브 모듈에서 개별 적용
    kotlin("plugin.spring") version "2.3.10" apply false               // 서브 모듈에서 개별 적용
}

// 모든 프로젝트(루트 + 서브 모듈)에 공통 적용되는 설정
allprojects {
    group = "com.channelmanager"     // 프로젝트 그룹 ID
    version = "0.0.1-SNAPSHOT"       // 프로젝트 버전

    // 의존성을 다운로드할 저장소 설정
    repositories {
        mavenCentral()               // Maven 중앙 저장소
    }
}

// 서브 모듈에만 공통 적용되는 설정 (루트 프로젝트 제외)
subprojects {
    // Java 플러그인 적용 - 모든 서브 모듈은 Java 기반
    apply(plugin = "java")

    // Java 25 버전 설정
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))  // Java 25 LTS 사용
        }
    }
}
