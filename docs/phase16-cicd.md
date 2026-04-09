# Phase 16 — GitHub Actions CI/CD

## 1. CI/CD란?

### 1.1 CI (Continuous Integration) — 지속적 통합

```
개발자 A ──push──┐
                  │     ┌──────────────┐     ┌──────────┐
개발자 B ──push──┼────▶│ CI 서버      │────▶│ 결과 알림 │
                  │     │ (빌드+테스트)  │     └──────────┘
개발자 C ──push──┘     └──────────────┘
```

- 여러 개발자가 코드를 자주 통합(merge)하고, **매번 자동으로 빌드/테스트**를 실행
- 버그를 조기에 발견: "내 로컬에서는 되는데" 문제를 방지
- 통합 충돌을 최소화: 작은 변경을 자주 합치면 대규모 충돌 감소

### 1.2 CD (Continuous Delivery/Deployment) — 지속적 배포

```
CI 통과 ──▶ Docker 이미지 빌드 ──▶ 레지스트리 푸시 ──▶ 배포
                                    (GHCR, DockerHub)    (선택적)
```

- **Continuous Delivery**: CI 통과 후 배포 **가능한** 상태로 준비 (수동 승인 후 배포)
- **Continuous Deployment**: CI 통과 후 **자동**으로 프로덕션 배포
- 이 프로젝트에서는 Continuous Delivery 수준 — Docker 이미지까지 자동 빌드

### 1.3 CI/CD 파이프라인 전체 흐름

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌──────────┐    ┌─────────┐
│  코드    │    │  빌드   │    │ 테스트   │    │ 이미지    │    │ 배포    │
│  Push   │───▶│ Gradle  │───▶│ JUnit   │───▶│ Docker   │───▶│ (수동)  │
│         │    │ compile │    │ 5       │    │ build    │    │         │
└─────────┘    └─────────┘    └─────────┘    └──────────┘    └─────────┘
     CI ───────────────────────────────┘          CD ─────────────────┘
```

## 2. GitHub Actions

### 2.1 핵심 개념

| 개념 | 설명 | 비유 |
|------|------|------|
| **Workflow** | 자동화 파이프라인 전체 정의 (.yml 파일) | Jenkins Pipeline |
| **Event** | 워크플로우를 실행시키는 트리거 (push, PR 등) | 트리거 |
| **Job** | 워크플로우 안의 독립적인 작업 단위 | 스테이지 |
| **Step** | Job 안에서 순차 실행되는 개별 명령 | 단계 |
| **Action** | 재사용 가능한 작업 단위 (마켓플레이스) | 라이브러리 |
| **Runner** | 워크플로우를 실행하는 서버 | 빌드 서버 |
| **Artifact** | 빌드 결과물 (JAR, 테스트 리포트 등) | 산출물 |

### 2.2 워크플로우 파일 구조

```yaml
# .github/workflows/ci.yml
name: CI                          # 워크플로우 이름

on:                               # 트리거 이벤트
  push:
    branches: [main]              # main 브랜치 push 시
  pull_request:
    branches: [main]              # main 대상 PR 시

jobs:                             # 작업 정의
  build:                          # Job 이름
    runs-on: ubuntu-latest        # 실행 환경
    steps:                        # 단계별 실행
      - uses: actions/checkout@v4 # 소스 코드 체크아웃
      - name: Setup JDK           # 단계 이름
        uses: actions/setup-java@v4  # 공식 Java 설정 Action
        with:
          java-version: '25'
      - name: Build
        run: ./gradlew build      # 셸 명령 실행
```

### 2.3 트리거 이벤트 종류

```yaml
on:
  push:                    # 브랜치에 push 시
    branches: [main]
    paths-ignore:          # 이 경로 변경은 무시
      - 'docs/**'
      - '*.md'
  pull_request:            # PR 생성/업데이트 시
    branches: [main]
  schedule:                # cron 스케줄
    - cron: '0 0 * * *'   # 매일 자정
  workflow_dispatch:       # 수동 실행 버튼
