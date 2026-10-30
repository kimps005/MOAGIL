/* MOAGIL route planner. Map tiles and road routes use OpenStreetMap data. */
const app = document.getElementById('app');
const captureMode = new URLSearchParams(location.search).has('capture');
let activeMaps = [];
let passengerMapController = null;
let mapConfigPromise = null;
let naverMapScriptPromise = null;
const state = {
  session: null,
  credentials: null,
  parked: false,
  navConfirmed: false,
  navMessage: '',
  pin: { lat: 37.525, lon: 127.000, label: '시연 지점' },
  invite: null,
  joinId: null,
  joinInfo: null,
  joined: false,
  pinMode: 'passenger',
  saving: false,
  showPhone: false,
  mapProvider: 'osm',
  routingProvider: 'osrm',
};

const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
const fmtNumber = value => new Intl.NumberFormat('ko-KR').format(value);
const shortId = value => String(value || '').slice(0, 8).toUpperCase();
const byId = id => document.getElementById(id);

async function api(path, options = {}, token = '') {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers.Authorization = 'Bearer ' + token;
  let response;
  try {
    response = await fetch(path, { ...options, headers, cache: 'no-store' });
  } catch (_error) {
    throw new Error('서버에 연결할 수 없습니다. QR 주소와 PC 시연 서버가 켜져 있는지 확인해 주세요.');
  }
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error?.message || '요청을 처리하지 못했습니다.');
  return payload;
}

function toast(message) {
  let node = byId('toast');
  if (!node) {
    node = document.createElement('div');
    node.id = 'toast';
    node.className = 'toast';
    document.body.appendChild(node);
  }
  node.textContent = message;
  node.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => node.classList.remove('show'), 2800);
}

function saveCredentials(credentials) {
  state.credentials = credentials;
  sessionStorage.setItem('moagil-driver', JSON.stringify(credentials));
}

function loadCredentials() {
  try { return JSON.parse(sessionStorage.getItem('moagil-driver') || 'null'); }
  catch (_error) { return null; }
}

function formatDuration(ms) {
  if (!Number.isFinite(ms)) return '—';
  const mins = Math.max(1, Math.round(ms / 60000));
  return mins >= 60 ? Math.floor(mins / 60) + '시간 ' + (mins % 60) + '분' : mins + '분';
}

function formatDistance(meters) {
  if (!Number.isFinite(meters)) return '—';
  return meters >= 1000 ? (meters / 1000).toFixed(1) + ' km' : fmtNumber(meters) + ' m';
}

function currentOrder() {
  if (!state.session) return [];
  const order = state.session.selectedOrder || state.session.routeSnapshot?.order || [];
  const by = new Map(state.session.passengers.map(person => [person.id, person]));
  return order.map(id => by.get(id)).filter(Boolean);
}

async function loadMapConfig() {
  if (!mapConfigPromise) {
    mapConfigPromise = api('/api/config').then(config => {
      state.mapProvider = config.mapProvider || 'osm';
      state.routingProvider = config.routingProvider || 'osrm';
      return config;
    }).catch(() => {
      state.mapProvider = 'osm';
      state.routingProvider = 'osrm';
      return { mapProvider: 'osm', routingProvider: 'osrm', naverMapClientId: '' };
    });
  }
  return mapConfigPromise;
}

async function getMapAdapter() {
  const config = await loadMapConfig();
  if (config.mapProvider === 'naver' && config.naverMapClientId) {
    if (!window.naver?.maps) {
      if (!naverMapScriptPromise) {
        naverMapScriptPromise = new Promise((resolve, reject) => {
          const script = document.createElement('script');
          script.src = 'https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=' + encodeURIComponent(config.naverMapClientId);
          script.async = true;
          script.onload = resolve;
          script.onerror = () => reject(new Error('네이버 지도 API를 불러오지 못했습니다. Client ID와 등록 도메인을 확인해 주세요.'));
          document.head.appendChild(script);
        });
      }
      await naverMapScriptPromise;
    }
    if (!window.MOAGILNaverMaps) throw new Error('네이버 지도 모듈을 초기화하지 못했습니다.');
    return window.MOAGILNaverMaps;
  }
  if (!window.MOAGILMaps) throw new Error('OpenStreetMap 모듈을 불러오지 못했습니다.');
  return window.MOAGILMaps;
}

function passengerRows() {
  const people = state.session?.passengers || [];
  if (!people.length) return '<div class="empty-list">아직 등록된 동승자가 없습니다.<br>QR을 공유하고 각자의 하차 위치를 받아보세요.</div>';
  return '<div class="passenger-list">' + people.map((person, index) => '<div class="passenger-row"><div class="avatar">' + String.fromCharCode(65 + index) + '</div><div class="passenger-main"><div class="passenger-name">' + esc(person.displayName) + '</div><div class="passenger-point">' + esc(person.point.label || '시연 위치') + ' · ' + person.point.lat.toFixed(3) + ', ' + person.point.lon.toFixed(3) + '</div></div>' + (['COLLECTING', 'PLANNED'].includes(state.session.state) ? '<button class="icon-btn" data-action="remove" data-id="' + esc(person.id) + '" aria-label="' + esc(person.displayName) + ' 삭제">×</button>' : '') + '</div>').join('') + '</div>';
}

function routeRows() {
  const order = currentOrder();
  if (!state.session?.routeSnapshot || !order.length) return '<div class="empty-list">동승자가 한 명 이상 등록되고 정차 상태일 때<br>순서를 계산할 수 있습니다.</div>';
  const editable = state.session.state === 'PLANNED' && state.parked;
  const items = order.map((person, index) => '<li class="order-item"><span class="order-number">' + (index + 1) + '</span><div><div class="order-name">' + esc(person.displayName) + '</div><div class="order-destination">' + esc(person.point.label || '시연 지점') + '</div></div><div class="order-move">' + (editable ? '<button class="icon-btn" data-action="move" data-index="' + index + '" data-delta="-1" aria-label="위로 이동" ' + (index === 0 || (state.session.priorityPassengerId && index === 1) ? 'disabled' : '') + '>↑</button><button class="icon-btn" data-action="move" data-index="' + index + '" data-delta="1" aria-label="아래로 이동" ' + (index === order.length - 1 || (state.session.priorityPassengerId && index === 0) ? 'disabled' : '') + '>↓</button>' : '') + '</div></li>').join('');
  const providerLabel = state.routingProvider === 'naver' ? 'NAVER 실시간 경로' : state.routingProvider === 'osrm' ? '실제 도로 경로' : '모의 계산';
  return '<div class="route-panel"><div class="route-top"><div><div class="route-total">' + formatDuration(state.session.routeSnapshot.durationMs) + ' <span>예상 시간</span></div><div class="route-meta">' + formatDistance(state.session.routeSnapshot.distanceM) + ' · ' + (state.session.routeSnapshot.evaluatedCount || 1) + '가지 순서 비교</div></div><div class="mock-pill">' + providerLabel + '</div></div><ol class="order-list">' + items + '</ol></div>';
}

