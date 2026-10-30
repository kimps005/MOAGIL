const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { chromium } = require(process.env.MOAGIL_PLAYWRIGHT_MODULE || 'playwright');

const baseUrl = process.env.MOAGIL_BASE_URL || 'http://127.0.0.1:8080';
const requestTimeout = 120_000;

function auth(token) {
  return { Authorization: `Bearer ${token}` };
}

async function body(response, label) {
  let payload = {};
  try { payload = await response.json(); } catch {}
  assert(response.ok(), `${label} failed with HTTP ${response.status()}${payload.error?.code ? ` (${payload.error.code})` : ''}`);
  return payload;
}

async function createSession(api) {
  const response = await api.post(`${baseUrl}/api/sessions`, { data: {}, timeout: requestTimeout });
  assert.equal(response.status(), 201, 'session creation');
  return response.json();
}

async function snapshot(api, credentials) {
  return body(await api.get(`${baseUrl}/api/sessions/${credentials.sessionId}`, {
    headers: auth(credentials.driverToken), timeout: requestTimeout,
  }), 'driver snapshot');
}

async function addPassenger(api, credentials, displayName, point, key = crypto.randomBytes(18).toString('hex')) {
  const response = await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/passengers`, {
    data: { inviteToken: credentials.inviteToken, displayName, point, idempotencyKey: key },
    timeout: requestTimeout,
  });
  return { response, payload: await response.json(), key, point, displayName };
}

async function cancelIfOpen(api, credentials) {
  if (!credentials?.sessionId || !credentials?.driverToken) return;
  try {
    const current = await snapshot(api, credentials);
    if (['COMPLETED', 'CANCELLED', 'EXPIRED'].includes(current.state)) return;
    await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/cancel`, {
      headers: auth(credentials.driverToken),
      data: { expectedVersion: current.version },
      timeout: requestTimeout,
    });
  } catch {}
}

