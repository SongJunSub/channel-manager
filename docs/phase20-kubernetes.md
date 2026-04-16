# Phase 20 — Kubernetes 배포

## 1. Kubernetes란?

### 1.1 왜 Kubernetes가 필요한가?

```
Docker Compose:                      Kubernetes:
  - 단일 머신에서 컨테이너 실행          - 여러 머신(클러스터)에서 컨테이너 실행
  - 수동 스케일링                      - 자동 스케일링 (HPA)
  - 재시작: restart: on-failure       - 자동 복구 (Self-Healing)
  - 배포: docker compose up           - 롤링 업데이트 + 롤백
  - 로컬 개발/소규모 운영              - 프로덕션 대규모 운영
```

Kubernetes(K8s)는 **컨테이너화된 애플리케이션의 배포, 스케일링, 관리를 자동화**하는 오케스트레이션 플랫폼이다.

### 1.2 핵심 개념

| 개념 | 설명 | Docker Compose 비유 |
|------|------|-------------------|
| **Pod** | 컨테이너의 최소 실행 단위 | 하나의 서비스 컨테이너 |
| **Deployment** | Pod의 원하는 상태를 선언 (레플리카 수, 이미지 등) | 서비스 정의 |
| **Service** | Pod에 접근하기 위한 안정적인 네트워크 엔드포인트 | ports + 네트워크 |
| **ConfigMap** | 설정 데이터 (환경변수, 설정 파일) | environment 섹션 |
| **Secret** | 민감한 데이터 (비밀번호, 토큰) | 환경변수 (암호화) |
| **Namespace** | 리소스를 논리적으로 분리하는 가상 클러스터 | 프로젝트 디렉토리 |
| **PersistentVolumeClaim** | 영속적 스토리지 요청 | volumes 섹션 |

## 2. K8s 매니페스트 구조

### 2.1 YAML 매니페스트

```yaml
apiVersion: apps/v1          # API 그룹 및 버전
kind: Deployment             # 리소스 종류
metadata:
  name: kotlin-app           # 리소스 이름
  namespace: channel-manager # 네임스페이스
  labels:
    app: kotlin-app          # 라벨 (선택자로 사용)
spec:
  replicas: 2                # 원하는 Pod 수
  selector:
    matchLabels:
      app: kotlin-app        # 이 라벨을 가진 Pod를 관리
  template:                  # Pod 템플릿
    metadata:
      labels:
        app: kotlin-app
    spec:
      containers:
        - name: kotlin-app
          image: ghcr.io/user/channel-manager-kotlin:latest
          ports:
            - containerPort: 8080
          env:
            - name: DB_HOST
              value: postgres
```

### 2.2 주요 리소스 관계

```
┌─────────────┐
│  Deployment │ ── 원하는 상태 선언 (레플리카 수, 이미지 버전)
└──────┬──────┘
       │ 관리
       ▼
┌─────────────┐
│   ReplicaSet│ ── 실제 Pod 수를 유지 (자동 생성됨)
└──────┬──────┘
       │ 생성
       ▼
┌──────┴──────┐
│ Pod  │ Pod  │ ── 실제 컨테이너 실행
└──────┴──────┘
       ▲
       │ 트래픽 라우팅
┌──────┴──────┐
│   Service   │ ── 안정적인 IP + DNS 이름
└─────────────┘
```

## 3. Service 타입

| 타입 | 설명 | 용도 |
|------|------|------|
| **ClusterIP** | 클러스터 내부에서만 접근 가능 (기본값) | DB, Redis, 내부 서비스 |
| **NodePort** | 노드의 특정 포트로 외부 접근 | 개발/테스트 |
| **LoadBalancer** | 클라우드 로드밸런서 연동 | 프로덕션 외부 노출 |

```
외부 → LoadBalancer(80) → Service(ClusterIP) → Pod(:8080)
                                              → Pod(:8080)
```

## 4. ConfigMap과 Secret

### 4.1 ConfigMap — 비민감 설정

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  DB_HOST: "postgres"
  DB_PORT: "5432"
  DB_NAME: "channel_manager"
```

### 4.2 Secret — 민감 설정

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  DB_USERNAME: cG9zdGdyZXM=    # base64("postgres")
  DB_PASSWORD: cG9zdGdyZXM=    # base64("postgres")
```

- Secret은 base64로 인코딩되어 저장 (암호화는 아님, etcd 암호화는 별도 설정)
- Pod에서 환경변수 또는 파일로 마운트하여 사용

## 5. 헬스 체크 (Probe)

### 5.1 Probe 종류

```
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ startupProbe  │───▶│ livenessProbe │    │readinessProbe │
│ 시작 완료 확인  │    │ 생존 여부 확인  │    │ 요청 수용 가능? │
│ (한 번만 실행) │    │ 실패 → 재시작  │    │ 실패 → 트래픽 차단│
└───────────────┘    └───────────────┘    └───────────────┘
```

- **startupProbe**: 앱이 초기화를 완료했는지 확인 (Flyway 마이그레이션 등)
- **livenessProbe**: 앱이 살아있는지 확인 — 실패하면 Pod 재시작
- **readinessProbe**: 앱이 요청을 받을 수 있는지 확인 — 실패하면 Service에서 제외

### 5.2 Spring Boot Actuator와 연동

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

## 6. Kustomize

### 6.1 Kustomize란?

```
k8s/
├── base/                    # 기본 매니페스트 (공통)
│   ├── kustomization.yml
│   ├── namespace.yml
│   ├── postgres/
│   ├── redis/
│   ├── kotlin-app/
│   └── java-app/
└── overlays/                # 환경별 오버레이 (향후 확장)
    ├── dev/
    └── prod/
```

- Kubernetes 기본 내장 도구 (별도 설치 불필요)
- `kubectl apply -k k8s/base/`로 모든 리소스를 한 번에 배포
- 환경별 오버레이로 설정 분리 가능 (dev/prod)

## 7. 이 프로젝트의 K8s 구성

### 7.1 전체 아키텍처

```
Namespace: channel-manager
┌─────────────────────────────────────────────────────┐
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │ postgres │  │  redis   │  │    kotlin-app    │  │
│  │ :5432    │  │  :6379   │  │    :8080 (×2)    │  │
│  │ClusterIP │  │ClusterIP │  │   LoadBalancer   │  │
│  └──────────┘  └──────────┘  └──────────────────┘  │
│                                                     │
│                               ┌──────────────────┐  │
│                               │     java-app     │  │
│                               │    :8081 (×2)    │  │
│                               │   LoadBalancer   │  │
│                               └──────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 7.2 파일 구조

```
k8s/
├── base/
│   ├── kustomization.yml        ← 리소스 목록 + 공통 라벨
│   ├── namespace.yml            ← channel-manager 네임스페이스
│   ├── configmap.yml            ← DB/Redis 접속 정보 (비민감)
│   ├── secret.yml               ← DB 비밀번호 (base64)
│   ├── postgres-deployment.yml  ← PostgreSQL Deployment + PVC
│   ├── postgres-service.yml     ← PostgreSQL ClusterIP Service
│   ├── redis-deployment.yml     ← Redis Deployment
│   ├── redis-service.yml        ← Redis ClusterIP Service
│   ├── kotlin-deployment.yml    ← Kotlin앱 Deployment (2 replicas)
│   ├── kotlin-service.yml       ← Kotlin앱 LoadBalancer Service
│   ├── java-deployment.yml      ← Java앱 Deployment (2 replicas)
│   └── java-service.yml         ← Java앱 LoadBalancer Service
```
