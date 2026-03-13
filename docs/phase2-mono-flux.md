# Phase 2: Mono/Flux 연산과 WebFlux REST API

## 목차
1. [Mono/Flux 복습 — 왜 다시 살펴보는가?](#1-monoflux-복습--왜-다시-살펴보는가)
2. [Mono 핵심 연산자](#2-mono-핵심-연산자)
3. [Flux 핵심 연산자](#3-flux-핵심-연산자)
4. [에러 처리 전략](#4-에러-처리-전략)
5. [WebFlux REST API 구조](#5-webflux-rest-api-구조)
6. [Router Function vs @Controller 비교](#6-router-function-vs-controller-비교)
7. [Service 계층 — Reactive 비즈니스 로직 작성법](#7-service-계층--reactive-비즈니스-로직-작성법)
8. [DTO와 요청/응답 설계](#8-dto와-요청응답-설계)
9. [R2DBC 트랜잭션 처리](#9-r2dbc-트랜잭션-처리)
10. [인벤토리 관리 API 설계](#10-인벤토리-관리-api-설계)
11. [테스트 — WebTestClient 사용법](#11-테스트--webtestclient-사용법)
12. [Kotlin vs Java 비교 — Reactive 코드 스타일](#12-kotlin-vs-java-비교--reactive-코드-스타일)

---

## 1. Mono/Flux 복습 — 왜 다시 살펴보는가?

Phase 1에서 Mono와 Flux의 기본 개념을 배웠다.

```
Mono<T>  →  0 또는 1개의 데이터를 비동기적으로 발행
Flux<T>  →  0 ~ N개의 데이터를 비동기적으로 발행
```

하지만 Phase 1에서는 Repository의 반환 타입으로만 사용했다. Phase 2에서는 **실전 API를 구현**하므로, 연산자(operator)를 조합해서 비즈니스 로직을 만들어야 한다.

### 왜 연산자가 필요한가?

전통적인 Spring MVC에서는 이렇게 작성한다:

```java
// MVC 방식 (블로킹)
Inventory inventory = inventoryRepository.findById(id);  // 블로킹 호출
if (inventory == null) {
    throw new NotFoundException("재고를 찾을 수 없습니다");
}
inventory.setAvailableQuantity(inventory.getAvailableQuantity() - 1);
return inventoryRepository.save(inventory);  // 블로킹 호출
```

WebFlux에서는 **데이터가 나중에 도착**하므로, 직접 꺼내서 조작할 수 없다. 대신 **연산자 체인**으로 "데이터가 도착하면 이렇게 처리해라"라는 파이프라인을 만든다:

```java
// WebFlux 방식 (논블로킹)
return inventoryRepository.findById(id)           // Mono<Inventory> 반환
    .switchIfEmpty(Mono.error(new NotFoundException("재고를 찾을 수 없습니다")))
    .map(inventory -> {                            // 데이터가 도착하면 변환
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - 1);
        return inventory;
    })
    .flatMap(inventoryRepository::save);           // 변환 결과를 DB에 저장
```

**핵심:** Reactive에서는 데이터를 직접 꺼내지 않고, 연산자 체인으로 처리 흐름을 선언한다.

### Lazy vs Eager

Reactive 스트림은 **Lazy(게으른)** 실행이다.

```java
Mono<Inventory> mono = inventoryRepository.findById(1L);  // ← 아직 DB 조회 안 함!
// 누군가 subscribe() 해야 실제로 실행됨
```

Spring WebFlux에서는 Controller가 반환한 Mono/Flux를 **프레임워크가 자동으로 subscribe()** 해준다. 그래서 개발자는 subscribe()를 직접 호출할 필요가 없다.

```
[Controller 반환] → Mono<ResponseEntity> → [WebFlux 프레임워크가 subscribe()] → [실행 시작]
```

> **주의:** 비즈니스 로직에서 `.subscribe()`를 직접 호출하면 안 된다. 제어 흐름이 분리되어 에러 처리와 백프레셔 관리가 깨진다.

---

## 2. Mono 핵심 연산자

Mono는 **0 또는 1개의 값**을 다루는 Publisher다. API에서 "단건 조회", "단건 생성", "단건 수정" 등에 사용한다.

### 2-1. 생성 연산자

| 연산자 | 설명 | 예시 |
|--------|------|------|
| `Mono.just(value)` | 즉시 값을 발행 | `Mono.just("hello")` |
| `Mono.empty()` | 값 없이 완료 | 조회 결과 없을 때 |
| `Mono.error(e)` | 에러 시그널 발행 | `Mono.error(new RuntimeException())` |
| `Mono.defer(() -> ...)` | 구독 시점에 Mono 생성 | lazy 초기화에 유용 |
| `Mono.fromCallable(() -> ...)` | Callable 결과를 Mono로 감쌈 | 블로킹 코드 래핑 |

#### Mono.just vs Mono.defer

```java
// Mono.just — 즉시 평가 (eager)
Mono.just(System.currentTimeMillis());
// → Mono 생성 시점의 시간이 고정됨. 구독할 때마다 같은 값.

// Mono.defer — 지연 평가 (lazy)
Mono.defer(() -> Mono.just(System.currentTimeMillis()));
// → 구독할 때마다 새로운 시간을 계산함.
```

**언제 defer를 쓰는가?**
- 구독 시점마다 다른 값이 필요할 때
- 조건에 따라 다른 Mono를 반환해야 할 때

```java
// 실전 예시: 조건부 로직
Mono.defer(() -> {
    if (condition) {
        return inventoryRepository.findById(id);
    } else {
        return Mono.empty();
    }
});
```

### 2-2. 변환 연산자

#### map — 동기적 값 변환

`map`은 Mono 안의 값을 **동기적으로** 변환한다. 변환 함수가 일반 값을 반환할 때 사용한다.

```java
Mono<Inventory> inventoryMono = inventoryRepository.findById(1L);

// map: Inventory → InventoryResponse (단순 변환, DB 호출 없음)
Mono<InventoryResponse> responseMono = inventoryMono
    .map(inventory -> new InventoryResponse(
        inventory.getId(),
        inventory.getStockDate(),
        inventory.getAvailableQuantity()
    ));
```

```
[Inventory] --map--> [InventoryResponse]
     ↑ 입력               ↑ 출력
     (1개)                 (1개)
```

#### flatMap — 비동기적 값 변환

`flatMap`은 Mono 안의 값을 **또 다른 Mono/Flux로** 변환한다. 변환 과정에서 비동기 작업(DB 조회, 외부 API 호출 등)이 필요할 때 사용한다.

```java
// flatMap: roomTypeId로 RoomType 조회 → 비동기 DB 호출이므로 flatMap
Mono<RoomType> roomTypeMono = inventoryRepository.findById(1L)
    .flatMap(inventory -> roomTypeRepository.findById(inventory.getRoomTypeId()));
```

```
[Inventory] --flatMap--> [Mono<RoomType>] --구독--> [RoomType]
     ↑ 입력                    ↑ 중간 결과              ↑ 최종 출력
```

#### map vs flatMap — 핵심 차이

| | map | flatMap |
|---|---|---|
| **반환 타입** | 일반 값 (`T → R`) | Mono/Flux (`T → Mono<R>`) |
| **용도** | 단순 변환 (DTO 변환 등) | 비동기 작업 체이닝 (DB 조회 등) |
| **결과** | `Mono<R>` | `Mono<R>` (내부 Mono를 펼침) |

```java
// map을 써야 하는 곳에 flatMap을 쓰면?
// → 동작은 하지만 불필요한 오버헤드. map이 더 효율적.

// flatMap을 써야 하는 곳에 map을 쓰면?
// → Mono<Mono<T>> 이중 래핑이 되어 의도대로 동작하지 않음.
```

**판단 기준:** 변환 함수의 반환값이 `Mono`나 `Flux`면 → `flatMap`, 아니면 → `map`

### 2-3. 조건 / 기본값 연산자

#### switchIfEmpty — 빈 Mono 대체

Mono가 비어있을 때(값이 없을 때) 대체 Mono를 실행한다. **존재하지 않는 리소스에 대한 에러 처리**에 자주 사용한다.

```java
// 재고를 찾지 못하면 404 에러 발행
inventoryRepository.findById(id)
    .switchIfEmpty(Mono.error(new NotFoundException("재고를 찾을 수 없습니다")));
```

#### defaultIfEmpty — 빈 Mono에 기본값

Mono가 비어있을 때 기본값을 반환한다.

```java
// 설정값이 없으면 기본값 사용
configRepository.findByKey("maxRetry")
    .defaultIfEmpty(new Config("maxRetry", "3"));
```

#### switchIfEmpty vs defaultIfEmpty

| | switchIfEmpty | defaultIfEmpty |
|---|---|---|
| **인자** | `Mono<T>` (다른 Reactive 체인) | `T` (고정 값) |
| **용도** | 대체 로직 실행, 에러 발행 | 단순 기본값 |
| **Lazy** | O (구독 시 실행) | X (즉시 생성) |

### 2-4. 필터 연산자

#### filter — 조건에 맞지 않으면 empty

```java
// 활성화된 채널만 통과
channelRepository.findById(channelId)
    .filter(Channel::getIsActive)  // isActive가 false면 Mono.empty()가 됨
    .switchIfEmpty(Mono.error(new BadRequestException("비활성화된 채널입니다")));
```

### 2-5. 부수 효과 연산자

데이터를 변환하지 않고, **부수 효과**(로깅, 모니터링 등)만 수행한다.

```java
inventoryRepository.findById(id)
    .doOnNext(inv -> log.info("재고 조회: {}", inv))      // 값이 도착했을 때
    .doOnError(e -> log.error("조회 실패: {}", e))         // 에러 발생 시
    .doOnSuccess(inv -> log.info("처리 완료"))             // 성공 완료 시 (null일 수 있음)
    .doFinally(signal -> log.info("종료: {}", signal));    // 항상 실행 (finally와 유사)
```

---

## 3. Flux 핵심 연산자

Flux는 **0 ~ N개의 값**을 다루는 Publisher다. API에서 "목록 조회", "기간별 재고 조회" 등에 사용한다.

### 3-1. 생성 연산자

| 연산자 | 설명 | 예시 |
|--------|------|------|
| `Flux.just(a, b, c)` | 고정된 값들을 발행 | `Flux.just(1, 2, 3)` |
| `Flux.fromIterable(list)` | 컬렉션을 Flux로 변환 | `Flux.fromIterable(Arrays.asList(1, 2, 3))` |
| `Flux.empty()` | 값 없이 완료 | 빈 목록 반환 |
| `Flux.range(start, count)` | 정수 범위 발행 | `Flux.range(1, 10)` → 1~10 |
| `Flux.interval(duration)` | 주기적으로 0, 1, 2, ... 발행 | Phase 3에서 사용 예정 |

### 3-2. 변환 연산자

#### map — 각 요소를 동기적으로 변환

```java
// 모든 Inventory를 InventoryResponse로 변환
Flux<InventoryResponse> responses = inventoryRepository
    .findByRoomTypeIdAndStockDateBetween(roomTypeId, startDate, endDate)
    .map(inventory -> new InventoryResponse(
        inventory.getId(),
        inventory.getStockDate(),
        inventory.getTotalQuantity(),
        inventory.getAvailableQuantity()
    ));
```

```
[Inv1] [Inv2] [Inv3]  --map-->  [Resp1] [Resp2] [Resp3]
```

#### flatMap — 각 요소를 비동기적으로 변환 (순서 보장 안 됨)

```java
// 각 RoomType에 대해 Inventory 목록을 조회 (비동기, 순서 보장 X)
Flux<Inventory> inventories = roomTypeRepository.findByPropertyId(propertyId)
    .flatMap(roomType -> inventoryRepository
        .findByRoomTypeIdAndStockDateBetween(roomType.getId(), startDate, endDate));
```

```
[RT1] [RT2] [RT3]  --flatMap-->  [Inv2-1] [Inv1-1] [Inv3-1] [Inv1-2] ...
                                    ↑ 순서가 섞일 수 있음 (동시 실행)
```

#### concatMap — 각 요소를 비동기적으로 변환 (순서 보장)

```java
// flatMap과 같지만 순서를 보장해야 할 때
Flux<Inventory> inventories = roomTypeRepository.findByPropertyId(propertyId)
    .concatMap(roomType -> inventoryRepository
        .findByRoomTypeIdAndStockDateBetween(roomType.getId(), startDate, endDate));
```

```
[RT1] [RT2] [RT3]  --concatMap-->  [Inv1-1] [Inv1-2] [Inv2-1] [Inv3-1] ...
                                      ↑ 순서 보장 (직렬 실행)
```

#### flatMap vs concatMap

| | flatMap | concatMap |
|---|---|---|
| **실행 방식** | 동시(concurrent) | 순차(sequential) |
| **순서 보장** | X | O |
| **성능** | 빠름 (병렬 처리) | 느림 (하나씩 처리) |
| **사용 시점** | 순서 무관, 성능 중요 | 순서 중요 |

### 3-3. 필터 / 제한 연산자

```java
// filter — 조건에 맞는 요소만 통과
inventoryFlux.filter(inv -> inv.getAvailableQuantity() > 0);
// → 예약 가능한 재고만

// take — 처음 N개만
inventoryFlux.take(5);

// skip — 처음 N개 건너뜀
inventoryFlux.skip(10);

// distinct — 중복 제거
fluxOfCodes.distinct();
```

### 3-4. 집계 연산자

```java
// count — 요소 개수 (Mono<Long> 반환)
inventoryFlux.count();

// reduce — 누적 연산 (Mono<T> 반환)
inventoryFlux
    .map(Inventory::getAvailableQuantity)
    .reduce(0, Integer::sum);
// → 전체 가용 객실 수 합계

// collectList — Flux를 List로 수집 (Mono<List<T>> 반환)
inventoryFlux.collectList();
// → Mono<List<Inventory>>

// collectMap — Flux를 Map으로 수집
inventoryFlux.collectMap(Inventory::getStockDate, inv -> inv);
// → Mono<Map<LocalDate, Inventory>>
```

### 3-5. 결합 연산자

```java
// zip — 두 Flux의 요소를 1:1로 결합
Flux.zip(fluxA, fluxB, (a, b) -> a + ": " + b);
// [A1, A2, A3] + [B1, B2, B3] → [A1:B1, A2:B2, A3:B3]

// merge — 여러 Flux를 동시에 구독, 도착 순으로 발행
Flux.merge(fluxA, fluxB);
// [A1] [B1] [A2] [B2] ... (도착 순)

// concat — 여러 Flux를 순서대로 연결
Flux.concat(fluxA, fluxB);
// [A1, A2, A3, B1, B2, B3] (A가 끝나면 B 시작)
```

---

## 4. 에러 처리 전략

Reactive 스트림에서 에러가 발생하면, 스트림이 **즉시 종료**된다. 에러를 적절히 처리하지 않으면 클라이언트에 500 에러가 전달된다.

### 4-1. onErrorReturn — 기본값으로 대체

```java
// 에러 발생 시 기본값 반환
inventoryRepository.findById(id)
    .onErrorReturn(new Inventory());  // 빈 Inventory 반환
```

> **주의:** 모든 에러를 삼켜버리므로 주의해서 사용한다. 에러 종류를 한정할 수 있다:
> ```java
> .onErrorReturn(TimeoutException.class, defaultValue)
> ```

### 4-2. onErrorResume — 대체 로직 실행

```java
// 에러 종류에 따라 다른 처리
inventoryRepository.findById(id)
    .onErrorResume(NotFoundException.class, e -> Mono.empty())
    .onErrorResume(DatabaseException.class, e -> {
        log.error("DB 에러: {}", e.getMessage());
        return Mono.error(new ServiceUnavailableException("잠시 후 다시 시도해주세요"));
    });
```

### 4-3. onErrorMap — 에러 변환

```java
// 저수준 에러를 비즈니스 에러로 변환
inventoryRepository.save(inventory)
    .onErrorMap(DataIntegrityViolationException.class,
        e -> new ConflictException("해당 날짜에 이미 재고가 존재합니다"));
```

### 4-4. 전역 에러 처리 — @ControllerAdvice

WebFlux에서도 Spring MVC와 동일하게 `@ControllerAdvice`를 사용할 수 있다.

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(e.getMessage()));
    }
}
```

### 에러 처리 전략 정리

```
에러 종류             │ 처리 방법                │ 설명
────────────────────┼───────────────────────┼──────────────────────
리소스 미발견         │ switchIfEmpty + error  │ 404 Not Found
유효성 검증 실패       │ Mono.error             │ 400 Bad Request
중복 데이터           │ onErrorMap             │ 409 Conflict
서버 내부 에러         │ @ControllerAdvice      │ 500 Internal Error
```

---

## 5. WebFlux REST API 구조

### 5-1. 전체 흐름

Spring WebFlux에서 HTTP 요청이 처리되는 흐름은 다음과 같다:

```
[HTTP 요청]
    ↓
[DispatcherHandler]  ← Spring MVC의 DispatcherServlet 대신
    ↓
[HandlerMapping]     ← URL → 핸들러 매핑 (@RequestMapping 또는 RouterFunction)
    ↓
[Controller/Handler] ← 비즈니스 로직 호출
    ↓
[Service]            ← Mono/Flux 체인으로 로직 구현
    ↓
[Repository]         ← R2DBC로 논블로킹 DB 접근
    ↓
[Mono/Flux 반환]     ← 프레임워크가 subscribe() → 응답 전송
```

### 5-2. @RestController 방식

Phase 2에서는 **@RestController 방식**을 사용한다. Spring MVC와 거의 동일한 구조이므로 학습 부담이 적다.

```java
@RestController                              // REST 컨트롤러 선언
@RequestMapping("/api/inventories")          // 기본 경로 설정
@RequiredArgsConstructor                     // final 필드 생성자 주입
public class InventoryController {

    private final InventoryService inventoryService;  // Service 의존성

    @GetMapping("/{id}")                              // GET /api/inventories/{id}
    public Mono<ResponseEntity<InventoryResponse>> getInventory(
            @PathVariable Long id) {                  // 경로 변수
        return inventoryService.getInventory(id)      // Service 호출 (Mono 반환)
            .map(ResponseEntity::ok)                  // 200 OK로 래핑
            .defaultIfEmpty(ResponseEntity.notFound().build());  // 없으면 404
    }

    @GetMapping                                       // GET /api/inventories?roomTypeId=1&startDate=...
    public Flux<InventoryResponse> getInventories(    // Flux → JSON 배열
            @RequestParam Long roomTypeId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return inventoryService.getInventories(roomTypeId, startDate, endDate);
    }

    @PostMapping                                      // POST /api/inventories
    @ResponseStatus(HttpStatus.CREATED)               // 201 Created
    public Mono<InventoryResponse> createInventory(
            @RequestBody InventoryCreateRequest request) {  // 요청 본문
        return inventoryService.createInventory(request);
    }
}
```

**MVC와의 차이점:**
- 반환 타입이 `Mono<T>` 또는 `Flux<T>`
- `Flux<T>`를 반환하면 자동으로 **JSON 배열**로 직렬화됨
- `Mono<ResponseEntity<T>>`를 반환하면 상태 코드를 세밀하게 제어 가능
- 나머지 어노테이션(`@GetMapping`, `@PostMapping`, `@RequestBody` 등)은 MVC와 동일

### 5-3. 반환 타입 가이드

| 상황 | 반환 타입 | 설명 |
|------|----------|------|
| 단건 조회 (항상 존재) | `Mono<T>` | 자동으로 200 OK + JSON |
| 단건 조회 (없을 수 있음) | `Mono<ResponseEntity<T>>` | 조건부 404 처리 가능 |
| 목록 조회 | `Flux<T>` | JSON 배열 반환 |
| 생성 | `Mono<ResponseEntity<T>>` | 201 Created + Location 헤더 |
| 수정 | `Mono<ResponseEntity<T>>` | 200 OK |
| 삭제 | `Mono<ResponseEntity<Void>>` | 204 No Content |

---

## 6. Router Function vs @Controller 비교

WebFlux는 두 가지 프로그래밍 모델을 제공한다.

### 6-1. Annotated Controller (이 프로젝트에서 사용)

```java
@RestController
@RequestMapping("/api/inventories")
public class InventoryController {

    @GetMapping("/{id}")
    public Mono<InventoryResponse> getById(@PathVariable Long id) {
        return service.getById(id);
    }
}
```

**장점:** Spring MVC와 동일한 어노테이션 → 학습 비용 낮음
**단점:** 리플렉션 기반, 라우팅이 어노테이션에 분산

### 6-2. Functional Endpoints (Router Function)

```java
@Configuration
public class InventoryRouter {

    @Bean
    public RouterFunction<ServerResponse> inventoryRoutes(InventoryHandler handler) {
        return RouterFunctions.route()
            .path("/api/inventories", builder -> builder
                .GET("/{id}", handler::getById)
                .GET("", handler::getAll)
                .POST("", handler::create)
            )
            .build();
    }
}
```

**장점:** 라우팅이 한 곳에 집중, 리플렉션 없음, 경량
**단점:** 코드가 많아지고, Spring MVC 경험자에게 생소

### 왜 @Controller를 선택하는가?

이 프로젝트의 목적은 **WebFlux + R2DBC 학습**이다. 라우팅 방식보다 Reactive 연산자와 데이터 흐름을 이해하는 것이 핵심이므로, 익숙한 @Controller 방식으로 진행한다.

---

## 7. Service 계층 — Reactive 비즈니스 로직 작성법

### 7-1. 기본 패턴

Service 계층에서 가장 많이 사용하는 패턴을 정리한다.

#### 패턴 1: 조회 후 변환

```java
// 단건 조회 + DTO 변환
public Mono<InventoryResponse> getInventory(Long id) {
    return inventoryRepository.findById(id)                    // DB 조회
        .switchIfEmpty(Mono.error(new NotFoundException()))    // 없으면 에러
        .map(this::toResponse);                                // DTO 변환 (동기)
}
```

#### 패턴 2: 조회 후 연관 데이터 조합

```java
// Inventory 조회 → RoomType 이름까지 포함한 응답
public Mono<InventoryDetailResponse> getInventoryDetail(Long id) {
    return inventoryRepository.findById(id)
        .switchIfEmpty(Mono.error(new NotFoundException()))
        .flatMap(inventory -> roomTypeRepository.findById(inventory.getRoomTypeId())
            .map(roomType -> new InventoryDetailResponse(
                inventory.getId(),
                roomType.getRoomTypeName(),
                inventory.getStockDate(),
                inventory.getTotalQuantity(),
                inventory.getAvailableQuantity()
            ))
        );
}
```

#### 패턴 3: 유효성 검증 → 생성

```java
// RoomType 존재 확인 → Inventory 생성
public Mono<InventoryResponse> createInventory(InventoryCreateRequest request) {
    return roomTypeRepository.findById(request.getRoomTypeId())            // RoomType 존재?
        .switchIfEmpty(Mono.error(new NotFoundException("객실 타입 없음")))  // 없으면 에러
        .then(Mono.defer(() -> {                                           // 존재하면 생성
            Inventory inventory = new Inventory(
                request.getRoomTypeId(),
                request.getStockDate(),
                request.getTotalQuantity(),
                request.getTotalQuantity()    // 최초 생성 시 available = total
            );
            return inventoryRepository.save(inventory);
        }))
        .map(this::toResponse);                                            // DTO 변환
}
```

#### 패턴 4: 조회 → 수정 → 저장

```java
// 재고 수량 업데이트
public Mono<InventoryResponse> updateAvailability(Long id, int quantity) {
    return inventoryRepository.findById(id)
        .switchIfEmpty(Mono.error(new NotFoundException()))
        .flatMap(inventory -> {
            inventory.setAvailableQuantity(quantity);    // 값 수정
            return inventoryRepository.save(inventory);  // 수정된 엔티티 저장
        })
        .map(this::toResponse);
}
```

### 7-2. then vs flatMap

| 연산자 | 설명 | 결과 |
|--------|------|------|
| `monoA.then(monoB)` | A 실행 → A 결과 무시 → B 실행 | B의 결과만 반환 |
| `monoA.flatMap(a -> monoB)` | A 실행 → A 결과를 B에 전달 | B의 결과 반환 |

```java
// then: A의 결과(RoomType)가 필요 없을 때
roomTypeRepository.findById(id)
    .then(inventoryRepository.save(inventory));  // RoomType 값은 안 씀

// flatMap: A의 결과(RoomType)가 필요할 때
roomTypeRepository.findById(id)
    .flatMap(roomType -> {
        inventory.setRoomTypeId(roomType.getId());  // RoomType 값을 사용
        return inventoryRepository.save(inventory);
    });
```

### 7-3. 주의사항 — 블로킹 코드 금지

WebFlux는 소수의 이벤트 루프 스레드로 동작한다. 블로킹 코드가 들어가면 **전체 서버가 멈출 수 있다.**

```java
// ❌ 절대 하면 안 되는 것
public Mono<Inventory> getInventory(Long id) {
    Inventory inventory = inventoryRepository.findById(id).block();  // 블로킹!
    return Mono.just(inventory);
}

// ❌ Thread.sleep 사용
public Mono<Void> process() {
    Thread.sleep(1000);  // 이벤트 루프 스레드를 1초간 점유
    return Mono.empty();
}

// ✅ 올바른 비동기 지연
public Mono<Void> process() {
    return Mono.delay(Duration.ofSeconds(1))  // 논블로킹 지연
        .then();
}
```

---

## 8. DTO와 요청/응답 설계

### 8-1. 왜 DTO를 사용하는가?

엔티티를 직접 반환하면 다음 문제가 생긴다:
- 내부 필드(id, createdAt 등)가 그대로 노출된다.
- API 스펙과 DB 스키마가 강하게 결합된다.
- 요청/응답에 필요한 필드가 서로 다를 수 있다.

### 8-2. DTO 설계 규칙

```
요청 DTO:  {도메인}CreateRequest, {도메인}UpdateRequest
응답 DTO:  {도메인}Response
```

#### 인벤토리 요청 DTO 예시

```kotlin
// Kotlin
data class InventoryCreateRequest(
    val roomTypeId: Long,          // 객실 타입 ID
    val stockDate: LocalDate,      // 재고 날짜
    val totalQuantity: Int         // 전체 객실 수
)

data class InventoryUpdateRequest(
    val totalQuantity: Int?,       // 전체 수량 (선택적 수정)
    val availableQuantity: Int?    // 가용 수량 (선택적 수정)
)
```

```java
// Java
public record InventoryCreateRequest(
    Long roomTypeId,           // 객실 타입 ID
    LocalDate stockDate,       // 재고 날짜
    int totalQuantity          // 전체 객실 수
) {}

public record InventoryUpdateRequest(
    Integer totalQuantity,      // 전체 수량 (null이면 수정 안 함)
    Integer availableQuantity   // 가용 수량 (null이면 수정 안 함)
) {}
```

#### 인벤토리 응답 DTO 예시

```kotlin
// Kotlin
data class InventoryResponse(
    val id: Long,
    val roomTypeId: Long,
    val stockDate: LocalDate,
    val totalQuantity: Int,
    val availableQuantity: Int,
    val updatedAt: LocalDateTime?
)
```

```java
// Java
public record InventoryResponse(
    Long id,
    Long roomTypeId,
    LocalDate stockDate,
    int totalQuantity,
    int availableQuantity,
    LocalDateTime updatedAt
) {}
```

### 8-3. Java record vs Kotlin data class

| 특성 | Java `record` | Kotlin `data class` |
|------|---------------|---------------------|
| 불변성 | 완전 불변 (필드 수정 불가) | `val`이면 불변, `var`이면 가변 |
| equals/hashCode | 자동 생성 | 자동 생성 |
| toString | 자동 생성 | 자동 생성 |
| copy | 없음 (직접 구현 필요) | `copy()` 메서드 제공 |
| Builder 패턴 | 없음 | 없음 (copy로 대체) |
| 상속 | 불가 | 불가 |

> **Java DTO에 record 사용:** DTO는 불변이어야 하므로 Java 16+의 `record`가 최적이다. Lombok `@Data`보다 간결하고, 불변성이 보장된다.

---

## 9. R2DBC 트랜잭션 처리

### 9-1. 왜 트랜잭션이 필요한가?

인벤토리 수정 시 "재고 차감 + 예약 생성"을 하나의 단위로 처리해야 한다. 재고 차감은 성공했는데 예약 생성이 실패하면 데이터 정합성이 깨진다.

### 9-2. @Transactional

R2DBC에서도 `@Transactional`을 사용할 수 있다. Spring이 Reactive 트랜잭션 매니저를 자동 구성한다.

```java
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional                                             // 트랜잭션 시작
    public Mono<Inventory> decreaseAvailability(Long id, int quantity) {
        return inventoryRepository.findById(id)
            .switchIfEmpty(Mono.error(new NotFoundException()))
            .flatMap(inventory -> {
                int newQuantity = inventory.getAvailableQuantity() - quantity;
                if (newQuantity < 0) {
                    return Mono.error(new BadRequestException("재고 부족"));
                }
                inventory.setAvailableQuantity(newQuantity);
                return inventoryRepository.save(inventory);
            });
        // 메서드가 정상 완료 → 커밋, 에러 발생 → 롤백
    }
}
```

### 9-3. TransactionalOperator (프로그래밍 방식)

어노테이션 대신 코드로 트랜잭션 범위를 제어할 수도 있다.

```java
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final TransactionalOperator transactionalOperator;

    public Mono<Inventory> decreaseAvailability(Long id, int quantity) {
        return inventoryRepository.findById(id)
            .flatMap(inventory -> {
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
                return inventoryRepository.save(inventory);
            })
            .as(transactionalOperator::transactional);  // 이 체인 전체를 트랜잭션으로
    }
}
```

### 9-4. @Transactional vs TransactionalOperator

| | @Transactional | TransactionalOperator |
|---|---|---|
| **사용법** | 메서드에 어노테이션 | `.as(operator::transactional)` |
| **트랜잭션 범위** | 메서드 전체 | 지정한 체인만 |
| **선언적/프로그래밍** | 선언적 | 프로그래밍 |
| **권장** | 일반적인 경우 | 세밀한 제어 필요 시 |

> Phase 2에서는 `@Transactional`을 사용한다. 대부분의 경우 메서드 단위 트랜잭션으로 충분하다.

---

## 10. 인벤토리 관리 API 설계

Phase 2에서 구현할 API 목록이다.

### 10-1. API 목록

| HTTP | URL | 설명 | 요청 | 응답 |
|------|-----|------|------|------|
| GET | `/api/inventories/{id}` | 재고 단건 조회 | - | `InventoryResponse` |
| GET | `/api/inventories` | 기간별 재고 조회 | `?roomTypeId=&startDate=&endDate=` | `Flux<InventoryResponse>` |
| POST | `/api/inventories` | 재고 생성 | `InventoryCreateRequest` | `InventoryResponse` (201) |
| PUT | `/api/inventories/{id}` | 재고 수정 | `InventoryUpdateRequest` | `InventoryResponse` |
| DELETE | `/api/inventories/{id}` | 재고 삭제 | - | 204 No Content |
| POST | `/api/inventories/bulk` | 기간별 일괄 생성 | `InventoryBulkCreateRequest` | `Flux<InventoryResponse>` (201) |

### 10-2. 일괄 생성 API 설계

호텔에서는 "3월 15일부터 3월 31일까지 Deluxe Twin 10실" 처럼 기간별로 재고를 일괄 생성한다.

```json
// POST /api/inventories/bulk
{
    "roomTypeId": 2,
    "startDate": "2026-03-15",
    "endDate": "2026-03-31",
    "totalQuantity": 10
}
```

이를 구현하려면 날짜 범위를 Flux로 만들어야 한다:

```java
// startDate ~ endDate 범위의 날짜를 Flux로 생성
Flux<LocalDate> dateRange = Flux.fromStream(
    startDate.datesUntil(endDate.plusDays(1))  // endDate 포함
);

// 각 날짜에 대해 Inventory 생성
dateRange.flatMap(date -> {
    Inventory inventory = new Inventory(roomTypeId, date, totalQuantity, totalQuantity);
    return inventoryRepository.save(inventory);
});
```

### 10-3. 비즈니스 규칙

1. **재고 생성 시:** `availableQuantity = totalQuantity` (초기 가용 수량 = 전체 수량)
2. **중복 방지:** 동일 `(roomTypeId, stockDate)` 조합은 DB UNIQUE 제약으로 차단
3. **수량 검증:** `availableQuantity`는 0 미만이 될 수 없고, `totalQuantity`를 초과할 수 없다
4. **수정 시:** `totalQuantity`를 줄일 때 `availableQuantity`보다 작아지면 에러

---

## 11. 테스트 — WebTestClient 사용법

### 11-1. WebTestClient란?

WebTestClient는 WebFlux 애플리케이션을 테스트하기 위한 HTTP 클라이언트다. 실제 서버를 띄우거나(`@SpringBootTest`) 모킹으로 테스트할 수 있다.

### 11-2. 통합 테스트 (서버 기동)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryControllerTest {

    @Autowired
    private WebTestClient webTestClient;  // 자동 주입

    @Test
    void 재고_단건_조회() {
        webTestClient.get()
            .uri("/api/inventories/{id}", 1L)
            .exchange()                              // 요청 실행
            .expectStatus().isOk()                   // 200 확인
            .expectBody(InventoryResponse.class)     // 응답 타입
            .value(response -> {
                assertThat(response.getId()).isEqualTo(1L);
                assertThat(response.getAvailableQuantity()).isGreaterThan(0);
            });
    }

    @Test
    void 기간별_재고_조회() {
        webTestClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/inventories")
                .queryParam("roomTypeId", 1L)
                .queryParam("startDate", "2026-03-15")
                .queryParam("endDate", "2026-03-19")
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(InventoryResponse.class) // Flux → List
            .hasSize(5);                             // 5일치
    }

    @Test
    void 재고_생성() {
        InventoryCreateRequest request = new InventoryCreateRequest(1L, LocalDate.of(2026, 4, 1), 20);

        webTestClient.post()
            .uri("/api/inventories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()              // 201 확인
            .expectBody(InventoryResponse.class)
            .value(response -> {
                assertThat(response.getTotalQuantity()).isEqualTo(20);
                assertThat(response.getAvailableQuantity()).isEqualTo(20);
            });
    }

    @Test
    void 존재하지_않는_재고_조회시_404() {
        webTestClient.get()
            .uri("/api/inventories/{id}", 99999L)
            .exchange()
            .expectStatus().isNotFound();
    }
}
```

### 11-3. StepVerifier와 WebTestClient 비교

| | StepVerifier | WebTestClient |
|---|---|---|
| **테스트 대상** | Service/Repository (Mono/Flux) | Controller (HTTP 요청/응답) |
| **검증 수준** | Reactive 스트림 레벨 | HTTP 레벨 (상태 코드, 헤더, 본문) |
| **사용 시기** | 비즈니스 로직 테스트 | API 통합 테스트 |
| **Phase 1** | Repository 테스트에 사용 | - |
| **Phase 2** | Service 테스트에 계속 사용 | Controller 테스트에 추가 |

---

## 12. Kotlin vs Java 비교 — Reactive 코드 스타일

### 12-1. Controller

```kotlin
// Kotlin
@RestController
@RequestMapping("/api/inventories")
class InventoryController(
    private val inventoryService: InventoryService    // 생성자 주입 (자동)
) {
    @GetMapping("/{id}")
    fun getInventory(@PathVariable id: Long): Mono<ResponseEntity<InventoryResponse>> =
        inventoryService.getInventory(id)             // 단일 표현식 함수
            .map { ResponseEntity.ok(it) }            // it = 암시적 파라미터
            .defaultIfEmpty(ResponseEntity.notFound().build())
}
```

```java
// Java
@RestController
@RequestMapping("/api/inventories")
@RequiredArgsConstructor                              // Lombok 생성자 주입
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{id}")
    public Mono<ResponseEntity<InventoryResponse>> getInventory(@PathVariable Long id) {
        return inventoryService.getInventory(id)
            .map(ResponseEntity::ok)                  // 메서드 레퍼런스
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
```

**차이점:**
- Kotlin: 생성자 주입이 기본 문법, 단일 표현식 함수(`=`), 람다에서 `it` 사용
- Java: `@RequiredArgsConstructor`로 생성자 주입, 메서드 레퍼런스(`::`) 선호

### 12-2. Service

```kotlin
// Kotlin
@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository,
    private val roomTypeRepository: RoomTypeRepository
) {
    fun createInventory(request: InventoryCreateRequest): Mono<InventoryResponse> =
        roomTypeRepository.findById(request.roomTypeId)
            .switchIfEmpty(Mono.error(NotFoundException("객실 타입을 찾을 수 없습니다")))
            .then(Mono.defer {                        // Kotlin은 {} 가 람다
                val inventory = Inventory(
                    roomTypeId = request.roomTypeId,   // named argument
                    stockDate = request.stockDate,
                    totalQuantity = request.totalQuantity,
                    availableQuantity = request.totalQuantity
                )
                inventoryRepository.save(inventory)
            })
            .map { it.toResponse() }                  // 확장 함수 가능
}
```

```java
// Java
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final RoomTypeRepository roomTypeRepository;

    public Mono<InventoryResponse> createInventory(InventoryCreateRequest request) {
        return roomTypeRepository.findById(request.roomTypeId())
            .switchIfEmpty(Mono.error(new NotFoundException("객실 타입을 찾을 수 없습니다")))
            .then(Mono.defer(() -> {                  // Java 람다는 () ->
                Inventory inventory = new Inventory(
                    request.roomTypeId(),              // record 접근자
                    request.stockDate(),
                    request.totalQuantity(),
                    request.totalQuantity()
                );
                return inventoryRepository.save(inventory);
            }))
            .map(this::toResponse);                   // 변환 메서드 레퍼런스
    }
}
```

**차이점:**
- Kotlin: `Mono.defer { }` (람다가 `{}`), named argument, 확장 함수
- Java: `Mono.defer(() -> { })` (람다가 `() -> {}`), record 접근자 `field()`, `this::method`

### 12-3. DTO

```kotlin
// Kotlin — data class (간결)
data class InventoryResponse(
    val id: Long,
    val roomTypeId: Long,
    val stockDate: LocalDate,
    val totalQuantity: Int,
    val availableQuantity: Int,
    val updatedAt: LocalDateTime?
)
```

```java
// Java — record (Java 16+, 간결 + 불변)
public record InventoryResponse(
    Long id,
    Long roomTypeId,
    LocalDate stockDate,
    int totalQuantity,
    int availableQuantity,
    LocalDateTime updatedAt
) {}
```

---

## 핵심 요약

| 개념 | 설명 | Phase 2 활용 |
|------|------|-------------|
| `map` | 동기 변환 | 엔티티 → DTO 변환 |
| `flatMap` | 비동기 변환 | DB 조회 체이닝 |
| `switchIfEmpty` | 빈 결과 처리 | 404 에러 발행 |
| `filter` | 조건 필터링 | 활성 채널 확인 |
| `collectList` | Flux → List | 목록 응답 집계 |
| `@Transactional` | 트랜잭션 | 재고 수정 원자성 |
| `WebTestClient` | HTTP 테스트 | API 통합 테스트 |

### 다음 단계

이 개념들을 기반으로 인벤토리 관리 API를 구현한다:
1. DTO 정의 (Kotlin + Java)
2. Service 구현 (비즈니스 로직)
3. Controller 구현 (REST 엔드포인트)
4. 예외 처리 (글로벌 핸들러)
5. 테스트 (WebTestClient)