```

## 3. GitHub Actions 주요 기능

### 3.1 캐싱 (의존성 다운로드 시간 절약)

```yaml
- uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: gradle-${{ hashFiles('**/*.gradle.kts') }}
    restore-keys: gradle-
```

- Gradle 의존성을 캐시하면 빌드 시간이 대폭 단축 (수 분 → 수십 초)
- `key`: 캐시 키 — `build.gradle.kts` 해시 기반 (의존성 변경 시 새 캐시)
- `restore-keys`: 완전 일치 실패 시 접두사 매칭으로 이전 캐시 활용

### 3.2 서비스 컨테이너 (테스트용 DB)

```yaml
services:
  postgres:
    image: postgres:17-alpine
    env:
      POSTGRES_DB: channel_manager
      POSTGRES_PASSWORD: postgres
    ports:
      - 5432:5432
    options: >-
      --health-cmd pg_isready
      --health-interval 10s
      --health-timeout 5s
      --health-retries 5
```

- Job 실행 중에 **PostgreSQL 컨테이너를 자동 기동**
- Testcontainers 대신 GitHub Actions 서비스로 DB를 제공하는 방식
- 이 프로젝트는 Testcontainers를 사용하므로 서비스 컨테이너 대신 Docker-in-Docker 사용

### 3.3 매트릭스 전략 (여러 환경 병렬 테스트)

```yaml
strategy:
  matrix:
    module: [channel-manager-kotlin, channel-manager-java]
```

- 하나의 Job 정의로 **여러 환경을 병렬 실행**
- 이 프로젝트: Kotlin 모듈과 Java 모듈을 병렬로 빌드/테스트

## 4. GitHub Container Registry (GHCR)

### 4.1 GHCR이란?

```
GitHub Actions ──build──▶ Docker Image ──push──▶ ghcr.io/user/image:tag
                                                      │
                                                      ▼
                                              docker pull ghcr.io/...
```

- GitHub에서 제공하는 **Docker 이미지 레지스트리**
- 도메인: `ghcr.io`
- GitHub 저장소와 자연스럽게 연동 (패키지 탭에서 이미지 확인)
- 공개 저장소의 이미지는 무료

### 4.2 인증

```yaml
- name: Login to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}  # 자동 제공되는 토큰
```

- `GITHUB_TOKEN`: GitHub Actions가 자동으로 제공하는 토큰 (별도 설정 불필요)
- 해당 저장소에 대한 패키지 push 권한을 가짐

## 5. 이 프로젝트의 CI/CD 구성

### 5.1 CI 워크플로우 (ci.yml)

```
트리거: push(main) / PR(main)
  │
  ├── Kotlin 빌드 + 테스트 (병렬)
  │     ├── JDK 25 설정
  │     ├── Gradle 캐시 복원
  │     ├── ./gradlew :channel-manager-kotlin:build
  │     └── 테스트 리포트 업로드
  │
  └── Java 빌드 + 테스트 (병렬)
        ├── JDK 25 설정
        ├── Gradle 캐시 복원
        ├── ./gradlew :channel-manager-java:build
        └── 테스트 리포트 업로드
```

### 5.2 CD 워크플로우 (cd.yml)

```
트리거: main push (CI 통과 후)
  │
  ├── Kotlin 이미지 빌드 + GHCR 푸시 (병렬)
  │     ├── ghcr.io/user/channel-manager-kotlin:latest
  │     └── ghcr.io/user/channel-manager-kotlin:sha-abc1234
  │
  └── Java 이미지 빌드 + GHCR 푸시 (병렬)
        ├── ghcr.io/user/channel-manager-java:latest
        └── ghcr.io/user/channel-manager-java:sha-abc1234
```

### 5.3 태그 전략

```
latest       — 가장 최근 main 브랜치 이미지
sha-abc1234  — 특정 커밋 해시 (롤백용)
```