function activeDestination() {
  const order = currentOrder();
  const index = state.session.activeIndex || 0;
  if (index < order.length) return { label: order[index].displayName, point: order[index].point, type: '승객 하차' };
  if (state.session.finalPoint) return { label: state.session.finalPoint.label, point: state.session.finalPoint, type: '운전자 최종 목적지' };
  return { label: '모든 하차 지점', point: null, type: '이동 마무리' };
}

function driverMarkup() {
  const hasSession = Boolean(state.session);
  const finalEnabled = Boolean(state.session?.finalPoint);
  const collecting = state.session && ['COLLECTING', 'PLANNED'].includes(state.session.state);
  const order = currentOrder();
  const destination = state.session?.state === 'ACTIVE' ? activeDestination() : null;
  const currentPassenger = destination && (state.session.activeIndex || 0) < order.length;
  const finalArrived = destination && !currentPassenger && Boolean(state.session.finalPoint);
  const mapName = state.mapProvider === 'naver' ? '네이버 지도' : 'OpenStreetMap';
  const routeProviderLabel = state.routingProvider === 'naver' ? '네이버 Directions · 실시간 교통' : state.routingProvider === 'osrm' ? 'OSRM · 실시간 교통 없음' : '모의 경로';
  const mobileJoin = state.credentials ? state.credentials.joinBaseUrl + '/join/' + state.session.sessionId + '?invite=' + encodeURIComponent(state.credentials.inviteToken) : '';
  const capture = captureMode;
  const stepIndex = !hasSession ? 0 : state.session.state === 'ACTIVE' ? 2 : state.session.state === 'PLANNED' ? 1 : 0;
  const workflowHint = !hasSession
    ? '빈 이동을 만들거나 예시 데이터로 먼저 화면을 살펴보세요.'
    : state.session.state === 'ACTIVE'
      ? '현재 목적지에 도착한 뒤 완료를 누르면 다음 목적지로 이어집니다.'
      : state.session.state === 'PLANNED'
        ? '지도와 추천 순서를 확인하고 안내를 시작하세요.'
        : 'QR을 공유해 위치를 모은 뒤, 정차 확인 후 최적 순서를 계산하세요.';
  const workflow = '<section class="workflow-card" aria-label="이동 진행 단계"><ol class="workflow-list">'
    + ['이동 만들기 · 위치 모으기', '도로 경로로 순서 확인', '순서 확정 · 단계별 안내'].map((label, index) => '<li class="workflow-step ' + (index === stepIndex ? 'is-current' : index < stepIndex ? 'is-done' : '') + '" ' + (index === stepIndex ? 'aria-current="step"' : '') + '><span class="workflow-number">' + (index < stepIndex ? '✓' : index + 1) + '</span><span>' + label + '</span></li>').join('')
    + '</ol><p class="workflow-hint">' + workflowHint + '</p></section>';
  let pageTitle = hasSession ? (state.session.state === 'ACTIVE' ? '다음 목적지를 확인하고 이동을 이어가세요.' : '모두의 하차 지점을 한눈에 확인하세요.') : '여러 목적지를 모아, 좋은 순서를 찾아요.';
  let pageSub = hasSession ? (state.session.state === 'ACTIVE' ? '운전자가 도착을 확인하면 다음 하차 지점으로 넘어갑니다.' : 'QR로 위치를 받고 실제 도로망으로 순서를 비교합니다.') : '이동을 만들고 QR을 공유하면 각자 내릴 곳을 등록할 수 있어요.';
  const phoneUrl = capture && state.credentials ? location.origin + '/join/' + state.session.sessionId + '?invite=' + encodeURIComponent(state.credentials.inviteToken) : '';
  const phonePreview = capture && state.showPhone ? '<div class="preview-backdrop"><section class="preview-panel"><div class="preview-copy"><div class="eyebrow">시연용 브라우저 · 실제 휴대폰 연결 아님</div><h2>동승자가 모바일 화면에서<br>직접 핀을 등록해요.</h2><p class="subcopy">화면 오른쪽의 휴대폰은 이 브라우저 안에서 열린 참가 페이지입니다. 실제 QR은 촬영 보호 모드로 가렸습니다.</p><button class="btn" data-action="close-preview">미리보기 닫기</button></div><div class="phone-device"><div class="phone-notch"></div><iframe title="시연용 참가자 모바일 화면" src="' + esc(phoneUrl) + '"></iframe></div></section></div>' : '';
  return '<div class="app-shell">'
    + '<header class="topbar"><a class="brand" href="/"><span class="brand-mark">M</span><span><span class="brand-name">MOAGIL</span><span class="brand-sub">모아길 · 함께 가는 길</span></span></a><div class="nav-right"><span class="mock-pill">실제 도로 경로 · 안내 시연</span>'
    + '<button class="park-toggle ' + (state.parked ? 'is-parked' : '') + '" data-action="park" aria-pressed="' + state.parked + '" title="테스트용 상태입니다. 차량 센서를 읽지 않습니다.">' + (state.parked ? '✓ 정차 확인됨 · 시연' : '정차 상태 확인 · 시연') + '</button><span class="status-pill"><i class="status-dot ' + (hasSession ? '' : 'off') + '"></i>' + (hasSession ? state.session.state === 'ACTIVE' ? '이동 진행 중' : '서버 연결됨' : '새 이동 대기') + '</span></div></header>'
    + '<section class="page-title"><div><div class="eyebrow">공동 이동 · 차량 화면</div><h1>' + pageTitle + '</h1><p class="subcopy">' + pageSub + '</p></div><div class="button-row"><button class="btn" data-action="new">새 이동 만들기</button><button class="btn primary" data-action="demo">예시 데이터로 시작</button></div></section>'
    + workflow
    + (hasSession ? '<section class="dashboard-grid">'
      + '<div class="card">'
      + '<div class="card-pad"><div class="card-head"><div><div class="card-kicker">1단계 · 위치 모으기 · ' + shortId(state.session.sessionId) + '</div><h2 class="card-title">동승자를 초대하세요</h2></div><span class="status-pill">' + (state.session.passengers.length) + ' / 4명</span></div>'
      + '<div class="recruit-card"><div class="recruit-copy"><div class="eyebrow">QR로 바로 참여</div><h2>각자 내릴 곳을<br>직접 등록해요.</h2><p class="mini-note">앱 설치나 회원가입 없이 모바일 브라우저에서 참여할 수 있어요.</p><div class="join-link"><span class="link-text">' + esc(state.credentials?.joinBaseUrl || location.origin) + '/join/' + shortId(state.session.sessionId) + '?invite=••••••••</span><button class="tiny-button" data-action="copy">링크 복사</button></div>' + (capture ? '<button class="tiny-button" style="margin-top:9px" data-action="preview">휴대폰 미리보기 열기 →</button>' : '') + '</div><div class="qr-card ' + (capture ? 'capture-mode' : '') + '" id="qr-card">' + (capture ? '<div class="qr-masked"></div><span>촬영 보호 · 초대 QR 가림</span>' : '<canvas id="invite-qr" aria-label="모바일 초대 QR 코드"></canvas><span>모바일 카메라로 스캔</span>') + '</div></div>'
      + '<div class="stat-grid"><div class="stat-box"><div class="stat-label">참여 인원</div><div class="stat-value">' + state.session.passengers.length + '<small> / 4</small></div></div><div class="stat-box"><div class="stat-label">계산된 순서</div><div class="stat-value">' + (state.session.routeSnapshot?.evaluatedCount || '—') + '<small> 가지</small></div></div><div class="stat-box"><div class="stat-label">세션 상태</div><div class="stat-value" style="font-size:14px">' + (state.session.state === 'COLLECTING' ? '모집 중' : state.session.state === 'PLANNED' ? '순서 확인' : state.session.state === 'ACTIVE' ? '안내 중' : '완료') + '</div></div></div></div>'
      + '</div>'
      + '<div class="card map-card"><div class="map-topline"><span class="map-label">⌁ ' + mapName + ' 실제 도로 지도</span><span class="map-label">' + (state.session.routeSnapshot?.geometry?.length ? routeProviderLabel : '경로 계산 후 도로 경로를 표시합니다') + '</span></div><div id="driver-map" class="driver-map" role="application" aria-label="' + mapName + ' 지도와 실제 도로 경로"></div><div class="map-legend"><span class="legend-item"><i class="legend-dot dark"></i>출발·최종 목적지</span><span class="legend-item"><i class="legend-dot orange"></i>다음 하차</span><span class="legend-item"><i class="legend-dot"></i>동승자 위치</span></div></div>'
      + '</section>'
      + '<section class="content-grid">'
      + '<div class="card card-pad"><div class="card-head"><div><div class="card-kicker">현재 세션</div><h2 class="card-title">동승자 위치</h2></div><span class="mini-note">' + (collecting ? '등록 후 2초 이내 반영' : state.session.state === 'ACTIVE' ? '모의 안내 중' : '세션 정보') + '</span></div>' + passengerRows() + '</div>'
        + '<div class="card card-pad"><div class="card-head"><div><div class="card-kicker">2단계 · 경로 비교</div><h2 class="card-title">추천 하차 순서</h2></div><span class="mini-note">' + routeProviderLabel + '</span></div>'
      + (state.session.state === 'ACTIVE' ? '<div class="active-card"><div class="safety-banner ready">● 모의 내비 안내 · 운전자 정차 확인 상태</div><div class="mini-note">현재 단계 · ' + esc(destination.type) + '</div><div class="active-destination"><small>다음 안내 목적지</small><strong>' + esc(destination.label) + '</strong><small>' + (destination.point ? destination.point.lat.toFixed(5) + ', ' + destination.point.lon.toFixed(5) : '하차 단계가 모두 끝났습니다') + '</small></div><div class="route-actions">' + (currentPassenger ? '<button class="btn primary" data-action="complete-stop">하차 완료 · 시연</button>' : '<button class="btn primary" data-action="complete-trip">' + (finalArrived ? '최종 목적지 도착 · 시연' : '이동 마무리 · 시연') + '</button>') + '<button class="btn warn" data-action="end">이동 취소</button></div></div>'
      : '<div class="safety-banner ' + (state.parked ? 'ready' : '') + '">' + (state.parked ? '● 정차(시연) 상태입니다. 이 상태는 실제 차량 감지가 아닙니다.' : '○ 운전자 입력은 정차 상태에서만 열립니다. 정차 확인을 눌러 시연을 진행하세요.') + '</div>'
        + routeRows()
         + (state.routingProvider === 'naver' && collecting ? '<div class="notice-bar">네이버 Directions 5로 최대 24개 순서를 비교합니다. 계산 한 번에 최대 24회 API 요청이 발생하며 계정 쿼터·요금이 적용될 수 있습니다.</div>' : '')
        + '<div class="settings"><div class="field"><label for="priority">먼저 내릴 동승자</label><select id="priority" ' + (!state.parked || !collecting ? 'disabled' : '') + '><option value="">지정하지 않음</option>' + state.session.passengers.map(person => '<option value="' + esc(person.id) + '" ' + (person.id === state.session.priorityPassengerId ? 'selected' : '') + '>' + esc(person.displayName) + '</option>').join('') + '</select></div><div class="field"><label for="final-choice">운전자 최종 목적지</label><select id="final-choice" ' + (!state.parked || !collecting ? 'disabled' : '') + '><option value="none" ' + (!finalEnabled ? 'selected' : '') + '>마지막 하차에서 종료</option><option value="preset" ' + (finalEnabled ? 'selected' : '') + '>시연 최종 목적지 포함</option></select></div></div>'
        + '<div class="route-actions">' + (state.session.state === 'PLANNED' ? '<button class="btn" data-action="plan" ' + (!state.parked ? 'disabled' : '') + '>순서 다시 계산</button><button class="btn primary" data-action="start" ' + (!state.parked ? 'disabled' : '') + '>이 순서로 안내 시작</button>' : '<button class="btn primary" data-action="plan" ' + (!state.parked || state.session.passengers.length < 1 ? 'disabled' : '') + '>최적 순서 계산</button>') + (state.session.state !== 'ACTIVE' ? '<button class="btn warn" data-action="end">이동 종료</button>' : '') + '</div>')
      + '</div></section>'
      : '<section class="dashboard-grid"><div class="card card-pad"><div class="eyebrow">실제 도로망 경로 계산</div><h2 class="card-title" style="font-size:23px;margin:8px 0 12px">각자 핀을 놓으면,<br>모두의 하차 순서를 찾습니다.</h2><p class="subcopy" style="font-size:12px">' + mapName + ' 지도와 ' + routeProviderLabel + '으로 최대 24개 순서를 비교합니다. 실제 차량 내비 안내는 포함하지 않습니다.</p><div class="route-actions" style="margin-top:18px"><button class="btn primary" data-action="new">빈 이동 만들기</button><button class="btn" data-action="demo">예시 4명으로 둘러보기</button></div><div class="notice-bar"><strong>사용 흐름</strong><br>① 이동 만들기 → ② 정차 상태 확인 → ③ QR 공유 및 하차 위치 등록 → ④ 실제 도로 경로 계산 → ⑤ 단계별 모의 안내</div></div><div class="card map-card"><div class="map-topline"><span class="map-label">' + mapName + ' 실제 도로 지도</span><span class="map-label">' + routeProviderLabel + '</span></div><div id="driver-map" class="driver-map" role="application" aria-label="' + mapName + ' 실제 도로 지도"></div><div class="map-legend"><span class="legend-item"><i class="legend-dot dark"></i>출발지</span><span class="legend-item"><i class="legend-dot"></i>하차 위치</span></div></div></section>')
     + '<div class="notice-bar"><strong>연동 범위 안내</strong> · 지도와 경로는 설정한 실제 지도·경로 서비스를 사용합니다. 실시간 교통은 네이버 경로 제공자 사용 시에만 반영되며, 차량 센서·Pleos 내비 연동과 안전한 정차 판단은 시연 범위에 포함되지 않습니다.</div>'
    + '<footer class="footer"><span>MOAGIL · 모아길 프로토타입</span><span>시연용 데이터 및 모의 기능 · 실차 주행에 사용하지 마세요</span></footer></div>'
    + (capture ? '<div class="capture-chip">촬영 보호 모드 · 초대 토큰 가림</div>' : '') + phonePreview;
}

