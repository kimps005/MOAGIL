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
  searchQuery: '',
  searchResults: [],
  searchError: '',
  searching: false,
  pinMode: 'passenger',
  saving: false,
  showPhone: false,
  autoPlanning: false,
  autoPlanError: '',
  mapProvider: 'osm',
  routingProvider: 'osrm',
  avatarEmoji: '🚗',
};

const JOIN_EMOJIS = ['🚗', '🚕', '🚙', '🚌', '🚲', '🐱', '🐶', '🐼', '🐰', '🦊', '🌸', '⭐'];
const passengerEmoji = displayName => JOIN_EMOJIS.find(emoji => String(displayName || '').startsWith(emoji + ' ')) || '';
const passengerName = displayName => {
  const emoji = passengerEmoji(displayName);
  return emoji ? String(displayName).slice(emoji.length).trim() : String(displayName || '');
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
  return '<div class="passenger-list">' + people.map((person, index) => '<div class="passenger-row"><div class="avatar">' + esc(passengerEmoji(person.displayName) || String.fromCharCode(65 + index)) + '</div><div class="passenger-main"><div class="passenger-name">' + esc(passengerName(person.displayName)) + '</div><div class="passenger-point">' + esc(person.point.label || '시연 위치') + ' · ' + person.point.lat.toFixed(3) + ', ' + person.point.lon.toFixed(3) + '</div></div>' + (['COLLECTING', 'PLANNED'].includes(state.session.state) ? '<button class="icon-btn" data-action="remove" data-id="' + esc(person.id) + '" aria-label="' + esc(passengerName(person.displayName)) + ' 삭제">×</button>' : '') + '</div>').join('') + '</div>';
}

function routeRows() {
  const order = currentOrder();
  if (!state.session?.routeSnapshot || !order.length) return '<div class="empty-list">' + (state.autoPlanning ? '추천 순서를 계산하고 있어요…' : state.autoPlanError ? '자동 계산을 완료하지 못했습니다. 다시 계산해 주세요.' : '위치가 등록되면 자동 계산합니다.') + '</div>';
  const editable = state.session.state === 'PLANNED' && state.parked;
  const items = order.map((person, index) => '<li class="order-item"><span class="order-number">' + (index + 1) + '</span><div><div class="order-name">' + esc(passengerName(person.displayName)) + '</div><div class="order-destination">' + esc(person.point.label || '시연 지점') + '</div></div><div class="order-move">' + (editable ? '<button class="icon-btn" data-action="move" data-index="' + index + '" data-delta="-1" aria-label="위로 이동" ' + (index === 0 || (state.session.priorityPassengerId && index === 1) ? 'disabled' : '') + '>↑</button><button class="icon-btn" data-action="move" data-index="' + index + '" data-delta="1" aria-label="아래로 이동" ' + (index === order.length - 1 || (state.session.priorityPassengerId && index === 0) ? 'disabled' : '') + '>↓</button>' : '') + '</div></li>').join('');
  const providerLabel = state.routingProvider === 'naver' ? 'NAVER 실시간 경로' : state.routingProvider === 'osrm' ? '실제 도로 경로' : '모의 계산';
  return '<div class="route-panel"><div class="route-top"><div><div class="route-total">' + formatDuration(state.session.routeSnapshot.durationMs) + ' <span>예상 시간</span></div><div class="route-meta">' + formatDistance(state.session.routeSnapshot.distanceM) + ' · ' + (state.session.routeSnapshot.evaluatedCount || 1) + '가지 순서 비교</div></div><div class="mock-pill">' + providerLabel + '</div></div><ol class="order-list">' + items + '</ol></div>';
}

function activeDestination() {
  const order = currentOrder();
  const index = state.session.activeIndex || 0;
  if (index < order.length) return { label: passengerName(order[index].displayName), point: order[index].point, type: '승객 하차' };
  if (state.session.finalPoint) return { label: state.session.finalPoint.label, point: state.session.finalPoint, type: '운전자 최종 목적지' };
  return { label: '모든 하차 지점', point: null, type: '이동 마무리' };
}

