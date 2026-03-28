// ==============================
// Channel Manager 실시간 대시보드
// ==============================
// Phase 6: EventSource API로 SSE 이벤트를 수신하여 대시보드를 실시간 업데이트한다
// 순수 JavaScript(Vanilla JS)만 사용하며, 외부 라이브러리에 의존하지 않는다

// ==============================
// 1. 전역 상태 관리
// ==============================
// eventSource — SSE 연결 객체. 페이지 생명주기 동안 유지된다
let eventSource = null;

// eventCounts — 이벤트 타입별 수신 카운터
// SSE로 이벤트를 수신할 때마다 해당 타입의 카운터를 증가시킨다
// 대시보드의 통계 카드에 표시된다
const eventCounts = {
    RESERVATION_CREATED: 0,  // 예약 생성 횟수
    INVENTORY_UPDATED: 0,    // 재고 변경 횟수
    CHANNEL_SYNCED: 0,       // 채널 동기화 횟수
    RESERVATION_CANCELLED: 0 // 예약 취소 횟수
};

// EVENT_TYPES — 처리할 SSE 이벤트 타입 목록
// Phase 5의 EventType enum과 일치해야 한다
// addEventListener로 각 타입을 개별 등록한다
const EVENT_TYPES = [
    'RESERVATION_CREATED',
    'INVENTORY_UPDATED',
    'CHANNEL_SYNCED',
    'RESERVATION_CANCELLED'
];

// MAX_EVENTS — 화면에 표시할 최대 이벤트 수
// 너무 많은 DOM 노드가 쌓이면 브라우저 성능이 저하되므로 제한한다
const MAX_EVENTS = 100;

// ==============================
// 2. EventSource 연결
// ==============================
// SSE 엔드포인트(GET /api/events/stream)에 연결하여 실시간 이벤트를 수신한다
// Phase 5에서 구현한 EventStreamController가 이 엔드포인트를 제공한다
function connectSSE() {
    // EventSource 생성 — 브라우저 내장 SSE 클라이언트
    // /api/events/stream에 HTTP GET 요청을 보내고, text/event-stream 연결을 유지한다
    eventSource = new EventSource('/api/events/stream');

    // onopen — SSE 연결이 성공적으로 수립되었을 때 호출된다
    // 연결 상태를 "연결됨"으로 업데이트한다
    eventSource.onopen = function () {
        updateConnectionStatus('connected'); // 연결 상태 UI 업데이트
    };

    // onerror — SSE 연결 오류 또는 연결 끊김 시 호출된다
    // readyState에 따라 연결 상태를 표시한다
    // 브라우저는 자동으로 재연결을 시도하므로, 별도 재연결 로직이 필요 없다
    eventSource.onerror = function () {
        // CONNECTING(0): 재연결 시도 중 — 브라우저가 자동으로 재연결한다
        // CLOSED(2): 서버가 연결을 완전히 종료함 — 수동 재연결 필요
        if (eventSource.readyState === EventSource.CONNECTING) {
            updateConnectionStatus('connecting'); // "연결 중..." 표시
        } else {
            updateConnectionStatus('disconnected'); // "연결 끊김" 표시
        }
    };

    // 각 이벤트 타입별로 addEventListener를 등록한다
    // Phase 5에서 ServerSentEvent의 event: 필드에 EventType 이름을 설정했으므로,
    // addEventListener로 타입별 분기 처리가 가능하다
    // 주의: event: 필드가 설정된 이벤트는 onmessage로 수신할 수 없다
    EVENT_TYPES.forEach(function (type) {
        eventSource.addEventListener(type, function (event) {
            // event.data — SSE의 data: 필드 (JSON 문자열)
            // JSON.parse()로 JavaScript 객체로 변환한다
            var data = JSON.parse(event.data);

            // 이벤트 처리: DOM에 행 추가 + 통계 카운터 업데이트
            addEventRow(type, data);
            incrementCounter(type);
        });
    });
}