function joinMarkup() {
  document.body.classList.add('passenger-body');
  const error = state.joinError || '';
  const joined = state.joined;
  const mapName = state.mapProvider === 'naver' ? '네이버 지도' : 'OpenStreetMap';
  const routePrivacy = state.routingProvider === 'naver' ? '하차 좌표가 이 서버와 네이버 Directions로 전송되어 실시간 교통을 반영한 실제 도로 경로를 계산합니다.' : '하차 좌표가 이 서버와 공개 OSRM 경로 서버로 전송되어 실제 도로 경로를 계산합니다.';
  const heading = '<main class="passenger-shell"><header class="passenger-top"><a class="brand" href="/"><span class="brand-mark">M</span><span class="brand-name">MOAGIL</span></a><span class="phone-badge">앱 설치 없이 참여</span></header>'
    + '<section class="passenger-heading"><div class="eyebrow">동승자 참여 · ' + shortId(state.joinId) + '</div><h1>' + (joined ? '위치 등록이 완료됐어요.' : '내릴 곳을 직접<br>핀으로 알려주세요.') + '</h1><p>' + (joined ? '운전자 화면에서 확인할 수 있습니다. 이 탭을 닫아도 괜찮아요.' : '별명과 하차 위치만 등록하면 됩니다. 회원가입은 필요하지 않아요.') + '</p></section>';
  if (!state.joinInfo) return heading + '<div class="loading-card">초대 링크를 확인하고 있어요…</div><footer class="footer"><span>모아길 · 시연용 모바일 웹</span></footer></main>';
  if (joined) return heading + '<section class="phone-card passenger-result"><div class="success-mark">✓</div><h2>등록 완료 · ' + esc(state.joinedName) + '</h2><p>운전자 화면에서 위치를 확인합니다.<br>위치 수정은 시연 범위에 포함되지 않습니다.</p></section><footer class="footer"><span>모아길 · 시연용 모바일 웹</span></footer></main>';
  return heading + '<form id="passenger-form"><section class="phone-card"><h2>어떤 이름으로 표시할까요?</h2><div class="phone-field"><label for="display-name">별명 · 1~20자</label><input id="display-name" name="displayName" maxlength="20" placeholder="예: 민수" autocomplete="nickname" required></div><div class="phone-map-top"><span>실제 지도에서 누르거나 핀을 끌어 하차 위치를 선택해요</span><span>⌖ ' + mapName + '</span></div><div id="pin-map" class="pin-map" role="application" aria-label="' + mapName + ' 도로 지도. 눌러서 하차 위치 선택" tabindex="0"></div>'
    + '<div class="coord-readout"><span>위도 <strong id="read-lat">' + state.pin.lat.toFixed(5) + '</strong></span><span>경도 <strong id="read-lon">' + state.pin.lon.toFixed(5) + '</strong></span></div>'
      + '<div class="coord-inputs"><div class="phone-field"><label for="coord-lat">위도 입력</label><input id="coord-lat" type="number" min="-90" max="90" step="0.000001" value="' + state.pin.lat.toFixed(6) + '" aria-label="위도"></div><div class="phone-field"><label for="coord-lon">경도 입력</label><input id="coord-lon" type="number" min="-180" max="180" step="0.000001" value="' + state.pin.lon.toFixed(6) + '" aria-label="경도"></div></div><div class="phone-field" style="margin-top:13px"><label for="place-label">위치 이름 · 선택</label><input id="place-label" name="label" maxlength="80" value="' + esc(state.pin.label) + '" placeholder="시연 지점"></div><div class="privacy-note"><span>ⓘ</span><span>' + routePrivacy + ' 시연용 좌표를 사용하세요. 실시간 교통 정보 외에 안전한 정차 여부는 보장하지 않아요.</span></div><div class="phone-error" id="phone-error">' + esc(error) + '</div></section><div class="phone-submit-bar"><button class="phone-submit" type="submit" ' + (state.saving ? 'disabled' : '') + '>위치 등록</button></div></form><footer class="footer"><span>모아길 · 시연용 모바일 웹</span></footer></main>';
}

