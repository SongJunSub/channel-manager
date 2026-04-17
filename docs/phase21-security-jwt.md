# Phase 21 — Spring Security + JWT 인증

## 1. 인증(Authentication)과 인가(Authorization)

### 1.1 인증 vs 인가

```
인증 (Authentication)               인가 (Authorization)
"당신은 누구인가?"                   "당신은 무엇을 할 수 있는가?"
  │                                  │
  ├── 로그인 (ID + 비밀번호)          ├── ROLE_USER: 예약 조회
  ├── JWT 토큰 검증                   ├── ROLE_ADMIN: 통계 조회, 시뮬레이터 제어
  └── API Key 확인                    └── ROLE_CHANNEL: 채널 API 접근
```

- **인증**: 사용자의 신원을 확인하는 과정 (로그인)
- **인가**: 인증된 사용자의 권한을 확인하는 과정 (역할 기반 접근 제어)

### 1.2 세션 기반 vs 토큰 기반 인증

```
세션 기반 (Stateful):
클라이언트 → 로그인 → 서버가 세션 생성 (서버 메모리에 저장)
            → 세션 ID 쿠키 반환
            → 매 요청마다 세션 ID 전송 → 서버가 세션 조회

토큰 기반 (Stateless):
클라이언트 → 로그인 → 서버가 JWT 토큰 생성 (서버에 저장하지 않음)
            → JWT 토큰 반환
            → 매 요청마다 JWT 전송 → 서버가 토큰 자체를 검증
```

| 비교 | 세션 기반 | 토큰 기반 (JWT) |
|------|----------|----------------|
| 상태 저장 | 서버 메모리/DB | 없음 (Stateless) |
| 확장성 | 세션 공유 필요 | 서버 간 공유 불필요 |
| WebFlux 호환 | 세션 저장소 필요 | 완벽 호환 |
| 적합한 경우 | 전통적 웹 앱 | REST API, MSA |

## 2. JWT (JSON Web Token)

### 2.1 JWT 구조

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImV4cCI6MTcxMH0.abc123
 ──────────────────── ────────────────────────────────────────────────────── ──────
       Header                          Payload                            Signature
       (알고리즘)                      (클레임: 사용자 정보)                (서명: 위변조 방지)
```

- **Header**: 알고리즘(HS256) + 타입(JWT)
- **Payload**: 사용자 ID, 역할, 만료 시간 등의 클레임
- **Signature**: Header + Payload를 비밀키로 서명 → 위변조 검증

### 2.2 JWT 인증 흐름

```
1. 로그인
   POST /api/auth/login { "username": "admin", "password": "1234" }
   → 서버: 사용자 검증 → JWT 생성 → 응답: { "token": "eyJ..." }

2. API 호출
   GET /api/reservations
   Header: Authorization: Bearer eyJ...
   → 서버: JWT 검증 → 사용자 정보 추출 → 요청 처리

3. 토큰 만료
   토큰의 exp 클레임 시간이 지남
   → 서버: 401 Unauthorized 반환 → 클라이언트: 재로그인
```

## 3. Spring Security WebFlux

### 3.1 Spring MVC vs WebFlux Security

| 구성요소 | Spring MVC | Spring WebFlux |
|---------|------------|----------------|
| 필터 체인 | `SecurityFilterChain` | `SecurityWebFilterChain` |
| 필터 | `OncePerRequestFilter` | `WebFilter` |
| 인증 매니저 | `AuthenticationManager` | `ReactiveAuthenticationManager` |
| 사용자 서비스 | `UserDetailsService` | `ReactiveUserDetailsService` |
| 설정 | `@EnableWebSecurity` | `@EnableWebFluxSecurity` |
| 컨텍스트 | `SecurityContextHolder` | `ReactiveSecurityContextHolder` |

### 3.2 SecurityWebFilterChain 구성

```kotlin
@Bean
fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
    http
        .csrf { it.disable() }           // REST API는 CSRF 불필요
        .httpBasic { it.disable() }       // Basic Auth 비활성화
        .formLogin { it.disable() }       // 폼 로그인 비활성화
        .authorizeExchange { exchanges ->
            exchanges
                .pathMatchers("/api/auth/**").permitAll()      // 인증 API는 공개
                .pathMatchers("/actuator/**").permitAll()       // 모니터링은 공개
                .pathMatchers(HttpMethod.GET, "/api/**").hasAnyRole("USER", "ADMIN")
                .pathMatchers("/api/simulator/**").hasRole("ADMIN")
                .anyExchange().authenticated()
        }
        .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build()
```

### 3.3 JWT 인증 필터 흐름

```
요청 도착
  ↓
Authorization 헤더 확인
  ├── 없음 → 다음 필터로 전달 (인증 없이)
  └── Bearer 토큰 → JWT 검증
       ├── 유효 → SecurityContext에 인증 정보 저장 → 다음 필터
       └── 무효/만료 → 401 Unauthorized
```

## 4. 비밀번호 암호화

### 4.1 BCrypt

```
원본:     "password123"
BCrypt:   "$2a$10$N9qo8uLOickgx2ZMRZoMye..."

특징:
  - 단방향 해시 (복호화 불가)
  - Salt 내장 (같은 비밀번호도 매번 다른 해시)
  - Cost Factor로 해시 강도 조절 ($2a$10 = 2^10회 반복)
```

- Spring Security의 기본 `PasswordEncoder`
- `BCryptPasswordEncoder().encode("password")` → 해시
- `BCryptPasswordEncoder().matches("password", hash)` → 검증

## 5. 이 프로젝트의 Security 구성

### 5.1 역할 (Role)

| 역할 | 설명 | 접근 가능 API |
|------|------|--------------|
| `USER` | 일반 사용자 | 예약 CRUD, 통계 조회 |
| `ADMIN` | 관리자 | 모든 API + 시뮬레이터 제어 |

### 5.2 엔드포인트 보안 정책

| 경로 | 접근 제어 |
|------|----------|
| `POST /api/auth/register` | 공개 (회원가입) |
| `POST /api/auth/login` | 공개 (로그인) |
| `/actuator/**` | 공개 (모니터링) |
| `/webjars/**`, `/v3/api-docs`, `/swagger-ui/**` | 공개 (API 문서) |
| `/index.html`, `/css/**`, `/js/**`, `/` | 공개 (대시보드) |
| `/api/events/stream` | 공개 (SSE 스트림) |
| `/api/simulator/**` | ADMIN만 |
| `/api/**` (나머지) | USER 또는 ADMIN |

### 5.3 구현 파일

```
domain/
  └── User.kt (.java)               ← 사용자 엔티티
repository/
  └── UserRepository.kt (.java)     ← 사용자 조회
dto/
  ├── AuthRequest.kt (.java)        ← 로그인/회원가입 요청
  └── AuthResponse.kt (.java)       ← JWT 토큰 응답
security/
  ├── JwtUtil.kt (.java)            ← JWT 생성/검증 유틸
  ├── JwtAuthenticationFilter.kt (.java) ← WebFilter (JWT 검증)
  └── SecurityConfig.kt (.java)     ← SecurityWebFilterChain 설정
controller/
  └── AuthController.kt (.java)     ← 로그인/회원가입 API
```
