import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {access, mkdtemp, readFile, rm} from 'node:fs/promises';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const staticRoot = path.join(repositoryRoot, 'src/main/resources/static');
const templatePath = path.join(repositoryRoot, 'src/main/resources/templates/account/mypage.html');
const browserPath = await findBrowser();

test('실제 브라우저에서 오퍼 검색 결과를 다시 렌더링한다', {skip: !browserPath}, async () => {
  const html = await renderInBrowser('filter');
  const recordList = renderedRecordList(html);

  assert.match(html, /data-browser-test="ready"/);
  assert.match(html, /전체 4건 중 1건/);
  assert.match(recordList, /도시락 견적/);
  assert.doesNotMatch(recordList, /샌드위치 제안/);
});

test('실제 브라우저에서 실패한 소스의 부분 재시도 상태를 표시한다', {skip: !browserPath}, async () => {
  const html = await renderInBrowser('partial');

  assert.match(html, /data-browser-test="ready"/);
  assert.match(html, /보낸 제안 내역을 불러오지 못해 나머지 항목만 표시합니다/);
  assert.match(html, /data-retry-partial=""/);
  assert.match(html, /전체 3건/);
});

test('모바일 화면에서 사이드바와 거래 카드를 단일 열로 배치한다', {skip: !browserPath}, async () => {
  const html = await renderInBrowser('mobile');

  assert.match(html, /data-browser-test="ready"/);
  assert.match(html, /data-sidebar-position="static"/);
  assert.match(html, /data-record-display="grid"/);
});

test('구매 내역 화면에서 거래 상태를 단순 상태로 표시한다', {skip: !browserPath}, async () => {
  const html = await renderInBrowser('purchases');
  const recordList = renderedRecordList(html);

  assert.match(html, /data-browser-test="ready"/);
  assert.match(html, /전체 3건/);
  assert.match(recordList, /제안/);
  assert.match(recordList, /진행 중/);
  assert.match(recordList, /완료/);
});

test('채팅 목록에서 최근 메시지와 채팅방 이동 링크를 표시한다', {skip: !browserPath}, async () => {
  const html = await renderInBrowser('chats');
  const recordList = renderedRecordList(html);

  assert.match(html, /data-browser-test="ready"/);
  assert.match(recordList, /배송 시간을 확인해 주세요/);
  assert.match(recordList, /href="\/chat\?roomId=17"/);
  assert.match(recordList, /is-clickable/);
});

async function renderInBrowser(scenario) {
  const server = http.createServer((request, response) => serve(request, response, scenario));
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const {port} = server.address();
  const userDataDirectory = await mkdtemp(path.join(os.tmpdir(), 'matcheat-edge-'));
  try {
    const view = ['purchases', 'chats'].includes(scenario) ? scenario : 'offers';
    return await runBrowser(
      `http://127.0.0.1:${port}/mypage/${view}?scenario=${scenario}`,
      userDataDirectory,
      scenario,
    );
  } finally {
    await new Promise((resolve) => server.close(resolve));
    await rm(userDataDirectory, {recursive: true, force: true});
  }
}

function renderedRecordList(html) {
  const start = html.indexOf('<div class="record-list"');
  const end = html.indexOf('<button class="load-more-button"', start);
  assert.notEqual(start, -1, 'Rendered record list was not found');
  assert.notEqual(end, -1, 'Rendered load-more control was not found');
  return html.slice(start, end);
}

async function serve(request, response, scenario) {
  const pathname = new URL(request.url, 'http://localhost').pathname;
  if (['/mypage/offers', '/mypage/purchases', '/mypage/chats'].includes(pathname)) {
    const template = await readFile(templatePath, 'utf8');
    const html = prepareTemplate(template, scenario);
    send(response, 200, 'text/html; charset=utf-8', html);
    return;
  }

  const relativePath = decodeURIComponent(pathname).replace(/^\/+/, '');
  const filePath = path.resolve(staticRoot, relativePath);
  if (!filePath.startsWith(`${staticRoot}${path.sep}`)) {
    send(response, 403, 'text/plain; charset=utf-8', 'Forbidden');
    return;
  }
  try {
    const content = await readFile(filePath);
    const contentType = filePath.endsWith('.js')
      ? 'text/javascript; charset=utf-8'
      : filePath.endsWith('.css') ? 'text/css; charset=utf-8' : 'application/octet-stream';
    send(response, 200, contentType, content);
  } catch {
    send(response, 404, 'text/plain; charset=utf-8', 'Not found');
  }
}