function render() {
  destroyLeafletMaps();
  if (location.pathname.startsWith('/join/')) {
    app.innerHTML = joinMarkup();
    bindPassengerMap();
    return;
  }
  document.body.classList.remove('passenger-body');
  app.innerHTML = driverMarkup();
  decorateActiveNavigation();
  const canvas = byId('invite-qr');
  if (canvas && state.credentials) {
    const joinUrl = state.credentials.joinBaseUrl + '/join/' + state.session.sessionId + '?invite=' + encodeURIComponent(state.credentials.inviteToken);
    try { drawQr(canvas, joinUrl); } catch (_error) { toast('초대 QR을 표시하지 못했습니다. 주소를 복사해 사용해 주세요.'); }
  }
  bindDriverMap();
}

function destroyLeafletMaps() {
  activeMaps.forEach(instance => instance.destroy?.());
  activeMaps = [];
  passengerMapController = null;
}

async function bindDriverMap() {
  const element = byId('driver-map');
  if (!element) return;
  try {
    const maps = await getMapAdapter();
    if (!element.isConnected) return;
    const instance = maps.createDriverMap(element, state.session);
    activeMaps.push(instance.destroy ? instance : { destroy: () => instance.remove() });
  } catch (_error) {
    if (element.isConnected) element.innerHTML = '<div class="map-error">네이버 지도를 불러오지 못했어요.<br>Client ID, 등록 URL, Dynamic Map 설정을 확인해 주세요.</div>';
  }
}