// ==============================
// 3. 연결 상태 UI 업데이트
// ==============================
// SSE 연결 상태에 따라 헤더의 인디케이터 점과 텍스트를 변경한다
// status: 'connected' | 'connecting' | 'disconnected'
function updateConnectionStatus(status) {
    // DOM 요소 참조 — id로 직접 접근한다
    var indicator = document.getElementById('connection-indicator');
    var text = document.getElementById('connection-text');

    // 기존 상태 클래스를 모두 제거한다
    // classList.remove(): 특정 클래스만 제거 (다른 클래스는 유지)
    indicator.classList.remove(
        'status-dot--connected',
        'status-dot--connecting',
        'status-dot--disconnected'
    );

    // 새 상태에 맞는 클래스와 텍스트를 설정한다
    if (status === 'connected') {
        indicator.classList.add('status-dot--connected'); // 초록 점
        text.textContent = '연결됨';                       // 텍스트 변경
    } else if (status === 'connecting') {
        indicator.classList.add('status-dot--connecting'); // 노랑 점 + 깜빡임
        text.textContent = '연결 중...';
    } else {
        indicator.classList.add('status-dot--disconnected'); // 빨강 점
        text.textContent = '연결 끊김';
    }
}

// ==============================
// 4. 이벤트 행 추가
// ==============================
// SSE로 수신한 이벤트를 테이블에 새 행으로 추가한다
// 최신 이벤트가 맨 위에 표시되도록 insertBefore를 사용한다
// type: 이벤트 타입 문자열 (RESERVATION_CREATED 등)
// data: EventResponse 객체 (id, eventType, channelId, roomTypeId, eventPayload, createdAt)
function addEventRow(type, data) {
    var tbody = document.getElementById('event-list');

    // 빈 상태 메시지가 있으면 제거한다
    var emptyRow = tbody.querySelector('.events__empty');
    if (emptyRow) {
        emptyRow.remove();
    }

    // 새 행(tr) 요소를 생성한다
    var row = document.createElement('tr');
    // event-row--new 클래스로 페이드인 애니메이션을 적용한다
    row.className = 'event-row--new';

    // eventPayload에서 주요 정보를 추출하여 상세 열에 표시한다
    var detail = parseEventDetail(type, data.eventPayload);

    // 이벤트 타입을 kebab-case로 변환한다 (CSS 클래스에 사용)
    // RESERVATION_CREATED → reservation-created
    var badgeClass = 'badge--' + type.toLowerCase().replace(/_/g, '-');

    // 이벤트 타입의 한글 표시명
    var typeLabel = getEventTypeLabel(type);

    // 시간 포맷팅 — createdAt을 HH:MM:SS 형식으로 변환한다
    var timeStr = formatTime(data.createdAt);

    // 행의 HTML 내용을 설정한다
    // innerHTML: 문자열로 HTML 구조를 정의한다
    row.innerHTML =
        '<td>' + timeStr + '</td>' +
        '<td><span class="badge ' + badgeClass + '">' + typeLabel + '</span></td>' +
        '<td>' + (data.channelId || '—') + '</td>' +
        '<td>' + (data.roomTypeId || '—') + '</td>' +
        '<td><span class="event-detail">' + detail + '</span></td>';

    // insertBefore — 첫 번째 자식 앞에 삽입하여 최신 이벤트를 맨 위에 표시한다
    // appendChild를 쓰면 맨 아래에 추가되므로, insertBefore를 사용한다
    tbody.insertBefore(row, tbody.firstChild);

    // 최대 표시 개수를 초과하면 오래된 이벤트(맨 아래)를 제거한다
    // 너무 많은 DOM 노드는 브라우저 성능을 저하시킨다
    while (tbody.children.length > MAX_EVENTS) {
        tbody.removeChild(tbody.lastChild);
    }
}

// ==============================
// 5. 이벤트 상세 정보 파싱
// ==============================
// eventPayload JSON 문자열에서 주요 정보를 추출하여 읽기 쉬운 형태로 반환한다
// type: 이벤트 타입, payload: JSON 문자열 (nullable)
function parseEventDetail(type, payload) {
    // payload가 없으면 빈 문자열 반환
    if (!payload) return '—';

    try {
        // JSON 문자열을 객체로 파싱한다
        var obj = JSON.parse(payload);

        // 이벤트 타입별로 다른 정보를 표시한다
        if (type === 'RESERVATION_CREATED') {
            // 예약 생성: 게스트 이름과 객실 수
            return (obj.guestName || '') + ' (' + (obj.roomQuantity || 1) + '실)';
        } else if (type === 'INVENTORY_UPDATED') {
            // 재고 변경: 변경 전→후 수량과 날짜
            return (obj.stockDate || '') + ' ' + (obj.before || '?') +
                ' → ' + (obj.after || '?');
        } else if (type === 'CHANNEL_SYNCED') {
            // 채널 동기화: 채널 이름과 동기화된 수량
            return (obj.channel || '') + ' (수량: ' + (obj.syncedQuantity || '?') + ')';
        } else if (type === 'RESERVATION_CANCELLED') {
            // 예약 취소: 게스트 이름과 사유
            return (obj.guestName || '') + ' - ' + (obj.reason || '');
        }

        // 알 수 없는 타입이면 JSON 문자열 그대로 표시한다
        return payload;
    } catch (e) {
        // JSON 파싱 실패 시 원본 문자열을 그대로 표시한다
        return payload || '—';
    }
}