function driverMarkup() {
  const session = state.session;
  const passengers = session?.passengers || [];
  const waiting = session && session.state === 'COLLECTING' && passengers.length === 0;
  const mapName = state.mapProvider === 'naver' ? '네이버 지도' : 'OpenStreetMap';
  const routeProviderLabel = state.routingProvider === 'naver' ? 'NAVER Directions' : state.routingProvider === 'osrm' ? 'OSRM 실제 도로 경로' : '모의 경로';
  const capture = captureMode;
  const phoneUrl = capture && state.credentials ? location.origin + '/join/' + session.sessionId + '?invite=' + encodeURIComponent(state.credentials.inviteToken) : '';
  const phonePreview = capture && state.showPhone ? `<div class='preview-backdrop'><section class='preview-panel'><div class='preview-copy'><h2>휴대폰 참가 화면</h2><p class='subcopy'>실제 QR 대신 같은 참가 페이지를 미리 봅니다.</p><button class='btn' data-action='close-preview'>닫기</button></div><div class='phone-device'><div class='phone-notch'></div><iframe title='시연용 참가자 모바일 화면' src='${esc(phoneUrl)}'></iframe></div></section></div>` : '';
  if (!session || waiting) {
    const qrContent = capture ? `<div class='qr-masked'></div><span>촬영 보호 · QR 가림</span>` : `<canvas id='invite-qr' aria-label='동승자 초대 QR 코드'></canvas>`;
    const footerAction = session ? `<button class='tiny-button' data-action='copy'>링크 복사</button>` : `<button class='btn primary' data-action='new'>QR 다시 만들기</button>`;
    return `<div class='app-shell qr-only-shell'><main class='qr-waiting'><a class='brand qr-brand' href='/'><span class='brand-mark'>M</span><span class='brand-name'>MOAGIL</span></a><div class='qr-intro'><div class='eyebrow'>공동 이동 초대</div><h1>QR을 스캔해<br>내릴 곳을 등록하세요</h1><p>위치를 등록하면 추천 하차 순서를 자동으로 계산합니다.</p></div><div class='qr-card qr-card-large ${capture ? 'capture-mode' : ''}' id='qr-card'>${qrContent}</div><div class='qr-waiting-footer'><span>${session ? '참여 대기 · 0 / 4명' : 'QR 세션을 준비하지 못했습니다'}</span>${footerAction}</div>${capture ? `<button class='tiny-button' data-action='preview'>참가 화면 미리보기</button>` : ''}</main>${capture ? `<div class='capture-chip'>촬영 보호 · 실제 초대 QR 가림</div>` : ''}</div>${phonePreview}`;
  }
  const collecting = ['COLLECTING', 'PLANNED'].includes(session.state);
  const order = currentOrder();
  const destination = session.state === 'ACTIVE' ? activeDestination() : null;
  const currentPassenger = destination && (session.activeIndex || 0) < order.length;
  const finalArrived = destination && !currentPassenger && Boolean(session.finalPoint);
  const finalEnabled = Boolean(session.finalPoint);
  const status = session.state === 'ACTIVE' ? '이동 중' : state.autoPlanning ? '순서 계산 중' : session.state === 'PLANNED' ? '추천 순서 준비됨' : '위치 수집 중';
  const qrHint = state.autoPlanning ? '등록 위치를 반영해 경로를 계산하고 있어요.' : state.autoPlanError ? esc(state.autoPlanError) : session.routeSnapshot ? `${session.routeSnapshot.evaluatedCount || 1}가지 순서 비교 완료 · ${esc(session.routeSnapshot.note || '')}` : '새 위치가 등록되면 자동 계산합니다.';
  const settings = collecting ? `<details class='route-settings'><summary>경로 설정</summary><div class='settings'><div class='field'><label for='priority'>먼저 내릴 사람</label><select id='priority' ${!state.parked ? 'disabled' : ''}><option value=''>지정하지 않음</option>${passengers.map(person => `<option value='${esc(person.id)}' ${person.id === session.priorityPassengerId ? 'selected' : ''}>${esc(person.displayName)}</option>`).join('')}</select></div><div class='field'><label for='final-choice'>운전자 최종 목적지</label><select id='final-choice' ${!state.parked ? 'disabled' : ''}><option value='none' ${!finalEnabled ? 'selected' : ''}>설정 안 함</option><option value='preset' ${finalEnabled ? 'selected' : ''}>시연 지점 포함</option></select></div></div></details>` : '';
  const routeActions = session.state === 'ACTIVE'
    ? `<div class='active-card'><div class='safety-banner ${state.navConfirmed ? 'ready' : ''}'>${esc(state.navMessage || '○ 다음 하차 목적지 안내를 확인해 주세요.')}</div><div class='active-destination'><small>다음 목적지 · ${esc(destination.type)}</small><strong>${esc(destination.label)}</strong><small>${destination.point ? `${destination.point.lat.toFixed(5)}, ${destination.point.lon.toFixed(5)}` : '모든 하차 지점을 완료했습니다'}</small></div><div class='route-actions'>${currentPassenger ? `<button class='btn primary' data-action='complete-stop' ${!state.parked || !state.navConfirmed ? 'disabled' : ''}>하차 완료</button>` : `<button class='btn primary' data-action='complete-trip' ${!state.parked || !state.navConfirmed ? 'disabled' : ''}>${finalArrived ? '최종 목적지 도착' : '이동 마무리'}</button>`}<button class='btn warn' data-action='end'>이동 취소</button></div></div>`
    : `<div class='route-actions'><button class='btn' data-action='plan' ${state.autoPlanning ? 'disabled' : ''}>${session.routeSnapshot ? '순서 다시 계산' : '다시 계산'}</button>${session.state === 'PLANNED' ? `<button class='btn primary' data-action='start' ${!state.parked ? 'disabled' : ''}>${state.parked ? '이 순서로 안내 시작' : '정차 확인 후 안내 시작'}</button>` : ''}</div>`;
  const mapCaption = session.routeSnapshot?.geometry?.length ? routeProviderLabel : state.autoPlanning ? '추천 순서 계산 중' : '등록 위치를 기다리는 중';
  return `<div class='app-shell live-shell'><header class='topbar'><a class='brand' href='/'><span class='brand-mark'>M</span><span><span class='brand-name'>MOAGIL</span><span class='brand-sub'>${shortId(session.sessionId)} · ${passengers.length}/4명</span></span></a><div class='nav-right'><span class='status-pill'><i class='status-dot'></i>${status}</span>${collecting ? `<button class='btn small' data-action='copy'>초대 링크 복사</button>` : ''}<button class='park-toggle ${state.parked ? 'is-parked' : ''}' data-action='park' aria-pressed='${state.parked}' title='정차 확인은 시연용입니다.'>${state.parked ? '✓ 정차 확인됨' : '정차 확인'}</button><button class='btn small' data-action='new'>새 이동</button></div></header><main class='route-workspace'><section class='card map-card'><div class='map-topline'><span class='map-label'>${mapName}</span><span class='map-label'>${mapCaption}</span></div><div id='driver-map' class='driver-map' role='application' aria-label='${mapName} 지도와 하차 경로'></div><div class='map-legend'><span class='legend-item'><i class='legend-dot dark'></i>출발지</span><span class='legend-item'><i class='legend-dot orange'></i>다음 하차</span><span class='legend-item'><i class='legend-dot'></i>등록 위치</span></div></section><aside class='card session-panel'><section><div class='panel-heading'><div><div class='card-kicker'>현재 세션</div><h1 class='card-title'>등록 위치 <span>${passengers.length}/4</span></h1></div><span class='status-pill'>${status}</span></div>${passengerRows()}</section><section class='recommendation'><div class='panel-heading'><div><div class='card-kicker'>자동 추천</div><h2 class='card-title'>추천 하차 순서</h2></div><span class='mini-note'>${routeProviderLabel}</span></div><div class='route-status' role='status' aria-live='polite'>${qrHint}</div>${routeRows()}${state.autoPlanError ? `<div class='route-error'>자동 계산 실패 · <button class='tiny-button' data-action='plan'>다시 계산</button></div>` : ''}${settings}${routeActions}</section></aside></main><footer class='footer live-footer'><span>MOAGIL</span><span>계산은 등록 즉시 갱신됩니다</span></footer></div>${capture ? `<div class='capture-chip'>촬영 보호</div>` : ''}${phonePreview}`;
}