function decorateActiveNavigation() {
  const card = app.querySelector('.active-card');
  if (!card) return;
  const banner = card.querySelector('.safety-banner');
  if (banner) {
    banner.classList.toggle('ready', state.navConfirmed);
    banner.textContent = state.navMessage || (state.navConfirmed
      ? '● 모의 안내 확인 · 실제 차량 내비는 연결되지 않았습니다.'
      : '○ 안내 결과 확인 전에는 하차 완료와 이동 종료를 잠급니다.');
  }
  const complete = card.querySelector('[data-action="complete-stop"], [data-action="complete-trip"]');
  if (complete) complete.disabled = !state.navConfirmed;
  const actions = card.querySelector('.route-actions');
  if (actions) {
    const navigationButton = document.createElement('button');
    navigationButton.className = 'btn';
    navigationButton.dataset.action = state.navConfirmed ? 'nav-fail' : 'nav-retry';
    navigationButton.textContent = state.navConfirmed ? '안내 실패 시연' : '모의 안내 다시 요청';
    actions.prepend(navigationButton);
  }
}

async function requestMockNavigation() {
  state.navConfirmed = false;
  state.navMessage = '모의 내비 안내 요청 중…';
  render();
  await new Promise(resolve => setTimeout(resolve, 500));
  if (state.session?.state !== 'ACTIVE') return;
  const destination = activeDestination();
  if (!destination.point) {
    state.navConfirmed = true;
    state.navMessage = '모든 하차를 마쳤습니다. 이동 종료를 확인해 주세요.';
  } else {
    state.navConfirmed = true;
    state.navMessage = '모의 안내 확인 · ' + destination.label + ' · 실제 차량 내비는 연결되지 않았습니다.';
  }
  render();
}

async function newSession(demo) {
  try {
    const payload = await api(demo ? '/api/sessions/demo' : '/api/sessions', { method: 'POST', body: '{}' });
    saveCredentials({ sessionId: payload.sessionId, driverToken: payload.driverToken, inviteToken: payload.inviteToken, joinBaseUrl: payload.joinBaseUrl });
    state.session = payload.session || await api('/api/sessions/' + payload.sessionId, {}, payload.driverToken);
    state.parked = false;
    state.navConfirmed = false;
    state.navMessage = '';
    render();
    toast(demo ? '시연용 4명 데이터를 불러왔습니다.' : '새 이동을 만들었습니다. 초대 QR을 공유하세요.');
  } catch (error) { toast(error.message); }
}

async function mutate(path, body = {}, method = 'POST') {
  if (!state.credentials || !state.session) throw new Error('먼저 이동을 만들어 주세요.');
  const result = await api('/api/sessions/' + state.session.sessionId + path, { method, body: JSON.stringify(body) }, state.credentials.driverToken);
  if (result.sessionId) state.session = result;
  render();
  return result;
}

async function saveSettings() {
  if (!state.parked) return toast('정차 상태(시연)를 먼저 확인해 주세요.');
  const priority = byId('priority')?.value || null;
  const hasFinal = byId('final-choice')?.value === 'preset';
  const settings = {
    expectedVersion: state.session.version,
    startPoint: state.session.startPoint,
    finalPoint: hasFinal ? { lat: 37.510, lon: 126.950, label: '운전자 최종 목적지' } : null,
    priorityPassengerId: priority || null,
  };
  await mutate('/settings', settings, 'PUT');
}