// ==============================
// 6. 이벤트 타입 한글 라벨
// ==============================
// 영문 이벤트 타입을 한글 라벨로 변환한다
function getEventTypeLabel(type) {
    var labels = {
        RESERVATION_CREATED: '예약 생성',
        INVENTORY_UPDATED: '재고 변경',
        CHANNEL_SYNCED: '채널 동기화',
        RESERVATION_CANCELLED: '예약 취소'
    };
    // labels에 없으면 원본 타입 문자열을 반환한다
    return labels[type] || type;
}

// ==============================
// 7. 시간 포맷팅
// ==============================
// ISO 날짜-시간 문자열을 HH:MM:SS 형식으로 변환한다
// createdAt: "2026-03-15T14:23:01" → "14:23:01"
function formatTime(createdAt) {
    if (!createdAt) return '—';

    try {
        // Date 객체로 변환한다
        var date = new Date(createdAt);
        // 유효하지 않은 날짜인지 확인한다
        if (isNaN(date.getTime())) return createdAt;

        // HH:MM:SS 형식으로 변환한다
        // padStart(2, '0'): 한 자리 숫자 앞에 0을 붙인다 (예: 9 → 09)
        var hours = String(date.getHours()).padStart(2, '0');
        var minutes = String(date.getMinutes()).padStart(2, '0');
        var seconds = String(date.getSeconds()).padStart(2, '0');
        return hours + ':' + minutes + ':' + seconds;
    } catch (e) {
        return createdAt;
    }
}

// ==============================
// 8. 통계 카운터 업데이트
// ==============================
// 이벤트 수신 시 해당 타입의 카운터를 1 증가시키고 DOM에 반영한다
function incrementCounter(type) {
    // eventCounts 객체에서 해당 타입의 카운터를 증가시킨다
    if (eventCounts[type] !== undefined) {
        eventCounts[type]++;
    }

    // DOM의 카운터 요소를 업데이트한다
    // id 규칙: count-{타입의 kebab-case}
    // 예: RESERVATION_CREATED → count-reservation-created
    var elementId = 'count-' + type.toLowerCase().replace(/_/g, '-');
    var element = document.getElementById(elementId);
    if (element) {
        element.textContent = eventCounts[type];
    }
}

// ==============================
// 9. 시뮬레이터 제어
// ==============================
// POST /api/simulator/start — 채널 시뮬레이터를 시작한다
// Phase 3에서 구현한 SimulatorController가 이 엔드포인트를 제공한다
// async/await: fetch API는 Promise를 반환하므로 비동기 처리한다
async function startSimulator() {
    try {
        // fetch(): 브라우저 내장 HTTP 클라이언트
        // method: 'POST': HTTP POST 요청
        var response = await fetch('/api/simulator/start', { method: 'POST' });
        var result = await response.json(); // JSON 응답 파싱
        updateSimulatorUI(result);          // UI 업데이트
    } catch (error) {
        // 네트워크 오류 등 예외 처리
        console.error('시뮬레이터 시작 실패:', error);
    }
}

// POST /api/simulator/stop — 채널 시뮬레이터를 중지한다
async function stopSimulator() {
    try {
        var response = await fetch('/api/simulator/stop', { method: 'POST' });
        var result = await response.json();
        updateSimulatorUI(result);
    } catch (error) {
        console.error('시뮬레이터 중지 실패:', error);
    }
}

// GET /api/simulator/status — 시뮬레이터 현재 상태를 조회한다
async function loadSimulatorStatus() {
    try {
        var response = await fetch('/api/simulator/status');
        var result = await response.json();
        updateSimulatorUI(result);
    } catch (error) {
        // 서버 미기동 시 에러 — 상태를 "확인 불가"로 표시한다
        document.getElementById('simulator-status').textContent = '확인 불가';
    }
}

