/* MOAGIL route planner. Map tiles and road routes use OpenStreetMap data. */
const app = document.getElementById('app');
const captureMode = new URLSearchParams(location.search).has('capture');
let activeMaps = [];
let passengerMapController = null;
let mapConfigPromise = null;
let naverMapScriptPromise = null;
let autoPlanInFlight = false;
let autoPlanRequested = false;
let autoPlanAttemptKey = null;
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
  autoPlanning: false,
  autoPlanError: '',
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
  if (!state.session?.routeSnapshot || !order.length) return '<div class="empty-list">' + (state.autoPlanning ? '추천 순서를 계산하고 있어요…' : state.autoPlanError ? '자동 계산을 완료하지 못했습니다. 다시 계산해 주세요.' : '위치가 등록되면 자동 계산합니다.') + '</div>';
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

function driverMarkup()