async function verifyPassengerAndDriverUi(browser, api, testSessions) {
  const driver = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await driver.newPage();
  try {
    await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
    await page.locator('#qr-card').waitFor({ timeout: 15_000 });
    await page.waitForFunction(() => {
      const value = JSON.parse(sessionStorage.getItem('moagil-driver') || 'null');
      return value?.sessionId && value?.driverToken && value?.inviteToken;
    }, { timeout: 15_000 });
    const credentials = await page.evaluate(() => JSON.parse(sessionStorage.getItem('moagil-driver')));
    testSessions.push(credentials);
    await page.waitForFunction(() => document.querySelector('#invite-qr')?.width > 0);

    const unauthenticated = await api.get(`${baseUrl}/api/sessions/${credentials.sessionId}`, { timeout: requestTimeout });
    assert.equal(unauthenticated.status(), 401, 'unauthenticated session reads must be rejected');
    const joinInfoResponse = await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/join-info`, {
      data: { inviteToken: credentials.inviteToken }, timeout: requestTimeout,
    });
    const joinInfo = await body(joinInfoResponse, 'invite lookup');
    assert.equal(joinInfo.remaining, 4);
    assert(!('passengers' in joinInfo) && !('driverToken' in joinInfo), 'invite lookup exposed private driver data');

    const phoneContext = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    try {
      const phone = await phoneContext.newPage();
      const joinUrl = `${baseUrl}/join/${credentials.sessionId}?invite=${encodeURIComponent(credentials.inviteToken)}`;
      const joinPage = await phone.goto(joinUrl, { waitUntil: 'domcontentloaded' });
      assert.equal(joinPage.status(), 200, 'QR join page');
      await phone.locator('#display-name').waitFor();
      await phone.locator('#pin-map').waitFor();
      assert.equal(await phone.locator('#display-name').getAttribute('placeholder'), '이름이나 별명을 입력하세요.');
      assert.equal(await phone.locator('.phone-submit').innerText(), '목적지 등록');

      const geocodeResponse = phone.waitForResponse(response => response.url().includes('/api/geocode') && response.request().method() === 'GET');
      await phone.locator('#place-search').fill('오이도역');
      await phone.locator('#place-search').press('Enter');
      const geocode = await (await geocodeResponse).json();
      assert(geocode.features?.length > 0, 'place search returned no results');
      const needle = '오이도역';
      const features = [...geocode.features].sort((a, b) => {
        const score = feature => {
          const name = String(feature.properties?.name || '').toLocaleLowerCase('ko-KR').replace(/\s+/g, ' ');
          const query = needle.toLocaleLowerCase('ko-KR').replace(/\s+/g, ' ');
          return name === query ? 0 : name.includes(query) ? 1 : query.includes(name) ? 2 : 3;
        };
        return score(a) - score(b);
      });
      const selected = features[0].geometry.coordinates;
      await phone.locator('[data-action="select-place"]').first().click();
      await phone.locator('#pin-map.leaflet-container').waitFor({ timeout: 15_000 });

      const mapBox = await phone.locator('#pin-map').boundingBox();
      assert(mapBox && mapBox.width > 180, 'destination map did not initialize');
      await phone.locator('#pin-map').click({ position: { x: Math.round(mapBox.width * 0.65), y: Math.round(mapBox.height * 0.5) } });
      let registeredPoint = null;
      phone.on('request', request => {
        if (request.url().endsWith(`/api/sessions/${credentials.sessionId}/passengers`) && request.method() === 'POST') {
          try { registeredPoint = request.postDataJSON().point; } catch {}
        }
      });
      const registration = phone.waitForResponse(response => response.url().endsWith(`/api/sessions/${credentials.sessionId}/passengers`) && response.request().method() === 'POST');
      await phone.locator('#display-name').fill('E2E 수연');
      await phone.locator('.phone-submit').click();
      assert.equal((await registration).status(), 201, 'passenger registration');
      await phone.locator('.passenger-result').waitFor();
      assert.match(await phone.locator('.passenger-result').innerText(), /E2E 수연/);
      assert(registeredPoint, 'registration request did not include the selected pin');
      assert(Math.abs(registeredPoint.lat - selected[1]) > 0.00001 || Math.abs(registeredPoint.lon - selected[0]) > 0.00001,
        'moving the map pin did not change the submitted coordinates');
    } finally {
      await phoneContext.close();
    }

    await page.locator('.passenger-name').filter({ hasText: 'E2E 수연' }).waitFor({ timeout: 30_000 });
    await page.locator('.route-panel').waitFor({ timeout: requestTimeout });
    await page.waitForFunction(() => document.querySelector('.route-meta')?.textContent.includes('1가지 순서 비교'), { timeout: requestTimeout });
    assert.match(await page.locator('.route-panel .mock-pill').innerText(), /실제 도로 경로/);

    const start = page.locator('[data-action="start"]');
    await start.waitFor();
    assert(await start.isDisabled(), 'route start must stay locked until the parked check');
    await page.locator('[data-action="park"]').click();
    await page.locator('[data-action="start"]:not([disabled])').waitFor();
    await page.locator('[data-action="start"]').click();
    await page.locator('.active-destination strong').waitFor();
    assert.match(await page.locator('.active-destination strong').innerText(), /E2E 수연/);
    await page.locator('[data-action="nav-fail"]').click();
    assert(await page.locator('[data-action="complete-stop"]').isDisabled(), 'failed navigation must block drop-off');
    await page.locator('[data-action="nav-retry"]').click();
    await page.locator('[data-action="complete-stop"]:not([disabled])').waitFor();
    await page.locator('[data-action="complete-stop"]').click();
    await page.locator('[data-action="complete-trip"]:not([disabled])').waitFor();
    page.once('dialog', dialog => dialog.accept());
    await page.locator('[data-action="complete-trip"]').click();
    await page.locator('.qr-waiting').waitFor();
    const completed = await snapshot(api, credentials);
    assert.equal(completed.state, 'COMPLETED', 'trip completion');
    console.log('E2E PASS: QR join, real place search, movable pin, live route update, parked gate, navigation retry, drop-off and trip end');
  } finally {
    await driver.close();
  }
}

async function verifyApiFlows(api, testSessions) {
  const credentials = await createSession(api);
  testSessions.push(credentials);
  const passengers = [];
  const stops = [
    ['E2E A', { lat: 37.3624, lon: 126.7381, label: '오이도역' }],
    ['E2E B', { lat: 37.4979, lon: 127.0276, label: '강남역' }],
    ['E2E C', { lat: 37.4740, lon: 126.6170, label: '인천역' }],
    ['E2E D', { lat: 37.2636, lon: 126.9965, label: '수원역' }],
  ];

  const first = await addPassenger(api, credentials, stops[0][0], stops[0][1]);
  assert.equal(first.response.status(), 201, 'first passenger registration');
  passengers.push({ id: first.payload.passengerId, name: stops[0][0], point: stops[0][1] });
  const duplicate = await addPassenger(api, credentials, stops[0][0], stops[0][1], first.key);
  assert.equal(duplicate.response.status(), 200, 'idempotent retry');
  assert.equal(duplicate.payload.duplicate, true);
  const conflict = await addPassenger(api, credentials, 'changed name', stops[0][1], first.key);
  assert.equal(conflict.response.status(), 409, 'idempotency payload conflict');

  for (const [name, point] of stops.slice(1)) {
    const added = await addPassenger(api, credentials, name, point);
    assert.equal(added.response.status(), 201, `register ${name}`);
    passengers.push({ id: added.payload.passengerId, name, point });
  }
  const fifth = await addPassenger(api, credentials, 'E2E fifth', { lat: 37.5, lon: 127.0, label: 'test' });
  assert.equal(fifth.response.status(), 409, 'fifth passenger limit');
  assert.equal(fifth.payload.error?.code, 'SESSION_FULL');

  let current = await snapshot(api, credentials);
  const stale = await api.put(`${baseUrl}/api/sessions/${credentials.sessionId}/settings`, {
    headers: auth(credentials.driverToken),
    data: { expectedVersion: 0, startPoint: current.startPoint, finalPoint: null, priorityPassengerId: null },
  });
  assert.equal(stale.status(), 409, 'stale session version');

  const removed = await api.delete(`${baseUrl}/api/sessions/${credentials.sessionId}/passengers/${passengers[3].id}`, {
    headers: { ...auth(credentials.driverToken), 'If-Match': String(current.version) },
  });
  current = await body(removed, 'remove passenger');
  assert.equal(current.passengers.length, 3);
  const replacement = await addPassenger(api, credentials, stops[3][0], stops[3][1]);
  assert.equal(replacement.response.status(), 201, 'replacement passenger');
  passengers[3] = { id: replacement.payload.passengerId, name: stops[3][0], point: stops[3][1] };

  current = await snapshot(api, credentials);
  const finalPoint = { lat: 37.5100, lon: 126.9500, label: 'E2E 운전자 목적지' };
  current = await body(await api.put(`${baseUrl}/api/sessions/${credentials.sessionId}/settings`, {
    headers: auth(credentials.driverToken),
    data: { expectedVersion: current.version, startPoint: current.startPoint, finalPoint, priorityPassengerId: null },
    timeout: requestTimeout,
  }), 'route settings');

  const routeStart = Date.now();
  current = await body(await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/plan`, {
    headers: auth(credentials.driverToken), data: { expectedVersion: current.version }, timeout: requestTimeout,
  }), '24-route plan');
  assert.equal(current.routeSnapshot.evaluatedCount, 24, 'four unpinned stops must compare 24 orders');
  assert(current.routeSnapshot.distanceM > 0 && current.routeSnapshot.durationMs > 0, 'real-road route metrics');
  assert.match(current.routeSnapshot.providerVersion, /osrm/i, 'route plan must use the real-road provider');
  const initialPlanSeconds = (Date.now() - routeStart) / 1000;

  const priority = passengers[1];
  current = await body(await api.put(`${baseUrl}/api/sessions/${credentials.sessionId}/settings`, {
    headers: auth(credentials.driverToken),
    data: { expectedVersion: current.version, startPoint: current.startPoint, finalPoint: current.finalPoint, priorityPassengerId: priority.id },
    timeout: requestTimeout,
  }), 'priority setting');
  current = await body(await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/plan`, {
    headers: auth(credentials.driverToken), data: { expectedVersion: current.version }, timeout: requestTimeout,
  }), 'six-route priority plan');
  assert.equal(current.routeSnapshot.evaluatedCount, 6, 'a pinned first stop must reduce the four-stop comparison to six orders');
  assert.equal(current.selectedOrder[0], priority.id, 'the pinned stop must remain first');

  const manualOrder = [priority.id, passengers[3].id, passengers[2].id, passengers[0].id];
  current = await body(await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/select-order`, {
    headers: auth(credentials.driverToken), data: { expectedVersion: current.version, order: manualOrder }, timeout: requestTimeout,
  }), 'manual order');
  assert.deepEqual(current.selectedOrder, manualOrder, 'manual order selection');
  assert.equal(current.routeSnapshot.distanceM > 0, true, 'manual order road estimate');

  const unauthorizedTile = await api.get(`${baseUrl}/api/sessions/${credentials.sessionId}/map-tiles/0/0/0.png`);
  assert.equal(unauthorizedTile.status(), 401, 'map tiles require driver authentication');
  const tile = await api.get(`${baseUrl}/api/sessions/${credentials.sessionId}/map-tiles/0/0/0.png`, {
    headers: auth(credentials.driverToken), timeout: requestTimeout,
  });
  assert.equal(tile.status(), 200, 'authenticated map tile proxy');
  assert.deepEqual((await tile.body()).subarray(0, 8), Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), 'map tile PNG');
  assert.match(tile.headers()['cache-control'] || '', /max-age=/i, 'tile response must carry the upstream cache policy');
  assert.doesNotMatch(tile.headers()['cache-control'] || '', /no-store/i, 'tile response must not disable map tile caching');

  current = await body(await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/start`, {
    headers: auth(credentials.driverToken), data: { expectedVersion: current.version }, timeout: requestTimeout,
  }), 'route start');
  const wrongStop = await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/complete-stop`, {
    headers: auth(credentials.driverToken),
    data: { expectedVersion: current.version, passengerId: passengers[0].id === manualOrder[0] ? passengers[2].id : passengers[0].id },
  });
  assert.equal(wrongStop.status(), 409, 'out-of-order drop-off');

  for (const [index, passengerId] of manualOrder.entries()) {
    current = await body(await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/complete-stop`, {
      headers: auth(credentials.driverToken), data: { expectedVersion: current.version, passengerId }, timeout: requestTimeout,
    }), `drop-off ${index + 1}`);
    assert.equal(current.activeIndex, index + 1);
    assert.equal(current.state, 'ACTIVE');
  }
  assert.equal(current.finalPoint.label, finalPoint.label, 'driver final destination');
  current = await body(await api.post(`${baseUrl}/api/sessions/${credentials.sessionId}/complete-trip`, {
    headers: auth(credentials.driverToken), data: { expectedVersion: current.version }, timeout: requestTimeout,
  }), 'trip completion');
  assert.equal(current.state, 'COMPLETED');
  console.log(`E2E PASS: real OSRM roads, 24 → 6 orders, fixed first stop, manual order, map tile proxy, sequential drop-offs (24-order plan ${initialPlanSeconds.toFixed(2)}s)`);

  const concurrent = await createSession(api);
  testSessions.push(concurrent);
  const attempts = await Promise.all(Array.from({ length: 5 }, (_, index) => addPassenger(api, concurrent, `Concurrent ${index + 1}`, {
    lat: 37.51 + index / 1000, lon: 127.02, label: 'concurrent test',
  })));
  assert.equal(attempts.filter(item => item.response.status() === 201).length, 4, 'concurrent registration accepts only four people');
  assert.equal(attempts.filter(item => item.response.status() === 409 && item.payload.error?.code === 'SESSION_FULL').length, 1,
    'concurrent fifth registration is rejected');
  const cancelled = await snapshot(api, concurrent);
  const cancelResponse = await api.post(`${baseUrl}/api/sessions/${concurrent.sessionId}/cancel`, {
    headers: auth(concurrent.driverToken), data: { expectedVersion: cancelled.version }, timeout: requestTimeout,
  });
  assert.equal((await body(cancelResponse, 'session cancel')).state, 'CANCELLED');
  console.log('E2E PASS: invite privacy, authentication, idempotency, deletion, capacity, concurrent capacity and cancellation');
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const testSessions = [];
  try {
    const api = context.request;
    const health = await body(await api.get(`${baseUrl}/api/health`, { timeout: requestTimeout }), 'server health');
    assert.equal(health.status, 'ok');
    const config = await body(await api.get(`${baseUrl}/api/config`, { timeout: requestTimeout }), 'server config');
    assert(['osrm', 'naver', 'mock'].includes(config.routingProvider));
    await verifyPassengerAndDriverUi(browser, api, testSessions);
    await verifyApiFlows(api, testSessions);
  } finally {
    for (const credentials of testSessions) await cancelIfOpen(context.request, credentials);
    await context.close();
    await browser.close();
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
