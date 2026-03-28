# Phase 6 — 실시간 대시보드 (EventSource + Vanilla JS)

## 1. 개요

Phase 5에서 구현한 SSE(Server-Sent Events) 엔드포인트를 브라우저에서 소비하여,
**실시간으로 이벤트를 표시하는 대시보드**를 구현한다.

```
┌───────────────────────────────────────────────────────────────┐
│                      실시간 대시보드                           │
│                                                                │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────────┐│
│  │ 연결 상태    │  │ 시뮬레이터  │  │  통계 요약              ││
│  │ 🟢 연결됨   │  │ ▶ 시작/중지 │  │  예약: 12 | 재고변경: 5││
│  └─────────────┘  └─────────────┘  └────────────────────────┘│
│                                                                │
│  ┌────────────────────────────────────────────────────────────┐│
│  │                     이벤트 스트림                           ││
│  │  14:23:01  RESERVATION_CREATED  BOOKING  Superior Double  ││
│  │  14:23:01  CHANNEL_SYNCED       AGODA    Superior Double  ││
│  │  14:23:01  CHANNEL_SYNCED       DIRECT   Superior Double  ││
│  │  14:22:58  INVENTORY_UPDATED    —        Deluxe Twin      ││
│  │  ...                                                       ││
│  └────────────────────────────────────────────────────────────┘│
└───────────────────────────────────────────────────────────────┘
```

### 기술 스택
- **HTML5**: 시맨틱 마크업, 대시보드 레이아웃
- **CSS3**: Grid/Flexbox 레이아웃, 이벤트 타입별 색상
- **Vanilla JS**: 외부 라이브러리 없이 순수 JavaScript만 사용
- **EventSource API**: 브라우저 내장 SSE 클라이언트

## 2. EventSource API 상세

### 2.1 기본 사용법

```javascript
// 1. EventSource 생성 — SSE 엔드포인트에 연결
const eventSource = new EventSource('/api/events/stream');

// 2. 연결 성공 시 호출
eventSource.onopen = function(event) {
    console.log('SSE 연결 성공, readyState:', eventSource.readyState);
    // readyState: 0=CONNECTING, 1=OPEN, 2=CLOSED
};

// 3. 특정 이벤트 타입 수신 (서버에서 event: 필드를 설정한 경우)
eventSource.addEventListener('RESERVATION_CREATED', function(event) {
    const data = JSON.parse(event.data);  // SSE data: 필드 → JSON 파싱
    console.log('예약 생성:', data);
});

// 4. 에러/연결 끊김 처리
eventSource.onerror = function(event) {
    if (eventSource.readyState === EventSource.CLOSED) {
        console.log('서버가 연결을 종료함');
    } else {
        console.log('연결 오류, 자동 재연결 시도 중...');
    }
};

// 5. 연결 종료
eventSource.close();
```

### 2.2 event 타입별 수신 분기

Phase 5에서 SSE의 `event:` 필드에 EventType 이름을 설정했다:
- `event: RESERVATION_CREATED`
- `event: INVENTORY_UPDATED`
- `event: CHANNEL_SYNCED`
- `event: RESERVATION_CANCELLED`

**중요:** `event:` 필드가 설정된 SSE 이벤트는 `onmessage`로 수신할 수 없다.
반드시 `addEventListener(eventType, handler)`로 각 타입을 등록해야 한다.

```javascript
// ❌ 동작하지 않음 — event: 필드가 설정된 이벤트는 onmessage로 수신 불가
eventSource.onmessage = function(event) { ... };

// ✅ 올바른 방법 — 각 이벤트 타입별로 addEventListener 등록
const EVENT_TYPES = [
    'RESERVATION_CREATED',
    'INVENTORY_UPDATED',
    'CHANNEL_SYNCED',
    'RESERVATION_CANCELLED'
];

EVENT_TYPES.forEach(type => {
    eventSource.addEventListener(type, function(event) {
        const data = JSON.parse(event.data);
        handleEvent(type, data);
    });
});
```

### 2.3 자동 재연결과 Last-Event-ID

