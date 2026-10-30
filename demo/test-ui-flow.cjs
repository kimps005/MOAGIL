const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.MOAGIL_PLAYWRIGHT_MODULE || 'playwright');

const root = path.resolve(__dirname, '..');
const baseUrl = 'http://127.0.0.1:8080';
const planCalls = [];
let session = null;

function snapshot() {
  return JSON.parse(JSON.stringify(session));
}

function factorial(value) {
  return value < 2 ? 1 : value * factorial(value - 1);
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  const page = await browser.newPage();
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== baseUrl) return route.fulfill({ status: 204, body: '' });

    if (url.pathname === '/' || url.pathname.startsWith('/join/')) {
      return route.fulfill({ contentType: 'text/html', body: `<!doctype html><html lang='ko'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>모아길</title><link rel='stylesheet' href='/styles.css'></head><body><div id='app'></div><script>window.MOAGILMaps={createDriverMap:()=>({destroy(){}}),createPassengerMap:()=>({destroy(){},setPoint(){}})};</script><script src='/app.js'></script></body></html>` });
    }
    if (url.pathname === '/styles.css') return route.fulfill({ contentType: 'text/css', body: fs.readFileSync(path.join(root, 'web/styles.css'), 'utf8') });
    if (url.pathname === '/app.js') return route.fulfill({ contentType: 'text/javascript', body: fs.readFileSync(path.join(root, 'web/app.js'), 'utf8') });
    if (url.pathname === '/api/config') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ mapProvider: 'osm', routingProvider: 'mock' }) });

    if (url.pathname === '/api/sessions/test-session/join-info' && route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      assert.equal(body.inviteToken, 'invite-test');
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ state: 'COLLECTING', remaining: 4, expiresAt: '2030-01-01T00:00:00Z' }) });
    }

    if (url.pathname === '/api/sessions/test-session/passengers' && route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      assert.equal(body.inviteToken, 'invite-test');
      assert.equal(body.displayName, '수연');
      return route.fulfill({ status: 201, contentType: 'application/json', body: '{}' });
    }

    if (url.pathname === '/api/sessions' && route.request().method() === 'POST') {
      session = {
        sessionId: 'test-session', version: 1, state: 'COLLECTING',
        startPoint: { lat: 37.5665, lon: 126.978 }, finalPoint: null,
        priorityPassengerId: null, passengers: [], selectedOrder: [],
        activeIndex: 0, routeSnapshot: null,
      };
      return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ sessionId: session.sessionId, driverToken: 'driver-test', inviteToken: 'invite-test', joinBaseUrl: baseUrl, session }) });
    }

    if (url.pathname === '/api/sessions/test-session' && route.request().method() === 'GET') {
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(snapshot()) });
    }

    if (url.pathname === '/api/sessions/test-session/plan' && route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      planCalls.push({ expectedVersion: body.expectedVersion, passengerCount: session.passengers.length });
      if (body.expectedVersion !== session.version || !session.passengers.length) {
        return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ error: { message: '화면 버전을 다시 확인해 주세요' } }) });
      }
      const order = session.passengers.map(person => person.id);
      session.state = 'PLANNED';
      session.version += 1;
      session.selectedOrder = order;
      session.routeSnapshot = {
        order, durationMs: 600000, distanceM: 12000,
        evaluatedCount: factorial(session.passengers.length), basisVersion: body.expectedVersion,
        geometry: [], note: '자동 계산 테스트',
      };
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(snapshot()) });
    }

    return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ error: { message: 'not found' } }) });
  });

  try {
    await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
    await page.locator('#qr-card').waitFor();
    await page.waitForFunction(() => document.querySelector('#invite-qr')?.width > 0);
    assert.match(await page.locator('.qr-intro').innerText(), /QR을 스캔/);

    session.passengers.push({ id: 'passenger-a', displayName: '민수', point: { lat: 37.51, lon: 127.01, label: '강남역' } });
    session.version += 1;
    await page.locator('.passenger-name').filter({ hasText: '민수' }).waitFor();
    await page.waitForFunction(() => document.querySelector('.route-meta')?.textContent.includes('1가지 순서 비교'));

    session.state = 'COLLECTING';
    session.version += 1;
    session.routeSnapshot = null;
    session.selectedOrder = [];
    session.passengers.push({ id: 'passenger-b', displayName: '지우', point: { lat: 37.52, lon: 127.02, label: '선릉역' } });
    session.version += 1;
    await page.locator('.passenger-name').filter({ hasText: '지우' }).waitFor();
    await page.waitForFunction(() => document.querySelector('.route-meta')?.textContent.includes('2가지 순서 비교'));

    assert.deepEqual(planCalls.map(call => call.passengerCount), [1, 2], '각 위치 등록 뒤 자동으로 해당 인원수 경로를 계산해야 합니다.');
    assert.equal(await page.locator('.route-workspace .map-card').count(), 1);
    assert.equal(await page.locator('.session-panel').count(), 1);
    console.log('UI PASS: 첫 화면 QR, 등록 후 지도/세션, 1명·2명 자동 재계산');

    await page.goto(baseUrl + '/join/test-session?invite=invite-test', { waitUntil: 'domcontentloaded' });
    await page.locator('#display-name').waitFor();
    await page.locator('#pin-map').waitFor();
    assert.match(await page.locator('.passenger-heading h1').innerText(), /내릴 곳을 직접/);
    assert.equal(await page.locator('.phone-submit').isVisible(), true);
    await page.locator('#display-name').fill('수연');
    await page.locator('#place-label').fill('강남역');
    await page.locator('.phone-submit').click();
    await page.locator('.passenger-result').waitFor();
    assert.match(await page.locator('.passenger-result').innerText(), /수연/);
    console.log('UI PASS: QR 참가 화면 표시 및 위치 등록 완료');
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