// 시뮬레이터 상태에 따라 UI를 업데이트한다
// result: { running: true/false, ... }
function updateSimulatorUI(result) {
    var statusEl = document.getElementById('simulator-status');
    var btnStart = document.getElementById('btn-start');
    var btnStop = document.getElementById('btn-stop');

    // running 필드로 실행 여부를 판단한다
    var isRunning = result.running === true;

    // 상태 텍스트 업데이트
    statusEl.textContent = isRunning ? '실행 중' : '중지됨';
    // 실행 중이면 초록색, 중지면 회색으로 표시
    statusEl.style.color = isRunning ? '#10b981' : '#6b7280';

    // 버튼 활성/비활성 토글 — 실행 중이면 시작 버튼 비활성화
    btnStart.disabled = isRunning;
    btnStop.disabled = !isRunning;
}

// ==============================
// 10. 최근 이벤트 로드
// ==============================
// 페이지 로드 시 GET /api/events로 최근 이벤트를 조회하여 테이블에 표시한다
// SSE는 구독 이후의 이벤트만 수신하므로, 과거 이벤트는 REST API로 별도 조회한다
async function loadRecentEvents() {
    try {
        // limit=50: 최근 50개 이벤트를 조회한다
        var response = await fetch('/api/events?limit=50');
        var events = await response.json();

        if (events.length === 0) {
            // 이벤트가 없으면 안내 메시지를 표시한다
            showEmptyMessage();
            return;
        }

        // 서버에서 최신순으로 정렬하여 반환하므로,
        // 역순으로 추가하면 최신 이벤트가 맨 위에 위치한다
        // reverse(): 배열을 뒤집어서 오래된 이벤트부터 추가한다
        // addEventRow는 insertBefore로 맨 위에 추가하므로,
        // 결과적으로 최신 이벤트가 맨 위에 온다
        events.reverse().forEach(function (event) {
            addEventRow(event.eventType, event);
            // 초기 로드 이벤트도 카운터에 반영한다
            incrementCounter(event.eventType);
        });
    } catch (error) {
        console.error('최근 이벤트 로드 실패:', error);
        showEmptyMessage();
    }
}

// ==============================
// 11. 빈 상태 메시지
// ==============================
// 이벤트가 없을 때 테이블에 안내 메시지를 표시한다
function showEmptyMessage() {
    var tbody = document.getElementById('event-list');
    // 기존 내용이 있으면 건너뛴다
    if (tbody.children.length > 0) return;

    // colspan=5: 5개 열을 합쳐서 중앙에 메시지를 표시한다
    var row = document.createElement('tr');
    row.className = 'events__empty';
    row.innerHTML =
        '<td colspan="5" style="text-align: center; padding: 40px; color: #6b7280;">' +
        '이벤트가 없습니다. 시뮬레이터를 시작하면 실시간 이벤트가 표시됩니다.' +
        '</td>';
    tbody.appendChild(row);
}

// ==============================
// 12. 이벤트 목록 초기화
// ==============================
// 화면의 이벤트 행만 지우고 SSE 연결은 유지한다
// 통계 카운터도 0으로 초기화한다
function clearEvents() {
    // 이벤트 테이블 초기화
    var tbody = document.getElementById('event-list');
    tbody.innerHTML = ''; // 모든 자식 요소 제거

    // 통계 카운터 초기화
    EVENT_TYPES.forEach(function (type) {
        eventCounts[type] = 0;
        var elementId = 'count-' + type.toLowerCase().replace(/_/g, '-');
        var element = document.getElementById(elementId);
        if (element) {
            element.textContent = '0';
        }
    });

    // 빈 상태 메시지 표시
    showEmptyMessage();
}

// ==============================
// 13. 페이지 초기화
// ==============================
// DOMContentLoaded — HTML 파싱이 완료된 후 실행된다
// 이미지/CSS 로딩을 기다리지 않으므로 load 이벤트보다 빠르다
document.addEventListener('DOMContentLoaded', function () {
    // 1. SSE 연결을 수립한다 — 실시간 이벤트 수신 시작
    connectSSE();

    // 2. 시뮬레이터 현재 상태를 조회한다
    loadSimulatorStatus();

    // 3. 최근 이벤트를 로드하여 테이블에 표시한다
    // SSE는 구독 이후의 이벤트만 수신하므로, 과거 이벤트는 REST API로 로드한다
    loadRecentEvents();
});

// beforeunload — 페이지를 떠날 때 SSE 연결을 정리한다
// 브라우저 탭 닫기, 새로고침, 다른 페이지로 이동 시 호출된다
// SSE 연결을 명시적으로 닫아 서버 리소스를 해제한다
window.addEventListener('beforeunload', function () {
    if (eventSource) {
        eventSource.close(); // SSE 연결 종료
    }
});
