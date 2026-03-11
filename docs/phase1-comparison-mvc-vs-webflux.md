# Spring MVC vs Spring WebFlux 비교 학습 가이드

이 문서는 기존 Spring MVC 프로젝트 경험을 바탕으로
Spring WebFlux와의 차이점을 구조적으로 학습하기 위한 비교 가이드이다.

---

## 목차
1. [아키텍처 비교](#1-아키텍처-비교)
2. [의존성과 서버 비교](#2-의존성과-서버-비교)
3. [Controller 계층 비교](#3-controller-계층-비교)
4. [Service 계층 비교](#4-service-계층-비교)
5. [Repository 계층 비교 (JDBC vs R2DBC)](#5-repository-계층-비교-jdbc-vs-r2dbc)
6. [에러 처리 비교](#6-에러-처리-비교)
7. [트랜잭션 비교](#7-트랜잭션-비교)
8. [테스트 비교](#8-테스트-비교)
9. [필터와 인터셉터 비교](#9-필터와-인터셉터-비교)
10. [SSE와 스트리밍 비교](#10-sse와-스트리밍-비교)
11. [주의사항: WebFlux에서 절대 하면 안 되는 것](#11-주의사항-webflux에서-절대-하면-안-되는-것)

---

## 1. 아키텍처 비교

### Spring MVC (블로킹)

```
[클라이언트] → [Tomcat] → [DispatcherServlet] → [Controller] → [Service] → [Repository] → [JDBC] → [DB]
                  ↑                                                                           ↑
              스레드 풀                                                                    블로킹 I/O
              (200개)                                                                  (스레드가 대기)
```

- 요청 1개 = 스레드 1개가 전담
- DB 응답을 기다리는 동안 스레드는 블로킹(대기) 상태
- 동시 요청이 스레드 풀 크기를 초과하면 큐에서 대기

### Spring WebFlux (논블로킹)

```
[클라이언트] → [Netty] → [DispatcherHandler] → [Controller] → [Service] → [Repository] → [R2DBC] → [DB]
                 ↑                                                                           ↑
            이벤트 루프                                                                   논블로킹 I/O
           (CPU 코어 수)                                                              (스레드가 반환됨)
```

- 소수의 이벤트 루프 스레드가 모든 요청을 처리
- DB 응답을 기다리는 동안 스레드는 다른 요청 처리
- 스레드 고갈이 발생하지 않음

### 구조 비교표

| 항목 | Spring MVC | Spring WebFlux |
|---|---|---|
| **서버** | Tomcat (Servlet 컨테이너) | Netty (논블로킹 서버) |
| **디스패처** | DispatcherServlet | DispatcherHandler |
| **스레드 모델** | 요청당 1스레드 (스레드 풀) | 이벤트 루프 (소수 스레드) |
| **I/O 방식** | 블로킹 | 논블로킹 |
| **반환 타입** | `T`, `List<T>`, `ResponseEntity<T>` | `Mono<T>`, `Flux<T>` |
| **DB 접근** | JDBC, JPA, MyBatis | R2DBC, Spring Data R2DBC |
| **스트리밍** | 제한적 (DeferredResult 등) | 네이티브 지원 (SSE, WebSocket) |

---

## 2. 의존성과 서버 비교

### build.gradle.kts

```kotlin
// === Spring MVC ===
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")        // Tomcat + Spring MVC
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")   // JPA + Hibernate
    runtimeOnly("org.postgresql:postgresql")                                  // JDBC 드라이버
}

// === Spring WebFlux ===
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")    // Netty + WebFlux
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc") // R2DBC
    runtimeOnly("org.postgresql:r2dbc-postgresql")                            // R2DBC 드라이버
}
```

**주의:** `spring-boot-starter-web`과 `spring-boot-starter-webflux`를 동시에 포함하면
Spring MVC가 우선 적용된다. 반드시 하나만 선택해야 한다.

### 서버 시작 로그 차이

```
# Spring MVC
Tomcat started on port 8080 (http) with context path '/'

# Spring WebFlux
Netty started on port 8080
```

---

## 3. Controller 계층 비교

### 단건 조회

```java
// === Spring MVC: 블로킹 ===
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    @GetMapping("/{id}")
    public Property findById(@PathVariable Long id) {     // 반환 타입: Property (블로킹)
        return propertyService.findById(id);               // 스레드가 결과 올 때까지 대기
    }
}

// === Spring WebFlux: 논블로킹 ===
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    @GetMapping("/{id}")
    public Mono<Property> findById(@PathVariable Long id) {  // 반환 타입: Mono<Property> (논블로킹)
        return propertyService.findById(id);                  // Mono를 반환, WebFlux가 구독 처리
    }
}
```

**차이점:**
- MVC: `Property` → 메서드가 끝날 때 값이 이미 준비되어 있음
- WebFlux: `Mono<Property>` → "나중에 Property 1개를 줄게"라는 약속을 반환

### 목록 조회

```java
// === Spring MVC ===
@GetMapping
public List<Property> findAll() {         // List<Property>: 모든 데이터가 메모리에 로드
    return propertyService.findAll();
}

// === Spring WebFlux ===
@GetMapping
public Flux<Property> findAll() {         // Flux<Property>: 데이터를 하나씩 스트리밍
    return propertyService.findAll();
}
```

**차이점:**
- MVC `List`: 1000건이면 1000건 모두 메모리에 올린 후 응답
- WebFlux `Flux`: 1000건을 하나씩 스트리밍으로 응답 (메모리 효율적)

### 생성

```java
// === Spring MVC ===
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Property create(@RequestBody CreatePropertyRequest request) {
    return propertyService.create(request);    // 동기 호출
}

// === Spring WebFlux ===
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Mono<Property> create(@RequestBody CreatePropertyRequest request) {
    return propertyService.create(request);    // 비동기 호출 (Mono 반환)
}
```

### ResponseEntity 사용

```java
// === Spring MVC ===
@GetMapping("/{id}")
public ResponseEntity<Property> findById(@PathVariable Long id) {
    Property property = propertyService.findById(id);
    if (property == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(property);
}

// === Spring WebFlux ===
@GetMapping("/{id}")
public Mono<ResponseEntity<Property>> findById(@PathVariable Long id) {
    return propertyService.findById(id)
        .map(property -> ResponseEntity.ok(property))                    // 값이 있으면 200 OK
        .defaultIfEmpty(ResponseEntity.notFound().build());        // 값이 없으면 404
}
```

---

## 4. Service 계층 비교

### 단순 조회

```java
// === Spring MVC ===
@Service
public class PropertyService {
    private final PropertyRepository propertyRepository;

    public Property findById(Long id) {
        return propertyRepository.findById(id)         // Optional<Property> 반환 (블로킹)
            .orElseThrow(() -> new NotFoundException("Property not found"));
    }

    public List<Property> findAll() {
        return propertyRepository.findAll();            // List<Property> 반환 (블로킹)
    }
}

// === Spring WebFlux ===
@Service
public class PropertyService {
    private final PropertyRepository propertyRepository;

    public Mono<Property> findById(Long id) {
        return propertyRepository.findById(id)          // Mono<Property> 반환 (논블로킹)
            .switchIfEmpty(Mono.error(new NotFoundException("Property not found")));
    }

    public Flux<Property> findAll() {
        return propertyRepository.findAll();             // Flux<Property> 반환 (논블로킹)
    }
}
```

### 복합 로직 (여러 DB 호출 조합)

```java
// === Spring MVC: 순차 블로킹 호출 ===
public PropertyDetailDto getPropertyDetail(Long propertyId) {
    Property property = propertyRepository.findById(propertyId)
        .orElseThrow(() -> new NotFoundException("Property not found"));

    List<RoomType> roomTypes = roomTypeRepository.findByPropertyId(propertyId);  // 블로킹
    List<Channel> channels = channelRepository.findAll();                   // 블로킹 (순차)

    return new PropertyDetailDto(property, roomTypes, channels);
}

// === Spring WebFlux: 비동기 병렬 호출 ===
public Mono<PropertyDetailDto> getPropertyDetail(Long propertyId) {
    Mono<Property> propertyMono = propertyRepository.findById(propertyId)
        .switchIfEmpty(Mono.error(new NotFoundException("Property not found")));

    Mono<List<RoomType>> roomTypesMono = roomTypeRepository
        .findByPropertyId(propertyId).collectList();                             // 논블로킹
    Mono<List<Channel>> channelsMono = channelRepository
        .findAll().collectList();                                           // 논블로킹

    // Mono.zip: 세 개의 Mono를 병렬로 실행하고, 모두 완료되면 결합
    return Mono.zip(propertyMono, roomTypesMono, channelsMono)
        .map(tuple -> new PropertyDetailDto(
            tuple.getT1(),      // Property
            tuple.getT2(),      // List<RoomType>
            tuple.getT3()       // List<Channel>
        ));
}
```

**핵심 차이:**
- MVC: `roomTypes` 조회가 끝나야 `channels` 조회 시작 (순차)
- WebFlux: `Mono.zip()`으로 세 쿼리를 **동시에 실행** (병렬), 모두 완료 후 조합

### 연쇄 DB 호출 (이전 결과가 다음 호출에 필요한 경우)

```java
// === Spring MVC: 자연스러운 순차 코드 ===
public Reservation createReservation(CreateReservationRequest request) {
    RoomType roomType = roomTypeRepository.findById(request.getRoomTypeId())
        .orElseThrow();                                   // 1. 객실 타입 조회 (블로킹)

    Inventory inventory = inventoryRepository
        .findByRoomTypeIdAndStockDate(roomType.getId(), request.getCheckInDate())
        .orElseThrow();                                   // 2. 재고 조회 (블로킹)

    inventory.setAvailableQuantity(
        inventory.getAvailableQuantity() - request.getQuantity());
    inventoryRepository.save(inventory);                   // 3. 재고 차감 (블로킹)

    return reservationRepository.save(new Reservation(/* ... */));  // 4. 예약 저장 (블로킹)
}

// === Spring WebFlux: flatMap 체인 ===
public Mono<Reservation> createReservation(CreateReservationRequest request) {
    return roomTypeRepository.findById(request.getRoomTypeId())           // 1. 객실 타입 조회
        .switchIfEmpty(Mono.error(new NotFoundException("Room type not found")))
        .flatMap(roomType ->                                               // 2. 재고 조회
            inventoryRepository.findByRoomTypeIdAndStockDate(
                roomType.getId(), request.getCheckInDate())
        )
        .switchIfEmpty(Mono.error(new NotFoundException("Inventory not found")))
        .flatMap(inventory -> {                                            // 3. 재고 차감
            inventory.setAvailableQuantity(
                inventory.getAvailableQuantity() - request.getQuantity());
            return inventoryRepository.save(inventory);
        })
        .flatMap(savedInventory ->                                         // 4. 예약 저장
            reservationRepository.save(new Reservation(/* ... */))
        );
}
```

**핵심 차이:**
- MVC: 변수에 할당하며 순차적으로 진행 → 직관적
- WebFlux: `flatMap` 체인으로 이전 결과를 다음 단계에 전달 → 논블로킹이지만 코드가 길어짐

---

## 5. Repository 계층 비교 (JDBC vs R2DBC)

### Repository 인터페이스

```java
// === Spring MVC + JPA ===
public interface PropertyRepository extends JpaRepository<Property, Long> {
    // 반환 타입: Optional<Property>, List<Property>
    Optional<Property> findByName(String name);
    List<Property> findByAddressContaining(String keyword);
}

// === Spring WebFlux + R2DBC ===
public interface PropertyRepository extends ReactiveCrudRepository<Property, Long> {
    // 반환 타입: Mono<Property>, Flux<Property>
    Mono<Property> findByName(String name);
    Flux<Property> findByAddressContaining(String keyword);
}
```

### 기본 제공 메서드 비교

| 기능 | JpaRepository | ReactiveCrudRepository |
|---|---|---|
| 단건 조회 | `Optional<T> findById(ID)` | `Mono<T> findById(ID)` |
| 전체 조회 | `List<T> findAll()` | `Flux<T> findAll()` |
| 저장 | `T save(T)` | `Mono<T> save(T)` |
| 삭제 | `void deleteById(ID)` | `Mono<Void> deleteById(ID)` |
| 존재 확인 | `boolean existsById(ID)` | `Mono<Boolean> existsById(ID)` |
| 건수 | `long count()` | `Mono<Long> count()` |

### 엔티티 어노테이션 비교

```java
// === JPA 엔티티 ===
@Entity                           // JPA 전용
@Table(name = "properties")           // javax.persistence.Table
public class Property {
    @Id                           // javax.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 자동 증가 전략
    private Long id;

    @Column(nullable = false, length = 200)  // 컬럼 상세 설정
    private String name;

    @OneToMany(mappedBy = "property", fetch = FetchType.LAZY)  // 연관관계
    private List<RoomType> roomTypes;

    @CreatedDate
    private LocalDateTime createdAt;
}

// === R2DBC 엔티티 ===
@Table("properties")                   // org.springframework.data.relational.core.mapping.Table
public class Property {
    @Id                            // org.springframework.data.annotation.Id
    private Long id;               // BIGSERIAL이면 자동 증가 (별도 어노테이션 불필요)

    private String name;           // 컬럼 상세 설정 어노테이션 없음

    // @OneToMany 없음!            // 연관관계 매핑 불가
    // private Long propertyId;       // 대신 FK ID를 직접 저장

    @CreatedDate
    private LocalDateTime createdAt;
}
```

### JPA vs R2DBC 기능 비교

| 기능 | JPA | R2DBC |
|---|---|---|
| **연관관계 매핑** | `@OneToMany`, `@ManyToOne` 등 | 없음 → FK ID 직접 저장 |
| **Lazy Loading** | 지원 (프록시 기반) | 없음 → 명시적 쿼리 |
| **1차 캐시** | 영속성 컨텍스트 | 없음 |
| **2차 캐시** | EhCache, Redis 등 | 없음 |
| **Dirty Checking** | 자동 (변경 감지) | 없음 → 명시적 save() |
| **DDL 자동 생성** | `ddl-auto` 지원 | 없음 → Flyway 필수 |
| **JPQL** | 지원 | 없음 → Native SQL |
| **QueryDSL** | 지원 | 미지원 |
| **@Query** | JPQL 또는 Native SQL | Native SQL만 |
| **스키마 관리** | Hibernate가 관리 가능 | Flyway/Liquibase 필수 |
| **트랜잭션** | `@Transactional` (블로킹) | `@Transactional` (Reactive 버전) |

---

## 6. 에러 처리 비교

### 예외 발생

```java
// === Spring MVC: 일반 예외 던지기 ===
public Property findById(Long id) {
    return propertyRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Property not found: " + id));
    // 예외가 즉시 발생하고, 콜 스택을 타고 올라감
}

// === Spring WebFlux: Reactive 예외 ===
public Mono<Property> findById(Long id) {
    return propertyRepository.findById(id)
        .switchIfEmpty(Mono.error(new NotFoundException("Property not found: " + id)));
    // Mono.error(): 구독 시점에 에러 시그널을 발행
    // switchIfEmpty(): Mono가 비어있을 때 대체 Mono를 제공
}
```

### 글로벌 에러 처리

```java
// === Spring MVC: @ControllerAdvice ===
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException e) {
        return new ErrorResponse(e.getMessage());       // 동기적으로 처리
    }
}

// === Spring WebFlux: @ControllerAdvice (동일하게 사용 가능) ===
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ErrorResponse> handleNotFound(NotFoundException e) {
        return Mono.just(new ErrorResponse(e.getMessage()));  // Mono로 감싸서 반환
    }
}
```

### Reactive 체인 내 에러 처리 (WebFlux 전용)

```java
// === WebFlux: Reactive 연산자로 에러 처리 ===
public Mono<Property> findById(Long id) {
    return propertyRepository.findById(id)
        // 에러 시 대체값 반환
        .onErrorReturn(Property.builder().name("기본 숙소").build())

        // 에러 시 대체 Publisher로 전환
        .onErrorResume(e -> {
            log.error("DB 조회 실패: {}", e.getMessage());
            return Mono.empty();
        })

        // 에러를 다른 에러로 변환
        .onErrorMap(DataAccessException.class,
            e -> new ServiceException("DB 오류", e));
}
```

---

## 7. 트랜잭션 비교

```java
// === Spring MVC: 블로킹 트랜잭션 ===
@Service
@Transactional   // org.springframework.transaction.annotation.Transactional
public class ReservationService {

    public Reservation createReservation(CreateReservationRequest request) {
        // 이 메서드 내의 모든 DB 작업이 하나의 트랜잭션으로 묶인다
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
            .orElseThrow();
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - 1);
        inventoryRepository.save(inventory);

        Reservation reservation = new Reservation(/* ... */);
        return reservationRepository.save(reservation);
        // 메서드 정상 종료 → 커밋
        // 예외 발생 → 롤백
    }
}

// === Spring WebFlux: Reactive 트랜잭션 ===
@Service
public class ReservationService {

    private final TransactionalOperator transactionalOperator;  // Reactive 트랜잭션 관리자

    @Transactional   // 같은 어노테이션이지만 ReactiveTransactionManager가 처리
    public Mono<Reservation> createReservation(CreateReservationRequest request) {
        return inventoryRepository.findById(request.getInventoryId())
            .switchIfEmpty(Mono.error(new NotFoundException("Inventory not found")))
            .flatMap(inventory -> {
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() - 1);
                return inventoryRepository.save(inventory);
            })
            .flatMap(inv -> {
                Reservation reservation = new Reservation(/* ... */);
                return reservationRepository.save(reservation);
            });
        // Reactive 체인이 정상 완료 → 커밋
        // Mono.error() 시그널 발생 → 롤백
    }

    // 프로그래밍 방식 트랜잭션 (어노테이션 대신)
    public Mono<Reservation> createReservationProgrammatic(CreateReservationRequest request) {
        return transactionalOperator.transactional(
            inventoryRepository.findById(request.getInventoryId())
                .flatMap(/* ... */)
        );
    }
}
```

---

## 8. 테스트 비교

### 단위 테스트

```java
// === Spring MVC: 일반 JUnit 테스트 ===
@Test
void shouldFindPropertyById() {
    when(propertyRepository.findById(1L)).thenReturn(Optional.of(property));

    Property result = propertyService.findById(1L);   // 동기 호출

    assertThat(result.getName()).isEqualTo("서울 호텔");
}

// === Spring WebFlux: StepVerifier 사용 ===
@Test
void shouldFindPropertyById() {
    when(propertyRepository.findById(1L)).thenReturn(Mono.just(property));

    Mono<Property> result = propertyService.findById(1L);  // Mono 반환 (아직 실행되지 않음)

    StepVerifier.create(result)           // StepVerifier가 구독하여 실행
        .expectNextMatches(h -> h.getName().equals("서울 호텔"))  // 값 검증
        .verifyComplete();                // 정상 완료 확인
}
```

### WebTestClient (WebFlux 전용)

```java
// === Spring MVC: MockMvc ===
@Test
void shouldGetProperty() throws Exception {
    mockMvc.perform(get("/api/properties/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("서울 호텔"));
}

// === Spring WebFlux: WebTestClient ===
@Test
void shouldGetProperty() {
    webTestClient.get().uri("/api/properties/1")
        .exchange()                                    // 요청 실행
        .expectStatus().isOk()
        .expectBody(Property.class)
        .value(property -> assertThat(property.getName()).isEqualTo("서울 호텔"));
}
```

---

## 9. 필터와 인터셉터 비교

```java
// === Spring MVC: HandlerInterceptor ===
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) {
        log.info("Request: {} {}", request.getMethod(), request.getRequestURI());
        return true;   // true: 계속 진행, false: 요청 중단
    }
}

// === Spring WebFlux: WebFilter ===
@Component
public class LoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        log.info("Request: {} {}", request.getMethod(), request.getPath());
        return chain.filter(exchange);   // 다음 필터로 전달 (Reactive 체인)
    }
}
```

| 항목 | Spring MVC | Spring WebFlux |
|---|---|---|
| **인터페이스** | `HandlerInterceptor` | `WebFilter` |
| **요청 객체** | `HttpServletRequest` | `ServerHttpRequest` |
| **응답 객체** | `HttpServletResponse` | `ServerHttpResponse` |
| **반환 타입** | `boolean` | `Mono<Void>` |

---

## 10. SSE와 스트리밍 비교

이 프로젝트의 핵심 기능인 **실시간 이벤트 스트리밍**에서 가장 큰 차이가 드러난다.

```java
// === Spring MVC: SSE (제한적 지원) ===
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamEvents() {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);  // 타임아웃 설정

    // 별도 스레드에서 이벤트를 전송해야 한다 (스레드 점유)
    executor.execute(() -> {
        try {
            while (true) {
                ChannelEvent event = eventQueue.take();    // 블로킹 대기
                emitter.send(event);
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    return emitter;
}

// === Spring WebFlux: SSE (네이티브 지원) ===
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ChannelEvent>> streamEvents() {
    return eventSink.asFlux()                              // Sinks → Flux 변환
        .map(event -> ServerSentEvent.<ChannelEvent>builder()
            .id(String.valueOf(event.getId()))
            .event(event.getEventType())
            .data(event)
            .build()
        );
    // 별도 스레드 불필요, Flux가 이벤트를 자동으로 스트리밍
    // 클라이언트가 연결을 끊으면 자동으로 구독 해제
}
```

**핵심 차이:**
- MVC: `SseEmitter` + 별도 스레드로 구현 → 스레드를 점유하며 복잡
- WebFlux: `Flux<ServerSentEvent>` 반환만 하면 끝 → 논블로킹으로 자연스러움
- **이 프로젝트에서 WebFlux를 선택한 가장 큰 이유**

---

## 11. 주의사항: WebFlux에서 절대 하면 안 되는 것

### 이벤트 루프 스레드를 블로킹하면 안 된다

WebFlux의 이벤트 루프 스레드는 **절대 블로킹하면 안 된다.**
블로킹하면 전체 시스템의 처리량이 급격히 떨어진다.

```java
// ❌ 절대 하면 안 되는 것들

// 1. Thread.sleep() 사용
Thread.sleep(1000);  // 이벤트 루프 스레드를 1초간 블로킹!

// 2. 블로킹 I/O 호출
InputStream is = new FileInputStream("file.txt");  // 블로킹 파일 I/O!

// 3. JDBC 호출 (블로킹)
Connection conn = DriverManager.getConnection(url);  // 블로킹!
ResultSet rs = stmt.executeQuery("SELECT * FROM properties");  // 블로킹!

// 4. .block() 호출 (Mono/Flux를 블로킹으로 변환)
Property property = propertyRepository.findById(1L).block();  // 논블로킹을 블로킹으로!

// 5. synchronized 블록
synchronized (this) { /* ... */ }  // 스레드를 잠금!


// ✅ 올바른 대안

// 1. Thread.sleep() → Mono.delay()
Mono.delay(Duration.ofSeconds(1)).then(/* ... */);

// 2. 파일 I/O → AsynchronousFileChannel 또는 Spring Resource
DataBufferUtils.read(resource, new DefaultDataBufferFactory(), 4096);

// 3. JDBC → R2DBC
propertyRepository.findById(1L);  // R2DBC는 논블로킹

// 4. .block() → flatMap/map으로 체인 유지
propertyRepository.findById(1L).flatMap(property -> /* ... */);

// 5. synchronized → Sinks 또는 AtomicReference
Sinks.Many<Event> sink = Sinks.many().multicast().onBackpressureBuffer();
```

### 블로킹 라이브러리를 꼭 써야 한다면

```java
// 블로킹 코드를 별도 스레드 풀에서 실행
Mono<String> result = Mono.fromCallable(() -> {
        return blockingLibrary.call();             // 블로킹 호출
    })
    .subscribeOn(Schedulers.boundedElastic());     // 전용 스레드 풀에서 실행
    // boundedElastic: 블로킹 작업 전용 스케줄러 (이벤트 루프를 보호)
```