function prepareTemplate(template, scenario) {
  const resolved = template
    .replace(/th:(href|src)="@\{([^}]+)}"/g, '$1="$2"')
    .replace(/<header[^>]*th:replace="[^"]*"[^>]*><\/header>/, '<header></header>');
  const bootstrap = `<script>
    sessionStorage.setItem('matcheat.accessToken', 'browser-test-token');
    const responses = {
      '/api/v1/account/me': {name: '브라우저 테스트', email: 'browser@example.com', role: 'SELLER', sellerStatus: 'APPROVED'},
      '/api/v1/proposals/received': [{id: 1, requestId: 11, itemName: '샌드위치 제안', status: 'SENT', createdAt: '2026-09-01T09:00:00'}],
      '/api/v1/proposals/sent': [{id: 2, requestId: 12, itemName: '행사 제안', status: 'ACCEPTED', createdAt: '2026-09-01T10:00:00'}],
      '/api/v1/estimates/received': [{id: 3, itemName: '케이터링 견적', status: 'REQUESTED', createdAt: '2026-09-01T11:00:00'}],
      '/api/v1/estimates/sent': [{id: 4, itemName: '도시락 견적', status: 'REQUESTED', createdAt: '2026-09-01T12:00:00'}],
      '/api/v1/orders/purchases': [
        {activityId: 'PROPOSAL:5', sourceType: 'PROPOSAL', sourceId: 5, direction: 'RECEIVED', sourceStatus: 'SENT', itemName: '제안 거래'},
        {activityId: 'QUOTE:6', sourceType: 'QUOTE', sourceId: 6, direction: 'SENT', sourceStatus: 'ACCEPTED', itemName: '진행 거래'},
        {activityId: 'QUOTE:7', sourceType: 'QUOTE', sourceId: 7, direction: 'RECEIVED', sourceStatus: 'ACCEPTED', paymentStatus: 'COMPLETED', itemName: '완료 거래'},
      ],
      '/api/v1/chat-rooms': [
        {chatRoomId: 17, originType: 'PROPOSAL', status: 'ACTIVE', lastMessage: '배송 시간을 확인해 주세요.', lastMessageAt: '2026-09-02T12:30:00'},
      ],
    };
    window.fetch = async (input) => {
      const pathname = new URL(String(input), location.origin).pathname;
      if (${JSON.stringify(scenario)} === 'partial' && pathname === '/api/v1/proposals/sent') {
        return new Response(JSON.stringify({message: 'temporary failure'}), {status: 503, headers: {'Content-Type': 'application/json'}});
      }
      const payload = responses[pathname];
      return payload === undefined
        ? new Response(null, {status: 404})
        : new Response(JSON.stringify(payload), {status: 200, headers: {'Content-Type': 'application/json'}});
    };
  </script>`;
  const interaction = `<script type="module">
    const interval = setInterval(() => {
      const tools = document.querySelector('[data-record-tools]');
      if (!tools || tools.hidden) return;
      clearInterval(interval);
      if (${JSON.stringify(scenario)} === 'filter') {
        const search = document.querySelector('[data-record-search]');
        search.value = '도시락';
        search.dispatchEvent(new Event('input', {bubbles: true}));
      }
      if (${JSON.stringify(scenario)} === 'mobile') {
        document.documentElement.dataset.sidebarPosition = getComputedStyle(document.querySelector('.mypage-sidebar')).position;
        document.documentElement.dataset.recordDisplay = getComputedStyle(document.querySelector('.record-card')).display;
      }
      document.documentElement.dataset.browserTest = 'ready';
    }, 20);
  </script>`;
  return resolved
    .replace('<script type="module" src="/account/js/mypage.js"', `${bootstrap}<script type="module" src="/account/js/mypage.js"`)
    .replace('</body>', `${interaction}</body>`);
}

function send(response, status, contentType, body) {
  response.writeHead(status, {'Content-Type': contentType});
  response.end(body);
}

async function runBrowser(url, userDataDirectory, scenario) {
  return new Promise((resolve, reject) => {
    const args = [
      '--headless=new',
      '--disable-gpu',
      '--no-first-run',
      '--no-default-browser-check',
      `--user-data-dir=${userDataDirectory}`,
      '--virtual-time-budget=2500',
      '--dump-dom',
    ];
    if (scenario === 'mobile') args.push('--window-size=390,844');
    args.push(url);
    const child = spawn(browserPath, args, {windowsHide: true});
    let stdout = '';
    let stderr = '';
    child.stdout.setEncoding('utf8').on('data', (chunk) => { stdout += chunk; });
    child.stderr.setEncoding('utf8').on('data', (chunk) => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) resolve(stdout);
      else reject(new Error(`Browser exited with ${code}: ${stderr}`));
    });
  });
}

async function findBrowser() {
  const candidates = [
    process.env.BROWSER_BIN,
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    '/usr/bin/microsoft-edge',
    '/usr/bin/google-chrome',
    '/usr/bin/chromium',
  ].filter(Boolean);
  for (const candidate of candidates) {
    try {
      await access(candidate);
      return candidate;
    } catch {
      // Try the next known browser location.
    }
  }
  return null;
}