async function handleAction(button) {
  const action = button.dataset.action;
  try {
    if (action === 'new') return await newSession(false);
    if (action === 'demo') return await newSession(true);
    if (action === 'park') {
      state.parked = !state.parked;
      render();
      return toast(state.parked ? '정차(시연) 상태입니다. 실제 차량 감지는 아닙니다.' : '정차 확인을 해제했습니다. 운전자 입력이 잠깁니다.');
    }
    if (action === 'preview') { state.showPhone = true; render(); return; }
    if (action === 'close-preview') { state.showPhone = false; await refreshSession(true); return; }
    if (!state.session) return toast('먼저 이동을 만들어 주세요.');
    if (action === 'copy') {
      const url = state.credentials.joinBaseUrl + '/join/' + state.session.sessionId + '?invite=' + encodeURIComponent(state.credentials.inviteToken);
      await navigator.clipboard.writeText(url);
      return toast('초대 링크를 복사했습니다. 세션 종료 후에는 사용할 수 없습니다.');
    }
    if (action === 'remove') {
      if (!state.parked) return toast('정차 상태(시연)에서만 변경할 수 있습니다.');
      const result = await api('/api/sessions/' + state.session.sessionId + '/passengers/' + button.dataset.id, { method: 'DELETE', headers: { 'If-Match': String(state.session.version) } }, state.credentials.driverToken);
      state.session = result;
      return render();
    }
    if (action === 'move') {
      if (!state.parked) return toast('정차 상태(시연)에서만 순서를 조정할 수 있습니다.');
      const order = currentOrder().map(person => person.id);
      const index = Number(button.dataset.index);
      const next = index + Number(button.dataset.delta);
      if (next < 0 || next >= order.length) return;
      [order[index], order[next]] = [order[next], order[index]];
      const updated = await mutate('/select-order', { expectedVersion: state.session.version, order });
      return toast('새 순서의 경로를 계산했습니다. ' + (updated.routeSnapshot?.note || '경로 정보를 확인해 주세요.'));
    }
    if (action === 'plan') {
      if (!state.parked) return toast('정차 상태(시연)를 먼저 확인해 주세요.');
      await saveSettings();
      if (state.session.passengers.length < 1) return toast('동승자 한 명 이상 등록해 주세요.');
      const updated = await mutate('/plan', { expectedVersion: state.session.version });
      return toast((updated.routeSnapshot?.evaluatedCount || 0) + '개 순서를 비교했습니다. ' + (updated.routeSnapshot?.note || '경로 정보를 확인해 주세요.'));
    }
    if (action === 'start') {
      if (!state.parked) return toast('정차 상태(시연)를 먼저 확인해 주세요.');
      await mutate('/start', { expectedVersion: state.session.version });
      await requestMockNavigation();
      return toast('모의 내비 안내 화면을 시작했습니다.');
    }
    if (action === 'nav-fail') {
      if (!state.parked) return toast('정차 상태(시연)를 먼저 확인해 주세요.');
      state.navConfirmed = false;
      state.navMessage = '모의 안내 거부 · 시연용 요청 실패입니다. 다음 목적지로 진행하지 않습니다.';
      render();
      return toast('모의 안내 실패를 표시했습니다. 다시 요청해야 계속할 수 있습니다.');
    }
    if (action === 'nav-retry') {
      if (!state.parked) return toast('정차 상태(시연)를 먼저 확인해 주세요.');
      await requestMockNavigation();
      return toast(state.navConfirmed ? '모의 안내를 다시 확인했습니다.' : '안내를 다시 확인할 수 없습니다.');
    }
    if (action === 'complete-stop') {
      if (!state.parked) return toast('정차 상태(시연)에서만 하차를 확인할 수 있습니다.');
      if (!state.navConfirmed) return toast('현재 목적지의 안내 결과를 확인해 주세요.');
      const person = currentOrder()[state.session.activeIndex];
      await mutate('/complete-stop', { expectedVersion: state.session.version, passengerId: person.id });
      await requestMockNavigation();
      return toast('하차 완료를 반영했습니다. 다음 안내는 모의 상태입니다.');
    }
    if (action === 'complete-trip') {
      if (!state.parked) return toast('정차 상태(시연)에서만 이동을 마칠 수 있습니다.');
      if (!state.navConfirmed) return toast('현재 목적지의 안내 결과를 확인해 주세요.');
      if (!window.confirm('시연 이동을 종료하고 참가 링크를 비활성화할까요?')) return;
      await mutate('/complete-trip', { expectedVersion: state.session.version });
      state.session = null;
      sessionStorage.removeItem('moagil-driver');
      state.credentials = null;
      render();
      return toast('시연 이동을 종료했습니다.');
    }
    if (action === 'end') {
      if (!state.parked) return toast('정차 상태(시연)에서만 이동을 종료할 수 있습니다.');
      if (!window.confirm('현재 이동을 종료하고 초대 링크를 비활성화할까요?')) return;
      await mutate('/cancel', { expectedVersion: state.session.version });
      state.session = null;
      state.credentials = null;
      sessionStorage.removeItem('moagil-driver');
      return render();
    }
    if (action === 'stop') return toast('모의 안내를 닫으려면 이동을 종료해 주세요.');
  } catch (error) {
    toast(error.message);
    if (state.credentials) await refreshSession(true).catch(() => {});
  }
}

async function refreshSession(force = false) {
  if (!state.credentials) return;
  if (!force && ['INPUT', 'SELECT', 'TEXTAREA'].includes(document.activeElement?.tagName)) return;
  try {
    const next = await api('/api/sessions/' + state.credentials.sessionId, {}, state.credentials.driverToken);
    if (force || !state.session || next.version !== state.session.version) {
      state.session = next;
      if (!state.showPhone) render();
    }
  } catch (error) {
    if (error.message.includes('인증') || error.message.includes('만료')) {
      sessionStorage.removeItem('moagil-driver');
      state.credentials = null;
      state.session = null;
      render();
    }
  }
}

function updatePassengerPoint(point) {
  state.pin = {
    lat: +Number(point.lat).toFixed(6),
    lon: +Number(point.lon).toFixed(6),
    label: (byId('place-label')?.value || '').trim() || state.pin.label || '시연 위치',
  };
  if (byId('read-lat')) byId('read-lat').textContent = state.pin.lat.toFixed(5);
  if (byId('read-lon')) byId('read-lon').textContent = state.pin.lon.toFixed(5);
  if (byId('coord-lat')) byId('coord-lat').value = state.pin.lat.toFixed(6);
  if (byId('coord-lon')) byId('coord-lon').value = state.pin.lon.toFixed(6);
  const label = byId('place-label');
  if (label && !label.value) label.value = state.pin.label;
}

