const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const { chromium } = require(process.env.MOAGIL_PLAYWRIGHT_MODULE || 'playwright');
const baseUrl = process.env.MOAGIL_BASE_URL || 'http://127.0.0.1:8080';
const record = process.argv.includes('--record');
const quickWait = record ? 750 : 180;
const sceneWait = record ? 10500 : 250;
const outputDir = path.resolve(__dirname, 'video');

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function caption(page, text) {
  await page.evaluate(value => {
    let node = document.getElementById('demo-caption');
    if (!node) {
      node = document.createElement('div');
      node.id = 'demo-caption';
      node.style.cssText = 'position:fixed;z-index:40;left:50%;bottom:24px;transform:translateX(-50%);max-width:calc(100vw - 36px);padding:12px 20px;border:1px solid rgba(125,228,213,.28);border-radius:13px;background:rgba(7,17,27,.93);box-shadow:0 8px 30px rgba(0,0,0,.28);color:#f1f7f8;font:600 14px/1.5 Pretendard,system-ui,sans-serif;text-align:center;letter-spacing:-.02em;transition:opacity .2s';
      document.body.appendChild(node);
    }
    node.textContent = value;
  }, text);
}

async function waitScene(page, text, duration = sceneWait) {
  await caption(page, text);
  await page.waitForTimeout(duration);
}

async function waitResponse(page, suffix) {
  return page.waitForResponse(response => response.url().includes(suffix) && response.request().method() !== 'OPTIONS');
}

async function verifyAccessAndCapacity(context, page) {
  const credentials = await page.evaluate(() => JSON.parse(sessionStorage.getItem('moagil-driver') || 'null'));
  assert(credentials, '차량 토큰을 브라우저 세션에 보관하지 않았습니다.');
  const unauthenticated = await context.request.get(baseUrl + '/api/sessions/' + credentials.sessionId);
  assert(unauthenticated.status() === 401, '차량 인증 없는 세션 조회가 허용됐습니다.');
  const inviteInfo = await context.request.post(baseUrl + '/api/sessions/' + credentials.sessionId + '/join-info', { data: { inviteToken: credentials.inviteToken } });
  const inviteBody = await inviteInfo.json();
  assert(inviteInfo.ok() && !('passengers' in inviteBody) && !('driverToken' in inviteBody), '초대 조회에서 동승자 정보 또는 차량 토큰이 노출됐습니다.');

  for (const name of ['검증 B', '검증 C', '검증 D']) {
    const body = { inviteToken: credentials.inviteToken, displayName: name, point: { lat: 37.51, lon: 127.02, label: '검증 위치' }, idempotencyKey: crypto.randomBytes(18).toString('hex') };
    const response = await context.request.post(baseUrl + '/api/sessions/' + credentials.sessionId + '/passengers', { data: body });
    assert(response.status() === 201, name + ' 등록이 실패했습니다.');
    if (name === '검증 B') {
      const duplicate = await context.request.post(baseUrl + '/api/sessions/' + credentials.sessionId + '/passengers', { data: body });
      const duplicateBody = await duplicate.json();
      assert(duplicate.status() === 200 && duplicateBody.duplicate === true, '같은 idempotency key의 재시도가 중복을 만들었습니다.');
      const changed = await context.request.post(baseUrl + '/api/sessions/' + credentials.sessionId + '/passengers', { data: { ...body, displayName: '이름 변경 시도' } });
      assert(changed.status() === 409, 'idempotency key의 다른 payload를 거부하지 않았습니다.');
    }
  }
  const fifth = await context.request.post(baseUrl + '/api/sessions/' + credentials.sessionId + '/passengers', { data: { inviteToken: credentials.inviteToken, displayName: '다섯 번째', point: { lat: 37.5, lon: 127.0, label: '검증 위치' }, idempotencyKey: crypto.randomBytes(18).toString('hex') } });
  const fifthBody = await fifth.json();
  assert(fifth.status() === 409 && fifthBody.error?.code === 'SESSION_FULL', '다섯 번째 참가자를 원자적으로 거부하지 않았습니다.');

  const emptySession = await context.request.post(baseUrl + '/api/sessions', { data: {} });
  const emptyCredentials = await emptySession.json();
  const simultaneous = await Promise.all(Array.from({ length: 5 }, (_, index) => context.request.post(baseUrl + '/api/sessions/' + emptyCredentials.sessionId + '/passengers', {
    data: {
      inviteToken: emptyCredentials.inviteToken,
      displayName: '동시 참가 ' + (index + 1),
      point: { lat: 37.51 + index / 1000, lon: 127.02, label: '동시 검증 지점' },
      idempotencyKey: crypto.randomBytes(18).toString('hex'),
    },
  })));
  const concurrentBodies = await Promise.all(simultaneous.map(response => response.json()));
  assert(simultaneous.filter(response => response.status() === 201).length === 4, '동시 참가에서 정확히 네 명을 받지 않았습니다.');
  assert(simultaneous.filter((response, index) => response.status() === 409 && concurrentBodies[index].error?.code === 'SESSION_FULL').length === 1, '동시 다섯 번째 참가를 정원 초과로 거부하지 않았습니다.');
}