```
┌─────────┐                              ┌──────────┐
│  서버   │ ← HTTP GET /api/events/stream │ 브라우저 │
│         │ → id: 42, data: {...}         │          │
│         │ → id: 43, data: {...}         │          │
│         │                               │          │
│    ✕ 연결 끊김                          │          │
│         │                               │          │
│         │ ← GET /api/events/stream      │          │
│         │   Last-Event-ID: 43           │ (3초 후  │
│         │ → id: 44, data: {...}         │  자동    │
│         │   (43 이후 이벤트만 전송)      │  재연결) │
└─────────┘                              └──────────┘
```

- 브라우저는 연결이 끊기면 **자동으로 재연결**한다 (기본 3초 후)
- 재연결 시 `Last-Event-ID` 헤더에 마지막으로 받은 `id:` 값을 포함한다
- 서버가 이 ID를 활용하면 놓친 이벤트를 재전송할 수 있다 (선택 구현)

### 2.4 readyState 상태 관리

```javascript
function updateConnectionStatus() {
    const indicator = document.getElementById('connection-status');
    switch (eventSource.readyState) {
        case EventSource.CONNECTING: // 0
            indicator.textContent = '연결 중...';
            indicator.className = 'status connecting';
            break;
        case EventSource.OPEN:       // 1
            indicator.textContent = '연결됨';
            indicator.className = 'status connected';
            break;
        case EventSource.CLOSED:     // 2
            indicator.textContent = '연결 끊김';
            indicator.className = 'status disconnected';
            break;
    }
}
```

## 3. 대시보드 아키텍처

### 3.1 전체 데이터 흐름

```
┌──────────────── 서버 (Spring WebFlux) ──────────────────┐
│                                                          │
│  EventPublisher (Sinks) ─→ EventStreamController        │
│                              │                           │
│                    GET /api/events/stream (SSE)          │
│                    GET /api/events (최근 목록)            │
│                    GET /api/simulator/status              │
│                    POST /api/simulator/start|stop         │
│                    GET /api/inventories?roomTypeId=...    │
└─────────────────────┬────────────────────────────────────┘
                      │ HTTP
┌─────────────────────▼────────────────────────────────────┐
│               브라우저 (대시보드)                          │
│                                                           │
│  ┌─ EventSource ──────────────────────────────────┐      │
│  │ addEventListener('RESERVATION_CREATED', ...)   │      │
│  │ addEventListener('INVENTORY_UPDATED', ...)     │      │
│  │ addEventListener('CHANNEL_SYNCED', ...)        │      │
│  └────────────────────────────────────────────────┘      │
│                      │                                    │
│                      ▼                                    │
│  ┌─ DOM 업데이트 ─────────────────────────────────┐      │
│  │ 이벤트 목록에 새 행 추가                        │      │
│  │ 통계 카운터 업데이트                            │      │
│  │ 연결 상태 표시                                  │      │
│  └────────────────────────────────────────────────┘      │
└───────────────────────────────────────────────────────────┘
```

### 3.2 대시보드 구성 요소

| 영역 | 설명 | 데이터 소스 |
|------|------|-------------|
| 연결 상태 | SSE 연결 상태 표시 (연결됨/끊김) | EventSource.readyState |
| 시뮬레이터 제어 | 시작/중지 버튼, 현재 상태 | POST /api/simulator/start\|stop |
| 이벤트 통계 | 이벤트 타입별 카운터 | SSE 수신 시 클라이언트 측 집계 |
| 이벤트 스트림 | 실시간 이벤트 목록 (최신 순) | GET /api/events/stream (SSE) |

### 3.3 정적 파일 구조

```
channel-manager-common/
└── src/main/resources/
    └── static/
        ├── index.html       # 대시보드 HTML (시맨틱 마크업)
        ├── css/
        │   └── dashboard.css  # 스타일 (Grid, 이벤트 타입별 색상)
        └── js/
            └── dashboard.js   # EventSource 연결, DOM 조작, API 호출
```

**왜 channel-manager-common에 배치하는가?**
- Kotlin(8080)과 Java(8081) 모듈이 공통 리소스를 공유한다
- 두 모듈 모두 common 모듈의 classpath를 포함하므로,
  `/static/index.html`에 접근하면 자동으로 서빙된다
- 대시보드 코드를 한 곳에서 관리하여 중복을 방지한다

## 4. Spring WebFlux 정적 리소스 서빙

### 4.1 기본 동작

Spring WebFlux는 기본적으로 `classpath:/static/` 경로의 파일을 정적 리소스로 서빙한다.