function photonPlaceParts(feature) {
  const props = feature?.properties || {};
  const title = String(props.name || props.street || props.city || '검색 위치');
  const context = [props.district, props.city, props.state, props.country]
    .filter(value => value && String(value) !== title)
    .map(String)
    .filter((value, index, values) => values.indexOf(value) === index)
    .slice(0, 2);
  return { title, context: context.join(' · '), label: [title, ...context].join(', ') };
}

function renderPlaceSearchResults() {
  const container = byId('place-results');
  const button = byId('search-place-button');
  if (!container) return;
  if (button) button.disabled = state.searching;
  const results = state.searchResults.map((feature, index) => {
    const place = photonPlaceParts(feature);
    return `<button type="button" class="place-result" role="option" data-action="select-place" data-index="${index}"><span>${esc(place.title)}</span>${place.context ? `<small>${esc(place.context)}</small>` : ''}</button>`;
  }).join('');
  const message = state.searching ? '<div class="place-search-message">검색 중…</div>' : state.searchError ? `<div class="place-search-message">${esc(state.searchError)}</div>` : '';
  container.innerHTML = results || message;
  container.hidden = !results && !message;
}

async function searchPlaces() {
  const input = byId('place-search');
  const query = (input?.value || state.searchQuery).trim();
  state.searchQuery = query;
  state.searchResults = [];
  state.searchError = '';
  if (query.length < 2) {
    state.searchError = '두 글자 이상 입력해 주세요.';
    renderPlaceSearchResults();
    input?.focus();
    return;
  }
  state.searching = true;
  renderPlaceSearchResults();
  try {
    const result = await api('/api/geocode?q=' + encodeURIComponent(query));
    const features = Array.isArray(result.features) ? result.features : [];
    const needle = query.toLocaleLowerCase('ko-KR').replace(/\s+/g, ' ');
    const score = feature => {
      const name = String(feature?.properties?.name || '').toLocaleLowerCase('ko-KR').replace(/\s+/g, ' ');
      return name === needle ? 0 : name.includes(needle) ? 1 : needle.includes(name) ? 2 : 3;
    };
    state.searchResults = features.sort((a, b) => score(a) - score(b));
    if (!state.searchResults.length) state.searchError = '검색 결과가 없습니다.';
  } catch (error) {
    state.searchError = error.message;
  } finally {
    state.searching = false;
    renderPlaceSearchResults();
  }
}

