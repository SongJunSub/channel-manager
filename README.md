# Channel Manager

호텔 멀티 채널 예약 동기화 시스템 - Spring WebFlux 학습 프로젝트

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 25 (LTS) |
| Kotlin | 2.3.10 |
| Spring Boot | 4.0.3 |
| Gradle | 9.4.0 |
| PostgreSQL | 17 |

## 프로젝트 구조

```
channel-manager/
├── channel-manager-kotlin/    # Kotlin 구현 (port: 8080)
├── channel-manager-java/      # Java 구현 (port: 8081)
├── channel-manager-common/    # 공유 리소스 (Flyway SQL)
└── docs/                      # Phase별 개념 정리
```

## 로컬 개발 환경 설정

### 1. PostgreSQL 실행

Docker Desktop이 실행 중인 상태에서:

```bash
docker compose up -d
```

컨테이너가 정상 실행되면 `channel_manager` 데이터베이스가 자동 생성된다.

### 2. DB 접속 정보

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `channel_manager` |
| User | `postgres` |
| Password | `postgres` |

**IntelliJ Database 접속:**
1. 우측 **Database** 패널 → **+** → **Data Source** → **PostgreSQL**
2. 위 접속 정보 입력
3. **Test Connection** → **OK**

**CLI 접속:**
```bash
docker exec -it channel-manager-postgres psql -U postgres -d channel_manager
```

### 3. 애플리케이션 실행

```bash
# Kotlin 모듈 (port: 8080)
./gradlew :channel-manager-kotlin:bootRun

# Java 모듈 (port: 8081)
./gradlew :channel-manager-java:bootRun
```

앱 시작 시 Flyway가 자동으로 테이블을 생성한다.

### 4. Docker 종료

```bash
# 컨테이너 중지 (데이터 유지)
docker compose stop

# 컨테이너 + 볼륨 삭제 (데이터 초기화)
docker compose down -v
```