async function verifyVersionConflict(context, page) {
  const credentials = await page.evaluate(() => JSON.parse(sessionStorage.getItem('moagil-driver') || 'null'));
  const response = await context.request.put(baseUrl + '/api/sessions/' + credentials.sessionId + '/settings', {
    headers: { Authorization: 'Bearer ' + credentials.driverToken },
    data: { expectedVersion: 0, startPoint: { lat: 37.525, lon: 127.0, label: '시연 출발지' }, finalPoint: null, priorityPassengerId: null },
  });
  assert(response.status() === 409, '오래된 화면 version 변경을 거부하지 않았습니다.');
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    recordVideo: record ? { dir: outputDir, size: { width: 1440, height: 900 } } : undefined,
  });
  const page = await context.newPage();
  if (process.env.MOAGIL_DEBUG === '1') {
    page.on('pageerror', error => console.error('PAGEERROR', error.stack));
    page.on('console', message => { if (message.type() === 'error') console.error('CONSOLE', message.text()); });
    page.on('response', response => { if (response.url().includes('/api/')) console.log('API', response.status(), new URL(response.url()).pathname); });
  }
  let video;
  try {
    await page.goto(baseUrl + '/?capture=1', { waitUntil: 'networkidle' });
    assert((await page.title()).includes('모아길'), '차량 시작 화면이 열리지 않았습니다.');
    if (record) {
      const configResponse = await page.request.get(baseUrl + '/api/config');
      assert(configResponse.ok(), '경로 제공자 설정을 확인하지 못했습니다.');
      const config = await configResponse.json();
      assert(['osrm', 'naver'].includes(config.routingProvider), '실제 도로 경로 제공자(OSRM 또는 NAVER)를 사용해 서버를 시작한 뒤 영상을 촬영하세요.');
    }
    await waitScene(page, '여러 목적지, 순서는 한 번에. · 모아길 시연 프로토타입', record ? 9000 : quickWait);

    await page.locator('#qr-card').waitFor({ state: 'visible' });
    await waitScene(page, '새 이동을 만들면 승객 전용 초대 링크가 발급됩니다. 촬영 화면에서는 실제 QR과 토큰을 가렸습니다.', record ? 9000 : quickWait);

    let phoneContext;
    let phone;
    if (record) {
      await page.locator('[data-action="preview"]').click();
      phone = page.frameLocator('iframe[title="시연용 참가자 모바일 화면"]');
    } else {
      const credentials = await page.evaluate(() => JSON.parse(sessionStorage.getItem('moagil-driver') || 'null'));
      phoneContext = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1,