function selectSearchResult(index) {
  const feature = state.searchResults[index];
  const coordinates = feature?.geometry?.coordinates;
  if (!Array.isArray(coordinates) || coordinates.length < 2) {
    state.searchError = '이 장소의 위치를 사용할 수 없습니다.';
    renderPlaceSearchResults();
    return;
  }
  const [lon, lat] = coordinates.map(Number);
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
    state.searchError = '이 장소의 위치를 사용할 수 없습니다.';
    renderPlaceSearchResults();
    return;
  }
  const place = photonPlaceParts(feature);
  state.pin = { lat: +lat.toFixed(6), lon: +lon.toFixed(6), label: place.label };
  state.searchQuery = place.title;
  state.searchResults = [];
  state.searchError = '';
  render();
}

function joinMarkup() {
  document.body.classList.add('passenger-body');
  const error = state.joinError || '';
  const joined = state.joined;
  const heading = '<main class="passenger-shell"><header class="passenger-top"><a class="brand" href="/"><span class="brand-mark">M</span><span class="brand-name">MOAGIL</span></a></header>'
    + '<section class="passenger-heading"><h1>' + (joined ? '등록 완료' : '목적지 입력') + '</h1></section>';
  if (!state.joinInfo) return heading + '<div class="loading-card">초대 확인 중…</div></main>';
  if (joined) return heading + '<section class="phone-card passenger-result"><div class="success-mark">✓</div><h2>' + esc(state.joinedName) + '</h2><p>목적지를 등록했어요.</p></section></main>';
  const searchResults = state.searchResults.map((feature, index) => {
    const place = photonPlaceParts(feature);
    return '<button type="button" class="place-result" role="option" data-action="select-place" data-index="' + index + '"><span>' + esc(place.title) + '</span>' + (place.context ? '<small>' + esc(place.context) + '</small>' : '') + '</button>';
  }).join('');
  const searchMessage = state.searching ? '검색 중…' : state.searchError;
  const emojiOptions = JOIN_EMOJIS.map(emoji => '<button type="button" class="emoji-option' + (emoji === state.avatarEmoji ? ' selected' : '') + '" data-action="select-emoji" data-emoji="' + emoji + '" aria-label="이모지 ' + emoji + '" aria-pressed="' + (emoji === state.avatarEmoji) + '">' + emoji + '</button>').join('');
  return heading + '<form id="passenger-form"><section class="phone-card destination-card"><div class="avatar-picker" role="group" aria-label="지도에 표시할 이모지">' + emojiOptions + '</div><input id="display-name" name="displayName" maxlength="18" placeholder="이름이나 별명을 입력하세요." autocomplete="nickname" aria-label="이름이나 별명을 입력하세요" required><div class="map-picker"><div class="map-search"><svg aria-hidden="true" viewBox="0 0 24 24"><circle cx="10.8" cy="10.8" r="6.8"></circle><path d="m16 16 5 5"></path></svg><input id="place-search" type="search" value="' + esc(state.searchQuery) + '" placeholder="장소 검색" aria-label="목적지 검색"><button id="search-place-button" type="button" class="search-place-button" data-action="search-place" aria-label="검색" ' + (state.searching ? 'disabled' : '') + '>검색</button></div><div id="place-results" class="place-results" role="listbox" aria-live="polite"' + (!searchResults && !searchMessage ? ' hidden' : '') + '>' + (searchResults || (searchMessage ? '<div class="place-search-message">' + esc(searchMessage) + '</div>' : '')) + '</div><div id="pin-map" class="pin-map" role="application" aria-label="목적지를 찾고 핀을 조정하는 지도" tabindex="0"></div></div><div class="phone-error" id="phone-error">' + esc(error) + '</div></section><div class="phone-submit-bar"><button class="phone-submit" type="submit" ' + (state.saving ? 'disabled' : '') + '>목적지 등록</button></div></form></main>';
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

async function newSession(demo, silent = false) {
  try {
    const payload = await api(demo ? '/api/sessions/demo' : '/api/sessions', { method: 'POST', body: '{}' });
    saveCredentials({ sessionId: payload.sessionId, driverToken: payload.driverToken, inviteToken: payload.inviteToken, joinBaseUrl: payload.joinBaseUrl });
    state.session = payload.session || await api('/api/sessions/' + payload.sessionId, {}, payload.driverToken);
    autoPlanAttemptKey = null;
    state.parked = false;
    state.navConfirmed = false;
    state.navMessage = '';
    render();
    if (!silent) toast(demo ? '시연용 4명 데이터를 불러왔습니다.' : '새 이동을 만들었습니다. 초대 QR을 공유하세요.');
    void autoPlanIfNeeded();
  } catch (error) {
    render();
    if (!silent) toast(error.message);
  }
}

async function mutate(path, body = {}, method = 'POST') {
  if (!state.credentials || !state.session) throw new Error('먼저 이동을 만들어 주세요.');
  const result = await api('/api/sessions/' + state.session.sessionId + path, { method, body: JSON.stringify(body) }, state.credentials.driverToken);
  if (result.sessionId) state.session = result;
  render();
  void autoPlanIfNeeded();
  return result;
}

async function autoPlanIfNeeded(force = false) {
  if (!state.credentials || !state.session || !['COLLECTING', 'PLANNED'].includes(state.session.state) || !state.session.passengers.length) return;
  if (!force && state.session.state !== 'COLLECTING') return;
  if (!force && state.session.routeSnapshot) return;
  if (autoPlanInFlight) { autoPlanRequested = true; return; }
  const sessionId = state.session.sessionId;
  const version = state.session.version;
  const attemptKey = sessionId + ':' + version;
  if (!force && autoPlanAttemptKey === attemptKey) return;
  autoPlanAttemptKey = attemptKey;
  autoPlanInFlight = true;
  autoPlanRequested = false;
  state.autoPlanning = true;
  state.autoPlanError = '';
  render();
  try {
    const updated = await api('/api/sessions/' + sessionId + '/plan', {
      method: 'POST',
      body: JSON.stringify({ expectedVersion: version }),
    }, state.credentials.driverToken);
    if (state.credentials?.sessionId === sessionId && (!state.session || updated.version >= state.session.version)) state.session = updated;
  } catch (error) {
    if (state.credentials?.sessionId === sessionId) {
      state.autoPlanError = error.message;
      if (/최신|버전/.test(error.message)) autoPlanAttemptKey = null;
    }
  } finally {
    autoPlanInFlight = false;
    state.autoPlanning = false;
    if (state.credentials?.sessionId === sessionId) render();
    const shouldRefresh = autoPlanRequested;
    autoPlanRequested = false;
    if (shouldRefresh && state.credentials) await refreshSession(true);
  }
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
    if (action === 'select-emoji') {
      state.avatarEmoji = JOIN_EMOJIS.includes(button.dataset.emoji) ? button.dataset.emoji : JOIN_EMOJIS[0];
      app.querySelectorAll('.emoji-option').forEach(option => {
        const selected = option.dataset.emoji === state.avatarEmoji;
        option.classList.toggle('selected', selected);
        option.setAttribute('aria-pressed', String(selected));
      });
      return;
    }
    if (action === 'search-place') return await searchPlaces();
    if (action === 'select-place') return selectSearchResult(Number(button.dataset.index));
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
      render();
      void autoPlanIfNeeded();
      return;
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
      if (state.session.passengers.length < 1) return toast('동승자 한 명 이상 등록해 주세요.');
      await autoPlanIfNeeded(true);
      return toast(state.session.routeSnapshot ? (state.session.routeSnapshot.evaluatedCount || 0) + '개 순서를 비교했습니다.' : '최적 순서를 다시 계산하고 있습니다.');
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
    void autoPlanIfNeeded();
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
    label: String(point.label || '지도에서 선택한 위치'),
  };
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
    if (content) content.innerHTML = '<main class="passenger-shell"><header class="passenger-top"><a class="brand" href="/"><span class="brand-mark">M</span><span class="brand-name">MOAGIL</span></a></header><section class="passenger-heading"><h1>초대 링크를 확인할 수 없어요.</h1><p>' + esc(error.message) + '</p></section></main>';
    return;
  }
  render();
}

async function submitPassenger(event) {
  event.preventDefault();
  const form = event.target;
  const nickname = form.elements.displayName.value.trim();
  const name = state.avatarEmoji + ' ' + nickname;
  const errorNode = byId('phone-error');
  if (!nickname || Array.from(nickname).length > 18) { if (errorNode) errorNode.textContent = '이름을 확인해 주세요.'; return; }
  state.pin.label = state.pin.label || '선택한 위치';
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
    saveSettings().then(() => toast('이동 설정을 저장하고 추천 순서를 다시 계산합니다.')).catch(error => toast(error.message));
  }
});

app.addEventListener('input', event => {
  if (event.target.id === 'place-search') {
    state.searchQuery = event.target.value;
    state.searchResults = [];
    state.searchError = '';
    renderPlaceSearchResults();
  }
});

app.addEventListener('keydown', event => {
  if (event.target.id === 'place-search' && event.key === 'Enter') {
    event.preventDefault();
    void searchPlaces();
  }
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
  if (!state.credentials || !state.session) await newSession(false, true);
  else {
    render();
    void autoPlanIfNeeded();
  }
  setInterval(() => refreshSession(false), 1000);
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
