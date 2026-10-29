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
    await waitScene(page, '여러 목적지, 순서는 한 번에. · 모아길 시연 프로토타입', record ? 9000 : quickWait);

    await page.locator('[data-action="new"]').first().click();
    await page.locator('#qr-card').waitFor({ state: 'visible' });
    await waitScene(page, '새 이동을 만들면 승객 전용 초대 링크가 발급됩니다. 촬영 화면에서는 실제 QR과 토큰을 가렸습니다.', record ? 9000 : quickWait);

    await page.locator('[data-action="preview"]').click();
    const phone = page.frameLocator('iframe[title="시연용 참가자 모바일 화면"]');
    await phone.locator('#display-name').waitFor({ state: 'visible' });
    await phone.locator('#display-name').fill('은지');
    await phone.locator('#place-label').fill('시연 위치 A');
    await phone.locator('#pin-map').click({ position: { x: 240, y: 170 } });
    if (!record) {
      await phone.locator('#coord-lat').fill('37.481234');
      await phone.locator('#coord-lon').fill('126.981234');
      assert((await phone.locator('#read-lat').innerText()) === '37.48123', '수동 위도 입력이 지도 핀에 반영되지 않았습니다.');
    }
    await waitScene(page, '가상 휴대폰 화면에서 별명과 핀을 등록합니다. 이 페이지는 실제 참가자 웹과 같은 API를 씁니다.', record ? 8500 : quickWait);
    await phone.locator('.phone-submit').click();
    if (process.env.MOAGIL_DEBUG === '1') {
      await page.waitForTimeout(700);
      console.log('PHONE BODY', (await phone.locator('body').innerText()).slice(0, 700));
    }
    await phone.getByText('등록 완료 · 은지').waitFor({ state: 'visible' });
    if (!record) await verifyAccessAndCapacity(context, page);
    await waitScene(page, '서버에 등록된 위치가 차량 화면에 동기화됐습니다. 이 촬영은 실제 휴대폰 대신 가상 브라우저를 사용합니다.', record ? 9000 : quickWait);

    await page.locator('[data-action="close-preview"]').click();
    await page.locator('.passenger-row').filter({ hasText: '은지' }).first().waitFor({ state: 'visible' });
    assert(await page.locator('[data-action="plan"]').isDisabled(), '정차 상태 확인 전 경로 계산이 열려 있습니다.');
    await waitScene(page, '정차 상태가 확인되기 전에는 운전자 입력을 잠급니다.', record ? 7000 : quickWait);
    await page.locator('.park-toggle').click();
    assert(!(await page.locator('[data-action="plan"]').isDisabled()), '정차(시연) 확인 뒤에도 경로 계산이 잠겨 있습니다.');
    await waitScene(page, '참가 화면을 닫으면 운전자 목록에서 새 위치를 확인할 수 있습니다.', record ? 6500 : quickWait);
    await page.locator('[data-action="demo"]').click();
    await page.locator('.passenger-row').filter({ hasText: '동승자 D' }).waitFor({ state: 'visible' });
    if (!record) await verifyVersionConflict(context, page);
    await waitScene(page, '시연 데이터로 네 명의 목적지를 불러옵니다. 좌표는 예시 데이터이고 실제 도로가 아닙니다.', record ? 8000 : quickWait);

    await page.locator('.park-toggle').click();
    const allOrdersResponse = waitResponse(page, '/plan');
    await page.locator('[data-action="plan"]').click();
    assert((await allOrdersResponse).ok(), '전체 순서 비교가 실패했습니다.');
    await page.getByText(/24\s*가지 순서 비교/).waitFor({ state: 'visible' });
    await waitScene(page, '동승자 네 명의 순서를 모두 비교합니다. 최대 4! = 24가지이며 전부 시연용 추정입니다.', record ? 9000 : quickWait);

    const settingResponse = waitResponse(page, '/settings');
    await page.locator('#priority').selectOption({ label: '동승자 B' });
    assert((await settingResponse).ok(), '우선 하차 설정 저장이 실패했습니다.');
    await page.locator('#priority').waitFor({ state: 'visible' });
    await waitScene(page, '정차(시연) 상태에서 동승자 B를 먼저 내릴 사람으로 지정했습니다.', record ? 8500 : quickWait);

    const planResponse = waitResponse(page, '/plan');
    await page.locator('[data-action="plan"]').click();
    assert((await planResponse).ok(), '경로 순서 계산이 실패했습니다.');
    await page.getByText(/6\s*가지 순서 비교/).waitFor({ state: 'visible' });
    await waitScene(page, 'B가 첫 번째인 여섯 가지 순서를 비교했습니다. 추천 시간은 모의 직선거리 추정입니다.', record ? 10000 : quickWait);

    const moveResponse = waitResponse(page, '/select-order');
    await page.locator('.order-item').filter({ hasText: '동승자 D' }).locator('[data-action="move"][data-delta="-1"]').click();
    assert((await moveResponse).ok(), '수동 순서 변경이 실패했습니다.');
    await waitScene(page, '추천을 확인한 뒤 정차 상태(시연)에서 순서를 직접 조정할 수도 있습니다.', record ? 9000 : quickWait);

    await waitScene(page, '화면에는 비교 개수, 선택 순서, 예상 시간과 데이터 출처를 함께 표시합니다.', record ? 8000 : quickWait);
    const startResponse = waitResponse(page, '/start');
    await page.locator('[data-action="start"]').click();
    assert((await startResponse).ok(), '모의 안내 시작이 실패했습니다.');
    await page.locator('.safety-banner').filter({ hasText: '모의 내비 안내' }).waitFor({ state: 'visible' });
    await waitScene(page, '경로를 확정하면 모의 내비 화면이 열립니다. 실제 차량 내비게이션은 연결되지 않았습니다.', record ? 9000 : quickWait);

    for (let stop = 0; stop < 4; stop++) {
      const response = waitResponse(page, '/complete-stop');
      await page.locator('[data-action="complete-stop"]').click();
      assert((await response).ok(), '하차 단계 ' + (stop + 1) + '을 완료하지 못했습니다.');
      await waitScene(page, '정차 확인 후 ' + (stop + 1) + '번째 하차를 완료합니다. 다음 목적지는 운전자가 다시 확인합니다.', record ? 6500 : quickWait);
    }
    await page.getByText('운전자 최종 목적지', { exact: true }).waitFor({ state: 'visible' });
    await waitScene(page, '승객 하차가 끝나면 운전자 최종 목적지까지 이어갑니다.', record ? 8500 : quickWait);
    const finishedCredentials = await page.evaluate(() => JSON.parse(sessionStorage.getItem('moagil-driver') || 'null'));
    const finishResponse = waitResponse(page, '/complete-trip');
    await page.locator('[data-action="complete-trip"]').click();
    assert((await finishResponse).ok(), '시연 이동을 완료하지 못했습니다.');
    if (!record && finishedCredentials) {
      const closedInvite = await context.request.post(baseUrl + '/api/sessions/' + finishedCredentials.sessionId + '/join-info', { data: { inviteToken: finishedCredentials.inviteToken } });
      assert(closedInvite.status() === 409, '완료된 세션의 초대 링크를 비활성화하지 않았습니다.');
    }
    await waitScene(page, '모아길은 이동 순서와 협의를 돕는 시연 프로토타입입니다. 실제 도로·교통·내비는 별도 검증이 필요합니다.', record ? 11000 : quickWait);

    console.log('E2E PASS: 모바일 등록, 서버 동기화, 24→6 순열, 수동 순서, 정차 차단, 단계별 모의 안내 완료');
  } finally {
    video = page.video();
    await context.close();
    await browser.close();
  }
  if (record && video) {
    const source = await video.path();
    const target = path.join(outputDir, 'MOAGIL-demo.webm');
    if (path.resolve(source) !== path.resolve(target)) fs.copyFileSync(source, target);
    console.log('VIDEO ' + target);
  }
}

main().catch(error => { console.error(error); process.exit(1); });