function syncCoordinatesToMap() {
  const latInput = byId('coord-lat');
  const lonInput = byId('coord-lon');
  const error = byId('phone-error');
  if (!latInput || !lonInput || !latInput.value || !lonInput.value) return false;
  const lat = Number(latInput.value);
  const lon = Number(lonInput.value);
  if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    if (error) error.textContent = '위도는 -90~90, 경도는 -180~180 범위로 입력해 주세요.';
    return false;
  }
  updatePassengerPoint({ lat, lon });
  passengerMapController?.setPoint(state.pin, false);
  if (error) error.textContent = '';
  return true;
}

async function bindPassengerMap() {
  const element = byId('pin-map');
  if (!element) return;
  try {
    const maps = await getMapAdapter();
    if (!element.isConnected) return;
    passengerMapController = maps.createPassengerMap(element, state.pin, updatePassengerPoint);
    if (passengerMapController.destroy) activeMaps.push(passengerMapController);
    else {
      const map = passengerMapController.map;
      activeMaps.push({ destroy: () => map.remove() });
    }
  } catch (_error) {
    if (element.isConnected) element.innerHTML = '<div class="map-error">네이버 지도를 불러오지 못했어요.<br>Client ID, 등록 URL, Dynamic Map 설정을 확인해 주세요.</div>';
  }
}

async function initializePassenger() {
  document.body.classList.add('passenger-body');
  state.joinId = location.pathname.split('/').filter(Boolean)[1];
  const params = new URLSearchParams(location.search);
  state.invite = params.get('invite') || sessionStorage.getItem('moagil-invite-' + state.joinId);
  if (params.has('invite')) {
    sessionStorage.setItem('moagil-invite-' + state.joinId, state.invite);
    history.replaceState(null, '', '/join/' + state.joinId);
  }
  render();
  try {
    if (!state.invite) throw new Error('초대 정보가 없어요. 차량 화면에서 새 QR을 스캔해 주세요.');
    state.joinInfo = await api('/api/sessions/' + state.joinId + '/join-info', { method: 'POST', body: JSON.stringify({ inviteToken: state.invite }) });
  } catch (error) {
    state.joinError = error.message;
    state.joinInfo = { state: 'ERROR', remaining: 0 };
    render();
    const content = byId('app');
    if (content) content.innerHTML = '<main class="passenger-shell"><header class="passenger-top"><a class="brand" href="/"><span class="brand-mark">M</span><span class="brand-name">MOAGIL</span></a><span class="phone-badge">초대 확인 필요</span></header><section class="passenger-heading"><div class="eyebrow">동승자 참여</div><h1>초대 링크를<br>확인할 수 없어요.</h1><p>' + esc(error.message) + '</p></section><div class="phone-card privacy-note">차량 화면의 새 QR을 다시 스캔해 주세요. 만료된 링크나 이미 마감된 이동에는 참여할 수 없습니다.</div></main>';
    return;
  }
  render();
}

async function submitPassenger(event) {
  event.preventDefault();
  const form = event.target;
  const name = form.elements.displayName.value.trim();
  const label = form.elements.label.value.trim() || '시연 위치';
  const errorNode = byId('phone-error');
  if (!name || name.length > 20) { if (errorNode) errorNode.textContent = '별명은 1~20자로 입력해 주세요.'; return; }
  state.pin.label = label;
  state.saving = true;
  const button = form.querySelector('.phone-submit');
  button.disabled = true;
  button.textContent = '등록 중…';
  try {
    const keyStore = 'moagil-idempotency-' + state.joinId;
    const signature = JSON.stringify({ displayName: name, point: state.pin });
    const signatureStore = keyStore + '-signature';
    let key = sessionStorage.getItem(keyStore);
    if (!key || sessionStorage.getItem(signatureStore) !== signature) { key = randomToken(); sessionStorage.setItem(keyStore, key); sessionStorage.setItem(signatureStore, signature); }
    await api('/api/sessions/' + state.joinId + '/passengers', { method: 'POST', body: JSON.stringify({ inviteToken: state.invite, displayName: name, point: state.pin, idempotencyKey: key }) });
    sessionStorage.removeItem(keyStore); sessionStorage.removeItem(signatureStore);
    state.joined = true; state.joinedName = name; state.saving = false;
    render();
  } catch (error) {
    state.saving = false;
    if (errorNode) errorNode.textContent = error.message;
    button.disabled = false; button.textContent = '다시 시도';
  }
}

function randomToken() {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
}

app.addEventListener('click', event => {
  const button = event.target.closest('[data-action]');
  if (!button) return;
  handleAction(button);
});

app.addEventListener('change', event => {
  if (event.target.id === 'priority' || event.target.id === 'final-choice') {
    if (!state.parked) return toast('정차 상태(시연)에서만 설정을 바꿀 수 있습니다.');
    saveSettings().then(() => toast('이동 설정을 저장했습니다. 경로를 다시 계산해 주세요.')).catch(error => toast(error.message));
  }
});

app.addEventListener('input', event => {
  if (event.target.id === 'coord-lat' || event.target.id === 'coord-lon') syncCoordinatesToMap();
});

app.addEventListener('submit', event => {
  if (event.target.id === 'passenger-form') submitPassenger(event);
});

async function startApp() {
  await loadMapConfig();
  if (location.pathname.startsWith('/join/')) return initializePassenger();
  sessionStorage.removeItem('moagil-parked');
  state.credentials = loadCredentials();
  if (state.credentials) {
    try { state.session = await api('/api/sessions/' + state.credentials.sessionId, {}, state.credentials.driverToken); }
    catch (_error) { sessionStorage.removeItem('moagil-driver'); state.credentials = null; state.session = null; }
  }
  render();
  setInterval(() => refreshSession(false), 2000);
}

startApp();

/* QR Code Model 2 encoder: version 8, low error correction, byte mode. */
function drawQr(canvas, value) {
  const matrix = qrMatrix(value);
  const size = matrix.length;
  const scale = 5;
  const border = 4;
  canvas.width = (size + border * 2) * scale;
  canvas.height = canvas.width;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#10212a';
  matrix.forEach((row, y) => row.forEach((dark, x) => { if (dark) ctx.fillRect((x + border) * scale, (y + border) * scale, scale, scale); }));
}

function gfMultiply(x, y) {
  let z = 0;
  for (let i = 7; i >= 0; i--) {
    z = (z << 1) ^ (((z >>> 7) & 1) * 0x11d);
    z ^= ((y >>> i) & 1) * x;
  }
  return z;
}