```
요청: GET /index.html
→ classpath:/static/index.html 파일 반환

요청: GET /css/dashboard.css
→ classpath:/static/css/dashboard.css 파일 반환

요청: GET /
→ classpath:/static/index.html 자동 매핑 (welcome page)
```

별도 설정 없이도 `src/main/resources/static/` 디렉토리에 파일을 배치하면 자동으로 서빙된다.

### 4.2 WebFlux vs MVC 차이점

Spring MVC에서는 `ResourceHandlerRegistry`를 사용하지만,
Spring WebFlux에서는 `RouterFunction`으로 정적 리소스를 제공할 수 있다.

```kotlin
// 기본 설정으로 충분하므로 별도 설정이 필요 없다
// Spring Boot가 자동으로 classpath:/static/을 서빙한다
```

## 5. DOM 조작 패턴

### 5.1 이벤트 행 추가

```javascript
function addEventRow(eventData) {
    const tbody = document.getElementById('event-list');
    const row = document.createElement('tr');

    // 이벤트 타입에 따라 CSS 클래스 적용
    row.className = `event-row event-${eventData.eventType.toLowerCase()}`;

    row.innerHTML = `
        <td>${formatTime(eventData.createdAt)}</td>
        <td><span class="badge ${eventData.eventType}">${eventData.eventType}</span></td>
        <td>${eventData.channelId ?? '—'}</td>
        <td>${eventData.roomTypeId ?? '—'}</td>
    `;

    // 최신 이벤트를 맨 위에 추가한다 (prepend)
    tbody.insertBefore(row, tbody.firstChild);

    // 최대 100개까지만 유지 (오래된 이벤트 제거)
    while (tbody.children.length > 100) {
        tbody.removeChild(tbody.lastChild);
    }
}
```

### 5.2 fetch API로 REST 호출

```javascript
// 시뮬레이터 시작
async function startSimulator() {
    const response = await fetch('/api/simulator/start', { method: 'POST' });
    const result = await response.json();
    updateSimulatorStatus(result);
}

// 최근 이벤트 로드
async function loadRecentEvents() {
    const response = await fetch('/api/events?limit=50');
    const events = await response.json();
    events.forEach(event => addEventRow(event));
}
```

## 6. 이벤트 타입별 시각적 구분

| EventType | 색상 | 아이콘 | 설명 |
|-----------|------|--------|------|
| RESERVATION_CREATED | 🟢 초록 | 예약 생성 | 새 예약이 들어옴 |
| INVENTORY_UPDATED | 🔵 파랑 | 재고 변경 | 재고 수량이 변경됨 |
| CHANNEL_SYNCED | 🟡 노랑 | 채널 동기화 | 다른 채널로 재고 동기화 |
| RESERVATION_CANCELLED | 🔴 빨강 | 예약 취소 | 예약이 취소됨 (Phase 7) |

## 7. Kotlin vs Java 차이점

Phase 6에서는 프론트엔드 코드만 작성하므로, Kotlin/Java 구현 차이가 없다.
대시보드 HTML/CSS/JS는 `channel-manager-common` 모듈에 한 벌만 작성하며,
Kotlin(8080)과 Java(8081) 양쪽에서 동일하게 서빙된다.

유일한 차이는 **접속 포트**뿐이다:
- Kotlin 대시보드: `http://localhost:8080/`
- Java 대시보드: `http://localhost:8081/`

## 8. 구현 계획

### 구현할 파일

| 파일 | 역할 |
|------|------|
| `static/index.html` | 대시보드 HTML 구조 |
| `static/css/dashboard.css` | 레이아웃, 이벤트 타입별 색상, 반응형 |
| `static/js/dashboard.js` | EventSource 연결, DOM 조작, API 호출 |

### 핵심 학습 포인트

1. **EventSource API**: 브라우저 내장 SSE 클라이언트 실습
2. **addEventListener vs onmessage**: event: 필드가 있을 때의 수신 방법
3. **DOM 조작**: createElement, insertBefore, innerHTML
4. **fetch API**: REST API 호출 (시뮬레이터 제어, 최근 이벤트 로드)
5. **CSS Grid/Flexbox**: 대시보드 레이아웃 구성
6. **정적 리소스 서빙**: Spring WebFlux의 classpath:/static/ 매핑
