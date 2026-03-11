# Kotlin vs Java 비교 학습 가이드

이 문서는 channel-manager 프로젝트에서 동일한 로직을 Kotlin과 Java로 구현하면서
두 언어의 차이점을 학습하기 위한 비교 가이드이다.

---

## 목차
1. [언어 철학의 차이](#1-언어-철학의-차이)
2. [변수와 타입 시스템](#2-변수와-타입-시스템)
3. [Null 안전성](#3-null-안전성)
4. [클래스와 데이터 클래스](#4-클래스와-데이터-클래스)
5. [함수와 표현식](#5-함수와-표현식)
6. [컬렉션과 스트림](#6-컬렉션과-스트림)
7. [Scope 함수](#7-scope-함수)
8. [비동기 프로그래밍: 코루틴 vs Reactor](#8-비동기-프로그래밍-코루틴-vs-reactor)
9. [Spring Boot에서의 차이](#9-spring-boot에서의-차이)
10. [이 프로젝트에서 자주 마주치는 패턴 비교](#10-이-프로젝트에서-자주-마주치는-패턴-비교)

---

## 1. 언어 철학의 차이

| | Kotlin | Java |
|---|---|---|
| **설계 목표** | 간결함, 안전성, 상호운용성 | 안정성, 하위호환성, 명시성 |
| **등장** | 2011년 (JetBrains) | 1995년 (Sun Microsystems) |
| **Null 처리** | 컴파일러가 강제 | 개발자 책임 |
| **불변성** | `val` 기본 권장 | `final` 명시 필요 |
| **보일러플레이트** | 언어 자체가 최소화 | Lombok 등 외부 라이브러리 의존 |
| **함수형** | 1급 시민 함수, 확장 함수 | Java 8+ 람다, 메서드 레퍼런스 |

---

## 2. 변수와 타입 시스템

### 변수 선언

```kotlin
// === Kotlin ===
val name = "서울 호텔"           // val: 불변 (재할당 불가, Java의 final과 유사)
var count = 0                    // var: 가변 (재할당 가능)
val price: BigDecimal = BigDecimal("150000")  // 타입 명시도 가능 (보통 추론에 맡김)

// val은 "참조"가 불변이지, 내부 상태가 불변인 것은 아니다
val list = mutableListOf(1, 2, 3)  // 참조는 변경 불가
list.add(4)                         // 내부 상태 변경은 가능
// list = mutableListOf(5, 6)       // 컴파일 에러! 참조 변경 불가
```

```java
// === Java ===
final String name = "서울 호텔";  // final: 불변 (재할당 불가)
int count = 0;                    // 기본은 가변
BigDecimal price = new BigDecimal("150000");  // 타입 항상 명시

// Java 10+의 var (로컬 변수 타입 추론)
var hotel = new Hotel();          // 타입을 추론하지만 가변이다
// Kotlin의 val과 달리 불변이 아님에 주의
```

### 타입 추론

```kotlin
// === Kotlin: 대부분 타입 추론 가능 ===
val id = 1L                       // Long으로 추론
val hotels = listOf("A", "B")    // List<String>으로 추론
val result = hotelRepository.findById(1L)  // Mono<Hotel>로 추론

// 함수 반환 타입은 public API에서는 명시 권장
fun findHotel(id: Long): Mono<Hotel> = hotelRepository.findById(id)
```

```java
// === Java: var 외에는 타입 명시 필수 ===
long id = 1L;                     // 타입 명시
List<String> hotels = List.of("A", "B");  // 타입 명시
Mono<Hotel> result = hotelRepository.findById(1L);  // 타입 명시

// Java 10+ var (로컬 변수에서만 사용 가능)
var hotel = hotelRepository.findById(1L);  // Mono<Hotel>로 추론
// 필드, 메서드 반환 타입, 파라미터에는 var 사용 불가
```

### 원시 타입 vs 래퍼 타입

```kotlin
// === Kotlin: 원시 타입과 래퍼 타입의 구분이 없다 ===
val count: Int = 10               // 컴파일러가 상황에 따라 int/Integer 자동 선택
val nullableCount: Int? = null    // nullable이면 Integer (래퍼 타입)로 컴파일
```

```java
// === Java: 원시 타입과 래퍼 타입을 구분한다 ===
int count = 10;                   // 원시 타입 (null 불가, 스택 메모리)
Integer nullableCount = null;     // 래퍼 타입 (null 가능, 힙 메모리)
// 오토박싱/언박싱이 발생하므로 성능 차이가 있을 수 있다
```

---

## 3. Null 안전성

Kotlin의 **가장 핵심적인 차이**이다. Kotlin은 null을 **컴파일 타임**에 체크한다.

### 기본 개념

```kotlin
// === Kotlin: 컴파일러가 null을 체크한다 ===
var name: String = "호텔"         // non-null 타입: null 대입 불가
// name = null                    // 컴파일 에러!

var address: String? = "서울"     // nullable 타입: null 대입 가능
address = null                    // OK

// non-null 타입의 메서드는 바로 호출 가능
println(name.length)              // OK - name은 절대 null이 아님

// nullable 타입은 바로 호출 불가
// println(address.length)        // 컴파일 에러! address가 null일 수 있음
println(address?.length)          // OK - null이면 null 반환 (safe call)
println(address?.length ?: 0)     // OK - null이면 0 반환 (Elvis operator)
println(address!!.length)         // OK - 강제 non-null 단언 (null이면 NPE 발생)
```

```java
// === Java: 런타임에 NPE가 발생한다 ===
String name = "호텔";
name = null;                      // 컴파일 OK - 런타임에 문제 발생

String address = null;
// address.length();              // 런타임 NullPointerException!

// null 방어 코드를 개발자가 직접 작성해야 한다
if (address != null) {
    System.out.println(address.length());
}

// Java 8+ Optional
Optional<String> optAddress = Optional.ofNullable(address);
int length = optAddress.map(String::length).orElse(0);
```

### R2DBC에서의 null 처리 차이

```kotlin
// === Kotlin: nullable 타입으로 DB null 값을 안전하게 처리 ===
@Table("hotels")
data class Hotel(
    @Id val id: Long? = null,         // PK: INSERT 시 null
    val name: String,                  // NOT NULL 컬럼 → non-null 타입
    val address: String? = null        // NULLABLE 컬럼 → nullable 타입
)

// 사용할 때 컴파일러가 null 체크를 강제한다
fun getHotelAddress(hotel: Hotel): String {
    return hotel.address ?: "주소 없음"  // Elvis 연산자로 기본값 제공
}
```

```java
// === Java: 모든 필드가 null일 수 있다 ===
@Table("hotels")
public class Hotel {
    @Id private Long id;               // null 가능
    private String name;               // null 가능 (NOT NULL이지만 Java는 모름)
    private String address;            // null 가능
}

// 사용할 때 개발자가 직접 null을 체크해야 한다
public String getHotelAddress(Hotel hotel) {
    return hotel.getAddress() != null ? hotel.getAddress() : "주소 없음";
}
```

### Kotlin의 null 관련 연산자 정리

| 연산자 | 이름 | 설명 | 예시 |
|---|---|---|---|
| `?.` | Safe call | null이면 null 반환, 아니면 메서드 실행 | `hotel?.name` |
| `?:` | Elvis | 좌변이 null이면 우변 반환 | `name ?: "기본값"` |
| `!!` | Non-null 단언 | null이면 NPE, 아니면 non-null로 캐스팅 | `name!!.length` |
| `?.let {}` | Safe call + let | null이 아닐 때만 블록 실행 | `name?.let { println(it) }` |
| `as?` | Safe cast | 캐스팅 실패 시 null 반환 | `obj as? String` |

---

## 4. 클래스와 데이터 클래스

### 일반 클래스

```kotlin
// === Kotlin: 간결한 클래스 정의 ===
class HotelService(                           // primary constructor에서 프로퍼티 선언
    private val hotelRepository: HotelRepository  // val: 불변 프로퍼티 + 생성자 파라미터
) {
    fun findById(id: Long): Mono<Hotel> {     // fun으로 함수 정의
        return hotelRepository.findById(id)
    }
}
// - 기본이 public (접근 제한자 생략 시)
// - 기본이 final (상속 불가, open 키워드로 상속 허용)
// - @Service 어노테이션 + kotlin-spring 플러그인 → 자동으로 open 처리
```

```java
// === Java: 명시적인 클래스 정의 ===
public class HotelService {

    private final HotelRepository hotelRepository;  // final: 불변 필드

    public HotelService(HotelRepository hotelRepository) {  // 생성자 직접 정의
        this.hotelRepository = hotelRepository;              // this로 필드에 할당
    }

    public Mono<Hotel> findById(Long id) {     // public 메서드
        return hotelRepository.findById(id);
    }
}
// - 접근 제한자 명시 필수
// - 기본이 상속 가능 (final로 막음)
// - Lombok @RequiredArgsConstructor로 생성자 생략 가능
```

### data class vs Lombok

```kotlin
// === Kotlin: data class ===
data class HotelDto(
    val id: Long,
    val name: String,
    val address: String?
)
// 자동 생성: equals(), hashCode(), toString(), copy(), componentN()
// 별도 라이브러리 불필요
```

```java
// === Java: Lombok 어노테이션 ===
@Data                          // equals, hashCode, toString, getter, setter
@Builder                       // 빌더 패턴
@NoArgsConstructor             // 기본 생성자
@AllArgsConstructor            // 전체 필드 생성자
public class HotelDto {
    private Long id;
    private String name;
    private String address;
}
// Lombok 라이브러리 의존 필요
// 컴파일 시 코드 생성 → IDE 플러그인 필요
```

```java
// === Java 16+: record (Kotlin data class와 가장 유사) ===
public record HotelDto(
    Long id,
    String name,
    String address
) {}
// 자동 생성: equals(), hashCode(), toString(), getter (accessor)
// 불변 (final 필드), 상속 불가
// 단, setter 없음, builder 없음 → Spring Data R2DBC에서는 제약이 있을 수 있음
```

### enum 비교

```kotlin
// === Kotlin: enum class ===
enum class ReservationStatus {
    CONFIRMED,
    CANCELLED;

    // enum에 프로퍼티와 메서드를 추가할 수 있다
    fun isActive(): Boolean = this == CONFIRMED
}

// when 표현식으로 모든 케이스를 처리 (exhaustive check)
fun describe(status: ReservationStatus): String = when (status) {
    ReservationStatus.CONFIRMED -> "예약 확정"
    ReservationStatus.CANCELLED -> "예약 취소"
    // 모든 케이스를 처리하지 않으면 컴파일 에러
}
```

```java
// === Java: enum ===
public enum ReservationStatus {
    CONFIRMED,
    CANCELLED;

    public boolean isActive() {
        return this == CONFIRMED;
    }
}

// switch 표현식 (Java 14+)
public String describe(ReservationStatus status) {
    return switch (status) {
        case CONFIRMED -> "예약 확정";
        case CANCELLED -> "예약 취소";
    };
}
```

---

## 5. 함수와 표현식

### 함수 정의

```kotlin
// === Kotlin ===

// 일반 함수
fun calculatePrice(basePrice: BigDecimal, nights: Int): BigDecimal {
    return basePrice.multiply(BigDecimal(nights))
}

// 단일 표현식 함수 (= 사용, return 생략)
fun calculatePrice(basePrice: BigDecimal, nights: Int): BigDecimal =
    basePrice.multiply(BigDecimal(nights))

// 기본값이 있는 파라미터
fun findReservations(
    channelId: Long? = null,      // 기본값 null → 조건 무시
    status: String? = null,       // 기본값 null → 조건 무시
    page: Int = 0,                // 기본값 0
    size: Int = 20                // 기본값 20
): Flux<Reservation> { /* ... */ }

// 호출 시 named argument 사용 가능
findReservations(status = "CONFIRMED", size = 50)
```

```java
// === Java ===

// 일반 메서드
public BigDecimal calculatePrice(BigDecimal basePrice, int nights) {
    return basePrice.multiply(BigDecimal.valueOf(nights));
}

// 단일 표현식 함수 → Java에는 없음

// 기본값 파라미터 → Java에는 없으므로 오버로딩으로 대체
public Flux<Reservation> findReservations(Long channelId, String status,
                                           int page, int size) { /* ... */ }

public Flux<Reservation> findReservations(Long channelId, String status) {
    return findReservations(channelId, status, 0, 20);  // 기본값 수동 전달
}

// named argument → Java에는 없음
findReservations(null, "CONFIRMED", 0, 50);  // 파라미터 순서대로 전달
```

### 확장 함수 (Kotlin 전용)

```kotlin
// === Kotlin: 기존 클래스에 함수를 추가할 수 있다 ===

// Reservation에 확장 함수 추가 (Reservation 클래스를 수정하지 않고!)
fun Reservation.isActive(): Boolean = this.status == ReservationStatus.CONFIRMED.name

// BigDecimal에 확장 함수 추가
fun BigDecimal.toWon(): String = "₩${this.setScale(0, RoundingMode.HALF_UP)}"

// 사용
val reservation = Reservation(/* ... */)
if (reservation.isActive()) { /* ... */ }         // 마치 원래 메서드처럼 호출

val price = BigDecimal("150000")
println(price.toWon())                             // "₩150000"
```

```java
// === Java: 확장 함수 없음 → 유틸리티 클래스로 대체 ===
public class ReservationUtils {
    public static boolean isActive(Reservation reservation) {
        return ReservationStatus.CONFIRMED.name().equals(reservation.getStatus());
    }
}

// 사용
if (ReservationUtils.isActive(reservation)) { /* ... */ }
```

---

## 6. 컬렉션과 스트림

### 컬렉션 생성

```kotlin
// === Kotlin: 불변/가변 컬렉션을 구분한다 ===
val immutableList = listOf("A", "B", "C")         // 불변 리스트 (추가/삭제 불가)
val mutableList = mutableListOf("A", "B", "C")    // 가변 리스트

val immutableMap = mapOf("key" to "value")         // 불변 맵
val mutableMap = mutableMapOf("key" to "value")    // 가변 맵

// to는 Pair를 만드는 infix 함수: "key" to "value" == Pair("key", "value")
```

```java
// === Java: 기본이 가변, 불변은 별도 생성 ===
List<String> mutableList = new ArrayList<>(List.of("A", "B", "C"));  // 가변
List<String> immutableList = List.of("A", "B", "C");                  // 불변 (Java 9+)
// 주의: List.of()는 null 요소 불가, Collections.unmodifiableList()와 다름

Map<String, String> immutableMap = Map.of("key", "value");            // 불변 (Java 9+)
Map<String, String> mutableMap = new HashMap<>(Map.of("key", "value"));
```

### 컬렉션 연산 (블로킹 코드에서)

```kotlin
// === Kotlin: 체인 메서드로 간결하게 처리 ===
val hotels = listOf(
    Hotel(id = 1, name = "서울 호텔", address = "서울"),
    Hotel(id = 2, name = "부산 호텔", address = "부산"),
    Hotel(id = 3, name = "제주 호텔", address = null)
)

// 필터 + 변환 + 정렬
val result = hotels
    .filter { it.address != null }              // null이 아닌 것만
    .map { it.name }                            // 이름만 추출
    .sorted()                                   // 정렬
// result: ["부산 호텔", "서울 호텔"]

// groupBy
val byAddress = hotels
    .filterNot { it.address == null }
    .groupBy { it.address }
// {"서울": [Hotel(...)], "부산": [Hotel(...)]}
```

```java
// === Java: Stream API 사용 ===
List<Hotel> hotels = List.of(
    Hotel.builder().id(1L).name("서울 호텔").address("서울").build(),
    Hotel.builder().id(2L).name("부산 호텔").address("부산").build(),
    Hotel.builder().id(3L).name("제주 호텔").address(null).build()
);

// 필터 + 변환 + 정렬
List<String> result = hotels.stream()
    .filter(h -> h.getAddress() != null)        // null이 아닌 것만
    .map(Hotel::getName)                        // 이름만 추출
    .sorted()                                   // 정렬
    .toList();                                  // Java 16+
// result: ["부산 호텔", "서울 호텔"]

// groupBy
Map<String, List<Hotel>> byAddress = hotels.stream()
    .filter(h -> h.getAddress() != null)
    .collect(Collectors.groupingBy(Hotel::getAddress));
```

**핵심 차이:** Kotlin은 컬렉션에 직접 `filter`, `map` 등을 호출한다.
Java는 `.stream()`으로 변환 후 `.collect()`로 다시 컬렉션으로 모아야 한다.

---

## 7. Scope 함수

Kotlin에는 **scope 함수**라는 독특한 개념이 있다.
객체의 컨텍스트 안에서 코드 블록을 실행하는 함수이다.

### let

```kotlin
// null이 아닐 때만 실행 (가장 많이 쓰는 패턴)
val hotel: Hotel? = findHotel(id)
hotel?.let {
    println("호텔명: ${it.name}")     // it = hotel (non-null)
}

// 변환에 사용
val hotelName: String? = findHotel(id)?.let { it.name }
```

### apply

```kotlin
// 객체 초기화에 사용 (this가 객체를 가리킴, 객체 자신을 반환)
val hotel = Hotel(name = "서울 호텔").apply {
    // this = Hotel 객체 (this 생략 가능)
    println("호텔 생성: $name")
}
```

### also

```kotlin
// 부수 효과(로깅 등)에 사용 (it이 객체를 가리킴, 객체 자신을 반환)
val hotel = hotelRepository.save(hotel)
    .also { println("저장된 호텔: ${it.name}") }  // 로깅 후 hotel 반환
```

### run

```kotlin
// 객체의 컨텍스트에서 계산 후 결과 반환
val length = "서울 그랜드 호텔".run {
    // this = "서울 그랜드 호텔"
    this.length  // 결과값 반환
}
```

### with

```kotlin
// run과 유사하지만 확장 함수가 아닌 일반 함수
val description = with(hotel) {
    // this = hotel
    "$name ($address)"  // 결과값 반환
}
```

### Scope 함수 선택 가이드

| 함수 | 객체 참조 | 반환값 | 주요 용도 |
|---|---|---|---|
| `let` | `it` | 람다 결과 | null 체크, 변환 |
| `apply` | `this` | 객체 자신 | 객체 초기화 |
| `also` | `it` | 객체 자신 | 부수 효과 (로깅) |
| `run` | `this` | 람다 결과 | 객체에서 계산 |
| `with` | `this` | 람다 결과 | 객체의 여러 메서드 호출 |

**Java에는 scope 함수가 없다.** 각각의 기능을 다른 방식으로 구현해야 한다.

```java
// Java에서 Kotlin scope 함수와 유사한 패턴
// let → Optional.map()
Optional.ofNullable(hotel).map(h -> h.getName());

// also → 별도 변수 할당 + 로깅
Hotel saved = hotelRepository.save(hotel);
System.out.println("저장된 호텔: " + saved.getName());
```

---

## 8. 비동기 프로그래밍: 코루틴 vs Reactor

이 프로젝트에서는 **Reactor (Mono/Flux)**를 사용하지만, Kotlin에서는 **코루틴**이라는 대안이 있다.
동일한 비동기 로직을 코루틴과 Reactor로 각각 작성하면 어떻게 다른지 비교한다.

### Reactor 방식 (Kotlin & Java 공통)

```kotlin
// === Reactor: Mono/Flux 체인으로 비동기 처리 ===
fun createReservation(request: CreateReservationRequest): Mono<Reservation> {
    return inventoryRepository.findByRoomTypeIdAndStockDate(     // 1. 재고 조회
            request.roomTypeId, request.checkInDate
        )
        .switchIfEmpty(Mono.error(RuntimeException("재고 없음")))  // 2. 재고 없으면 에러
        .flatMap { inventory ->                                    // 3. 재고 차감
            if (inventory.availableQuantity < request.quantity) {
                Mono.error(RuntimeException("재고 부족"))
            } else {
                inventoryRepository.save(
                    inventory.copy(availableQuantity = inventory.availableQuantity - request.quantity)
                )
            }
        }
        .flatMap {                                                 // 4. 예약 저장
            reservationRepository.save(
                Reservation(
                    channelId = request.channelId,
                    roomTypeId = request.roomTypeId,
                    checkInDate = request.checkInDate,
                    checkOutDate = request.checkOutDate,
                    guestName = request.guestName,
                    quantity = request.quantity,
                    status = ReservationStatus.CONFIRMED.name
                )
            )
        }
}
```

### 코루틴 방식 (Kotlin 전용)

```kotlin
// === 코루틴: 동기 코드처럼 보이지만 비동기로 동작 ===
// suspend 키워드가 붙은 함수는 코루틴 안에서만 호출 가능
suspend fun createReservation(request: CreateReservationRequest): Reservation {
    // 1. 재고 조회 (awaitSingleOrNull: Mono → suspend 변환)
    val inventory = inventoryRepository
        .findByRoomTypeIdAndStockDate(request.roomTypeId, request.checkInDate)
        .awaitSingleOrNull() ?: throw RuntimeException("재고 없음")  // 2. 재고 없으면 에러

    // 3. 재고 차감
    if (inventory.availableQuantity < request.quantity) {
        throw RuntimeException("재고 부족")
    }
    inventoryRepository.save(
        inventory.copy(availableQuantity = inventory.availableQuantity - request.quantity)
    ).awaitSingle()

    // 4. 예약 저장
    return reservationRepository.save(
        Reservation(
            channelId = request.channelId,
            roomTypeId = request.roomTypeId,
            checkInDate = request.checkInDate,
            checkOutDate = request.checkOutDate,
            guestName = request.guestName,
            quantity = request.quantity,
            status = ReservationStatus.CONFIRMED.name
        )
    ).awaitSingle()
}
```

### 비교

| 항목 | Reactor (Mono/Flux) | 코루틴 (suspend) |
|---|---|---|
| **코드 스타일** | 체인 기반 (flatMap, map, zip) | 순차적 (동기 코드처럼 보임) |
| **에러 처리** | `onErrorResume`, `switchIfEmpty` | `try-catch` |
| **가독성** | 체인이 길어지면 복잡해짐 | 직관적 |
| **디버깅** | 스택트레이스 추적 어려움 | 상대적으로 쉬움 |
| **Java 호환** | Java에서 직접 사용 가능 | Kotlin 전용 |
| **학습 곡선** | Reactive Streams 이해 필요 | 상대적으로 낮음 |

**이 프로젝트에서 Reactor를 선택한 이유:**
- Java와 Kotlin 양쪽에서 동일한 패턴으로 구현하기 위해
- WebFlux의 Reactor를 직접 학습하는 것이 목적
- 코루틴은 Kotlin 전용이므로 Java와 비교가 불가능

---

## 9. Spring Boot에서의 차이

### 의존성 주입

```kotlin
// === Kotlin: primary constructor 주입 (가장 간결) ===
@Service
class HotelService(
    private val hotelRepository: HotelRepository,    // 생성자 주입 (자동)
    private val roomTypeRepository: RoomTypeRepository
) {
    fun findAll(): Flux<Hotel> = hotelRepository.findAll()
}
// kotlin-spring 플러그인이 @Service 클래스를 자동으로 open 처리
// 별도의 @RequiredArgsConstructor 불필요
```

```java
// === Java 방법 1: Lombok @RequiredArgsConstructor ===
@Service
@RequiredArgsConstructor         // final 필드에 대한 생성자 자동 생성
public class HotelService {
    private final HotelRepository hotelRepository;        // final → 생성자 주입 대상
    private final RoomTypeRepository roomTypeRepository;  // final → 생성자 주입 대상

    public Flux<Hotel> findAll() {
        return hotelRepository.findAll();
    }
}

// === Java 방법 2: 직접 생성자 정의 ===
@Service
public class HotelService {
    private final HotelRepository hotelRepository;

    public HotelService(HotelRepository hotelRepository) {  // 생성자 직접 작성
        this.hotelRepository = hotelRepository;
    }
}
```

### Controller 정의

```kotlin
// === Kotlin: WebFlux Controller ===
@RestController
@RequestMapping("/api/hotels")
class HotelController(
    private val hotelService: HotelService
) {
    @GetMapping
    fun findAll(): Flux<Hotel> = hotelService.findAll()  // 단일 표현식 함수

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): Mono<Hotel> =
        hotelService.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateHotelRequest): Mono<Hotel> =
        hotelService.create(request)
}
```

```java
// === Java: WebFlux Controller ===
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @GetMapping
    public Flux<Hotel> findAll() {
        return hotelService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<Hotel> findById(@PathVariable Long id) {
        return hotelService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Hotel> create(@RequestBody CreateHotelRequest request) {
        return hotelService.create(request);
    }
}
```

### 설정 클래스

```kotlin
// === Kotlin ===
@Configuration
@EnableR2dbcAuditing
class R2dbcConfig    // 클래스 본문이 없을 때 {} 생략 가능
```

```java
// === Java ===
@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {
    // 클래스 본문이 없어도 {} 필수
}
```

---

## 10. 이 프로젝트에서 자주 마주치는 패턴 비교

### 패턴 1: 동적 SQL 조건 추가

```kotlin
// === Kotlin: ?.let으로 null 안전하게 조건 추가 ===
fun buildDynamicQuery(param: SearchParam): String = buildString {
    append("SELECT * FROM reservations WHERE 1=1")

    param.channelId?.let { append(" AND channel_id = :channelId") }
    param.status?.let { append(" AND status = :status") }
    param.guestName?.let { append(" AND LOWER(guest_name) LIKE :guestName") }
}
```

```java
// === Java: if 문으로 null 체크 ===
public String buildDynamicQuery(SearchParam param) {
    StringBuilder sql = new StringBuilder("SELECT * FROM reservations WHERE 1=1");

    if (param.getChannelId() != null) {
        sql.append(" AND channel_id = :channelId");
    }
    if (param.getStatus() != null) {
        sql.append(" AND status = :status");
    }
    if (param.getGuestName() != null) {
        sql.append(" AND LOWER(guest_name) LIKE :guestName");
    }
    return sql.toString();
}
```

### 패턴 2: Reactive 체인에서 조건 분기

```kotlin
// === Kotlin: when 표현식 활용 ===
fun handleReservation(status: ReservationStatus): Mono<String> = when (status) {
    ReservationStatus.CONFIRMED -> Mono.just("예약이 확정되었습니다")
    ReservationStatus.CANCELLED -> Mono.just("예약이 취소되었습니다")
}
```

```java
// === Java: switch 표현식 활용 ===
public Mono<String> handleReservation(ReservationStatus status) {
    return switch (status) {
        case CONFIRMED -> Mono.just("예약이 확정되었습니다");
        case CANCELLED -> Mono.just("예약이 취소되었습니다");
    };
}
```

### 패턴 3: Row 매핑

```kotlin
// === Kotlin: named argument + !! (non-null 단언) ===
return databaseClient.sql(sql)
    .map { row, _ ->                          // _ : 사용하지 않는 파라미터 무시
        ReservationDto(
            id = row.get("id", Long::class.java)!!,     // !!: DB에서 NOT NULL이므로 단언
            guestName = row.get("guest_name", String::class.java)!!,
            channelCode = row.get("channel_code", String::class.java)  // nullable
        )
    }.all()
```

```java
// === Java: 생성자 또는 빌더로 매핑 ===
return databaseClient.sql(sql)
    .map((row, metadata) -> ReservationDto.builder()
        .id(row.get("id", Long.class))
        .guestName(row.get("guest_name", String.class))
        .channelCode(row.get("channel_code", String.class))   // null 가능
        .build()
    ).all();
```

### 패턴 4: 바인드 파라미터 처리

```kotlin
// === Kotlin: fold로 함수형 처리 ===
val binds = mutableMapOf<String, Any>()
param.channelId?.let { binds["channelId"] = it }
param.status?.let { binds["status"] = it }

binds.entries.fold(databaseClient.sql(sql)) { spec, (key, value) ->
    spec.bind(key, value)
}
```

```java
// === Java: for-each 루프로 처리 ===
Map<String, Object> binds = new HashMap<>();
if (param.getChannelId() != null) {
    binds.put("channelId", param.getChannelId());
}
if (param.getStatus() != null) {
    binds.put("status", param.getStatus());
}

DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
for (Map.Entry<String, Object> entry : binds.entrySet()) {
    spec = spec.bind(entry.getKey(), entry.getValue());
}
```
