# =============================================================================
# Channel Manager Kotlin 모듈 — 멀티스테이지 Dockerfile
# =============================================================================
# Stage 1 (build): Gradle로 JAR 빌드 — JDK + Gradle 포함 (~800MB)
# Stage 2 (run):   JRE만으로 JAR 실행 — 최종 이미지 (~200MB)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: 빌드 단계 — Gradle Wrapper로 Spring Boot Fat JAR 생성
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS build
# eclipse-temurin: Adoptium에서 제공하는 공식 OpenJDK 배포판
# 25-jdk-alpine: JDK 25 + Alpine Linux (빌드에는 JDK가 필요)

# 작업 디렉토리 설정 (없으면 자동 생성)
WORKDIR /workspace

# --- 레이어 캐싱 최적화: 의존성 파일을 먼저 복사 ---
# Gradle Wrapper 파일 복사 (변경 드묾 → 캐시 적중률 높음)
COPY gradlew gradlew
COPY gradle/ gradle/

# 빌드 설정 파일 복사 (의존성 변경 시에만 레이어 무효화)
COPY build.gradle.kts build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts
COPY gradle.properties gradle.properties

# 각 모듈의 빌드 설정 복사 (settings.gradle.kts가 3개 모듈을 모두 등록하므로 전부 필요)
COPY channel-manager-kotlin/build.gradle.kts channel-manager-kotlin/build.gradle.kts
COPY channel-manager-java/build.gradle.kts channel-manager-java/build.gradle.kts
COPY channel-manager-common/build.gradle.kts channel-manager-common/build.gradle.kts

# Gradle Wrapper 실행 권한 부여
RUN chmod +x gradlew

# Kotlin 모듈의 의존성 미리 다운로드 (소스 변경 시에도 이 레이어는 캐시됨)
# :channel-manager-kotlin:dependencies — 서브프로젝트의 실제 의존성을 해석하여 다운로드
# --no-daemon: 컨테이너 빌드에서는 데몬 불필요 (일회성 빌드)
RUN ./gradlew :channel-manager-kotlin:dependencies --no-daemon

# --- 소스 코드 복사 후 빌드 (자주 변경 → 캐시 무효화 대상) ---
# common 모듈 소스 복사 (Flyway SQL, static 리소스)
COPY channel-manager-common/ channel-manager-common/

# Kotlin 모듈 소스 복사
COPY channel-manager-kotlin/src/ channel-manager-kotlin/src/

# Kotlin 모듈 Fat JAR 빌드
# -x test: Docker 빌드 시 테스트 스킵 (테스트는 CI에서 별도 실행)
# -x jar: plain JAR 생성 비활성화 (bootJar만 필요, *.jar 와일드카드 충돌 방지)
RUN ./gradlew :channel-manager-kotlin:bootJar --no-daemon -x test -x :channel-manager-kotlin:jar

# ---------------------------------------------------------------------------
# Stage 2: 실행 단계 — JRE만으로 경량 이미지 생성
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS run
# 25-jre-alpine: JRE만 포함 (JDK 대비 ~50% 크기 절감)

# 이미지 메타데이터 — 어떤 모듈의 이미지인지 표시
LABEL maintainer="channel-manager"
LABEL description="Channel Manager Kotlin Module — Spring Boot WebFlux"

# 작업 디렉토리 설정
WORKDIR /app

# 보안: 비루트 사용자로 실행 (root 권한 불필요)
# addgroup/adduser: Alpine Linux의 사용자 생성 명령어
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 빌드 단계에서 생성된 JAR 파일만 복사 (소스코드, Gradle 제외)
# --from=build: Stage 1의 파일시스템에서 복사
# --chown: 복사와 동시에 소유권 변경 (별도 RUN chown 레이어 불필요 → 이미지 크기 절감)
COPY --from=build --chown=appuser:appgroup /workspace/channel-manager-kotlin/build/libs/*.jar app.jar

# 비루트 사용자로 전환
USER appuser

# 컨테이너가 사용할 포트 선언 (문서화 목적, 실제 매핑은 docker-compose에서)
EXPOSE 8080

# JVM 옵션 환경변수 (docker-compose에서 오버라이드 가능)
# -XX:+UseContainerSupport: JVM이 컨테이너 메모리 제한을 인식
# -XX:MaxRAMPercentage=75.0: 컨테이너 메모리의 75%를 힙으로 사용
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 컨테이너 시작 시 실행할 명령
# sh -c로 감싸서 $JAVA_OPTS 환경변수를 확장한 뒤 java 프로세스 실행
# exec: sh를 java로 대체하여 PID 1이 java가 됨 → SIGTERM을 JVM이 직접 수신 (graceful shutdown)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