function rsRemainder(data, degree) {
  let generator = [1];
  let alpha = 1;
  for (let i = 0; i < degree; i++) {
    const next = new Array(generator.length + 1).fill(0);
    for (let j = 0; j < generator.length; j++) {
      next[j] ^= generator[j];
      next[j + 1] ^= gfMultiply(generator[j], alpha);
    }
    generator = next;
    alpha = gfMultiply(alpha, 2);
  }
  const result = data.concat(new Array(degree).fill(0));
  for (let i = 0; i < data.length; i++) {
    const factor = result[i];
    if (factor) for (let j = 0; j < generator.length; j++) result[i + j] ^= gfMultiply(generator[j], factor);
  }
  return result.slice(data.length);
}

function bch(value, polynomial, shift) {
  let remainder = value << shift;
  const polynomialDegree = Math.floor(Math.log2(polynomial));
  while (remainder !== 0 && Math.floor(Math.log2(remainder)) >= polynomialDegree) {
    const degree = Math.floor(Math.log2(remainder));
    remainder ^= polynomial << (degree - polynomialDegree);
  }
  return (value << shift) | remainder;
}

function qrMatrix(value) {
  const version = 8, dimension = 17 + 4 * version, dataCodewords = 194, eccPerBlock = 24, blockData = 97, mask = 0;
  const bytes = Array.from(new TextEncoder().encode(value));
  if (bytes.length > 192) throw new Error('초대 주소가 QR 코드에 들어가지 않습니다.');
  const bits = [];
  const appendBits = (number, count) => { for (let i = count - 1; i >= 0; i--) bits.push((number >>> i) & 1); };
  appendBits(4, 4); appendBits(bytes.length, 8);
  bytes.forEach(byte => appendBits(byte, 8));
  for (let i = 0; i < Math.min(4, dataCodewords * 8 - bits.length); i++) bits.push(0);
  while (bits.length % 8) bits.push(0);
  const data = [];
  for (let i = 0; i < bits.length; i += 8) data.push(bits.slice(i, i + 8).reduce((a, b) => (a << 1) | b, 0));
  for (let pad = 0; data.length < dataCodewords; pad++) data.push(pad % 2 === 0 ? 0xec : 0x11);
  const blocks = [data.slice(0, blockData), data.slice(blockData)];
  const ecc = blocks.map(block => rsRemainder(block, eccPerBlock));
  const codewords = [];
  for (let i = 0; i < blockData; i++) blocks.forEach(block => codewords.push(block[i]));
  for (let i = 0; i < eccPerBlock; i++) ecc.forEach(block => codewords.push(block[i]));
  const modules = Array.from({ length: dimension }, () => Array(dimension).fill(false));
  const functions = Array.from({ length: dimension }, () => Array(dimension).fill(false));
  const setFunction = (x, y, dark) => { if (x >= 0 && y >= 0 && x < dimension && y < dimension) { modules[y][x] = Boolean(dark); functions[y][x] = true; } };
  const finder = (cx, cy) => {
    for (let dy = -1; dy <= 7; dy++) for (let dx = -1; dx <= 7; dx++) {
      const x = cx + dx, y = cy + dy;
      const inside = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6;
      const dark = inside && (dx === 0 || dx === 6 || dy === 0 || dy === 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
      setFunction(x, y, dark);
    }
  };
  finder(0, 0); finder(dimension - 7, 0); finder(0, dimension - 7);
  const centers = [6, 24, 42];
  centers.forEach(cy => centers.forEach(cx => {
    if ((cx === 6 && cy === 6) || (cx === dimension - 7 && cy === 6) || (cx === 6 && cy === dimension - 7)) return;
    for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
  }));
  for (let i = 0; i < dimension; i++) {
    if (!functions[6][i]) setFunction(i, 6, i % 2 === 0);
    if (!functions[i][6]) setFunction(6, i, i % 2 === 0);
  }
  const versionBits = bch(version, 0x1f25, 12);
  for (let i = 0; i < 18; i++) {
    const dark = ((versionBits >>> i) & 1) !== 0;
    const a = dimension - 11 + (i % 3), b = Math.floor(i / 3);
    setFunction(a, b, dark); setFunction(b, a, dark);
  }
  for (let i = 0; i < 15; i++) {
    let x, y;
    if (i < 6) { x = 8; y = i; }
    else if (i < 8) { x = 8; y = i + 1; }
    else { x = 8; y = dimension - 15 + i; }
    setFunction(x, y, false);
    if (i < 8) { x = dimension - i - 1; y = 8; }
    else if (i < 9) { x = 15 - i; y = 8; }
    else { x = 14 - i; y = 8; }
    setFunction(x, y, false);
  }
  setFunction(8, dimension - 8, true);
  const stream = [];
  codewords.forEach(word => appendBitsTo(stream, word, 8));
  let bitIndex = 0;
  let upward = true;
  for (let right = dimension - 1; right >= 1; right -= 2) {
    if (right === 6) right--;
    for (let vert = 0; vert < dimension; vert++) {
      const y = upward ? dimension - 1 - vert : vert;
      for (let j = 0; j < 2; j++) {
        const x = right - j;
        if (functions[y][x]) continue;
        let dark = bitIndex < stream.length ? stream[bitIndex++] === 1 : false;
        if (((x + y) % 2) === 0) dark = !dark;
        modules[y][x] = dark;
      }
    }
    upward = !upward;
  }
  const formatData = (1 << 3) | mask;
  let format = bch(formatData, 0x537, 10);
  format ^= 0x5412;
  for (let i = 0; i < 15; i++) {
    const dark = ((format >>> i) & 1) !== 0;
    let x, y;
    if (i < 6) { x = 8; y = i; }
    else if (i < 8) { x = 8; y = i + 1; }
    else { x = 8; y = dimension - 15 + i; }
    modules[y][x] = dark;
    if (i < 8) { x = dimension - i - 1; y = 8; }
    else if (i < 9) { x = 15 - i; y = 8; }
    else { x = 14 - i; y = 8; }
    modules[y][x] = dark;
  }
  modules[dimension - 8][8] = true;
  return modules;
}

function appendBitsTo(bits, number, count) {
  for (let i = count - 1; i >= 0; i--) bits.push((number >>> i) & 1);
}
