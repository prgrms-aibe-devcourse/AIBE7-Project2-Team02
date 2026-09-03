import { authFetch, readApiBody, readAccessToken } from './auth-client.js';

const viewKey = location.pathname.endsWith('/users')
  ? 'users'
  : location.pathname.endsWith('/sellers')
    ? 'sellers'
    : location.pathname.endsWith('/reports') ? 'reports' : 'dashboard';
const viewLabels = {
  dashboard: ['OVERVIEW', '운영 현황'],
  users: ['MEMBERS', '회원 관리'],
  sellers: ['SELLER REVIEW', '판매자 심사'],
  reports: ['REPORT DESK', '신고함'],
};
let rejectionTarget = null;

if (!readAccessToken()) redirectToLogin();

const profileResponse = await authFetch('/api/v1/account/me');
if (profileResponse.status === 401) redirectToLogin();
if (!profileResponse.ok) fail('관리자 계정을 확인하지 못했습니다.');
const profile = await profileResponse.json();
if (profile.role !== 'ADMIN') {
  window.location.replace('/');
  throw new Error('Administrator role required');
}

await loadPendingReportBadge();

document.querySelector('[data-admin-identity]').textContent = `${profile.name} · ${profile.email}`;
document.querySelector(`[data-admin-view="${viewKey}"]`)?.setAttribute('aria-current', 'page');
document.querySelector('[data-view-kicker]').textContent = viewLabels[viewKey][0];
document.querySelector('[data-view-title]').textContent = viewLabels[viewKey][1];
document.querySelector(`[data-panel="${viewKey}"]`).hidden = false;

if (viewKey === 'dashboard') await loadDashboard();
if (viewKey === 'users') {
  document.querySelector('[data-user-filter]').addEventListener('submit', (event) => {
    event.preventDefault();
    loadUsers(0);
  });
  await loadUsers(0);
}
if (viewKey === 'sellers') {
  document.querySelector('[data-seller-filter]').addEventListener('submit', (event) => {
    event.preventDefault();
    loadSellers(0);
  });
  configureRejectionDialog();
  await loadSellers(0);
}
if (viewKey === 'reports') {
  document.querySelector('[data-report-filter]').addEventListener('submit', (event) => {
    event.preventDefault();
    loadReports(0);
  });
  await loadReports(0);
}

async function loadDashboard() {
  const body = await request('/api/v1/admin/dashboard');
  if (!body) return;
  document.querySelector('[data-total-users]').textContent = body.totalUsers.toLocaleString('ko-KR');
  document.querySelector('[data-pending-sellers]').textContent = body.pendingSellerApplications.toLocaleString('ko-KR');
  finishLoading();
}

async function loadUsers(page) {
  startLoading();
  const form = new FormData(document.querySelector('[data-user-filter]'));
  const query = new URLSearchParams({ page, size: 15 });
  if (form.get('keyword')) query.set('keyword', form.get('keyword'));
  if (form.get('status')) query.set('status', form.get('status'));
  const result = await request(`/api/v1/admin/users?${query}`);
  if (!result) return;

  const rows = document.querySelector('[data-user-rows]');
  rows.replaceChildren(...result.content.map(userRow));
  document.querySelector('[data-user-empty]').hidden = result.content.length !== 0;
  renderPagination(document.querySelector('[data-user-pagination]'), result, loadUsers);
  finishLoading();
}

function userRow(user) {
  const row = document.createElement('tr');
  row.append(
    cell(memberBlock(user.name, user.email)),
    textCell(user.role === 'ADMIN' ? '관리자' : '회원'),
    cell(stateBadge(user.status)),
    textCell(formatDate(user.createdAt)),
  );
  const actionCell = document.createElement('td');
  const button = actionButton(
    user.status === 'SUSPENDED' ? '활성화' : '정지',
    user.status === 'SUSPENDED' ? 'is-approve' : 'is-danger');
  button.disabled = user.status === 'WITHDRAWN';
  button.addEventListener('click', async () => {
    const target = user.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
    const reason = target === 'SUSPENDED' ? prompt('정지 사유를 입력하세요. (최대 500자)') : null;
    if (target === 'SUSPENDED' && (!reason || !reason.trim())) return;
    if (!confirm(`${user.name} 회원을 ${target === 'ACTIVE' ? '활성화' : '정지'}하시겠습니까?`)) return;
    button.disabled = true;
    const changed = await request(`/api/v1/admin/users/${user.userId}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: target, reason }),
    });
    if (changed) await loadUsers(0);
    else button.disabled = false;
  });
  actionCell.append(button);
  row.append(actionCell);
  return row;
}

async function loadSellers(page) {
  startLoading();
  const form = new FormData(document.querySelector('[data-seller-filter]'));
  const query = new URLSearchParams({ page, size: 12 });
  if (form.get('status')) query.set('status', form.get('status'));
  const result = await request(`/api/v1/admin/seller-applications?${query}`);
  if (!result) return;

  const rows = document.querySelector('[data-seller-rows]');
  rows.replaceChildren(...result.content.map(sellerCard));
  document.querySelector('[data-seller-empty]').hidden = result.content.length !== 0;
  renderPagination(document.querySelector('[data-seller-pagination]'), result, loadSellers);
  finishLoading();
}

function sellerCard(seller) {
  const card = document.createElement('article');
  card.className = 'seller-card';
  const business = document.createElement('div');
  const title = document.createElement('h3');
  title.textContent = seller.businessName;
  business.append(title, paragraph(`사업자번호 ${seller.businessNumber}`), paragraph(`신청 ${formatDate(seller.appliedAt)}`));

  const applicant = document.createElement('div');
  applicant.append(memberBlock(seller.userName, seller.userEmail), stateBadge(seller.status));
  if (seller.rejectionReason) applicant.append(paragraph(`거부 사유: ${seller.rejectionReason}`));

  const actions = document.createElement('div');
  actions.className = 'seller-actions';
  const approve = actionButton('승인', 'is-approve');
  const reject = actionButton('거부', 'is-danger');
  approve.disabled = reject.disabled = seller.status !== 'PENDING';
  approve.addEventListener('click', async () => {
    if (!confirm(`${seller.businessName}의 판매자 신청을 승인하시겠습니까?`)) return;
    const changed = await reviewSeller(seller.sellerId, 'APPROVED', null);
    if (changed) await loadSellers(0);
  });
  reject.addEventListener('click', () => {
    rejectionTarget = seller.sellerId;
    document.querySelector('[data-rejection-dialog]').showModal();
  });
  actions.append(approve, reject);
  card.append(business, applicant, actions);
  return card;
}

function configureRejectionDialog() {
  const dialog = document.querySelector('[data-rejection-dialog]');
  const form = document.querySelector('[data-rejection-form]');
  document.querySelector('[data-dialog-cancel]').addEventListener('click', () => dialog.close());
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const reason = new FormData(form).get('reason').trim();
    if (!reason) return;
    const changed = await reviewSeller(rejectionTarget, 'REJECTED', reason);
    if (changed) {
      dialog.close();
      form.reset();
      await loadSellers(0);
    }
  });
}

async function reviewSeller(sellerId, status, rejectionReason) {
  return request(`/api/v1/admin/seller-applications/${sellerId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status, rejectionReason }),
  });
}

async function loadReports(page) {
  startLoading();
  const status = new FormData(document.querySelector('[data-report-filter]')).get('status');
  const query = new URLSearchParams({ page, size: 12 });
  if (status) query.set('status', status);
  const result = await request(`/api/v1/admin/reports?${query}`);
  if (!result) return;
  document.querySelector('[data-report-rows]').replaceChildren(...result.content.map(reportCard));
  document.querySelector('[data-report-empty]').hidden = result.content.length !== 0;
  renderPagination(document.querySelector('[data-report-pagination]'), result, loadReports);
  finishLoading();
}

function reportCard(report) {
  const card = document.createElement('article');
  card.className = 'admin-report-card';
  const heading = document.createElement('div');
  const identity = document.createElement('div');
  const title = document.createElement('h3');
  title.textContent = report.title;
  identity.append(title, memberBlock(report.reporterName, report.reporterEmail));
  heading.append(identity, stateBadge(report.status));

  const message = paragraph(report.message);
  message.className = 'admin-report-message';
  const meta = paragraph(`접수 ${formatDateTime(report.createdAt)}`);
  meta.className = 'admin-report-meta';
  card.append(heading, message, meta);
  if (report.reportedUserId) {
    const reported = paragraph(`피신고자 회원 #${report.reportedUserId}`);
    reported.className = 'admin-report-target';
    card.append(reported);
  }
  if (report.targetType && report.targetId) {
    const target = paragraph(`신고 대상: ${reportTargetLabel(report.targetType)} #${report.targetId}`);
    target.className = 'admin-report-target';
    card.append(target);
  }

  if (report.targetSnapshot) {
    const snapshot = paragraph(report.targetSnapshot);
    snapshot.className = 'admin-report-response';
    card.append(snapshot);
  }
  const evidenceButton = actionButton('첨부 증거 보기', '');
  evidenceButton.addEventListener('click', async () => {
    const files = await request(`/api/v1/admin/reports/${report.reportId}/attachments`);
    if (!files?.length) return fail('첨부된 증거 이미지가 없습니다.');
    for (const file of files) {
      const response = await authFetch(`/api/v1/admin/reports/attachments/${file.attachmentId}`);
      if (response.ok) window.open(URL.createObjectURL(await response.blob()), '_blank', 'noopener');
    }
  });
  card.append(evidenceButton);

  if (report.adminResponse) {
    const previous = paragraph(`관리자 답변: ${report.adminResponse}`);
    previous.className = 'admin-report-response';
    card.append(previous);
  }
  if (report.status !== 'RESOLVED' && report.status !== 'REJECTED') {
    const form = document.createElement('form');
    form.className = 'admin-report-form';
    const select = document.createElement('select');
    select.name = 'status';
    select.append(new Option('검토 중', 'IN_REVIEW'), new Option('처리 완료', 'RESOLVED'), new Option('반려', 'REJECTED'));
    select.value = report.status === 'PENDING' ? 'IN_REVIEW' : report.status;
    const textarea = document.createElement('textarea');
    textarea.name = 'adminResponse';
    textarea.maxLength = 2000;
    textarea.placeholder = '처리 완료 또는 반려 시 답변을 입력하세요.';
    const button = actionButton('상태 저장', 'is-approve');
    button.type = 'submit';
    form.append(select, textarea, button);
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const changed = await request(`/api/v1/admin/reports/${report.reportId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: select.value, adminResponse: textarea.value }),
      });
      if (changed) {
        await loadPendingReportBadge();
        await loadReports(0);
      }
    });
    card.append(form);
  }
  if (report.status === 'RESOLVED' && report.reportedUserId) {
    const penaltyForm = document.createElement('form');
    penaltyForm.className = 'admin-report-form';
    const days = document.createElement('select');
    [1, 3, 7, 15, 30].forEach(value => days.append(new Option(`${value}일 정지`, value)));
    const reason = document.createElement('textarea');
    reason.maxLength = 500;
    reason.placeholder = '제재 사유';
    const submit = actionButton('기간 정지 적용', 'is-danger');
    submit.type = 'submit';
    penaltyForm.append(days, reason, submit);
    penaltyForm.addEventListener('submit', async event => {
      event.preventDefault();
      const result = await request(`/api/v1/admin/reports/${report.reportId}/penalties`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ days: Number(days.value), reason: reason.value }),
      });
      if (result) submit.disabled = true;
    });
    card.append(penaltyForm);
  }
  const historyButton = actionButton('처리 이력 보기', '');
  historyButton.classList.add('admin-history-button');
  const history = document.createElement('ol');
  history.className = 'admin-report-history';
  history.hidden = true;
  historyButton.addEventListener('click', async () => {
    if (history.dataset.loaded !== 'true') {
      const entries = await request(`/api/v1/admin/reports/${report.reportId}/history`);
      if (!entries) return;
      history.replaceChildren(...entries.map(historyItem));
      history.dataset.loaded = 'true';
    }
    history.hidden = !history.hidden;
    historyButton.textContent = history.hidden ? '처리 이력 보기' : '처리 이력 닫기';
  });
  card.append(historyButton, history);
  return card;
}

function historyItem(entry) {
  const item = document.createElement('li');
  const transition = entry.previousStatus
    ? `${reportStatusLabel(entry.previousStatus)} → ${reportStatusLabel(entry.newStatus)}`
    : `${reportStatusLabel(entry.newStatus)} 접수`;
  const title = document.createElement('strong');
  title.textContent = transition;
  const meta = document.createElement('span');
  meta.textContent = `${formatDateTime(entry.changedAt)} · 처리자 #${entry.actorId}`;
  item.append(title, meta);
  if (entry.adminResponse) item.append(paragraph(entry.adminResponse));
  return item;
}

function reportStatusLabel(status) {
  return { PENDING: '접수', IN_REVIEW: '검토 중', RESOLVED: '처리 완료', REJECTED: '반려' }[status] || status;
}

async function loadPendingReportBadge() {
  try {
    const response = await authFetch('/api/v1/admin/reports?status=PENDING&page=0&size=1');
    if (!response.ok) return;
    const result = await response.json();
    const count = Number(result.totalElements) || 0;
    const badge = document.querySelector('[data-report-badge]');
    badge.textContent = count > 99 ? '99+' : String(count);
    badge.hidden = count === 0;
  } catch {
    // The badge is supplemental; the reports page keeps its own error handling.
  }
}

function reportTargetLabel(targetType) {
  return {
    ORDER_REQUEST: '구매 요청',
    PROPOSAL: '제안',
    ESTIMATE: '견적 요청',
    QUOTE: '견적 거래',
    CHAT_ROOM: '채팅방',
    PRODUCT: '상품',
  }[targetType] || targetType;
}

async function request(url, options) {
  const response = await authFetch(url, options);
  if (response.status === 401) redirectToLogin();
  const body = await readApiBody(response);
  if (!response.ok) {
    fail(body?.message || '요청을 처리하지 못했습니다.');
    return null;
  }
  return body;
}

function renderPagination(container, page, load) {
  container.replaceChildren();
  for (let index = 0; index < page.totalPages; index += 1) {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = index + 1;
    if (index === page.page) button.setAttribute('aria-current', 'page');
    button.addEventListener('click', () => load(index));
    container.append(button);
  }
}

function memberBlock(name, email) {
  const block = document.createElement('div');
  block.className = 'member-cell';
  const strong = document.createElement('strong');
  const span = document.createElement('span');
  strong.textContent = name;
  span.textContent = email;
  block.append(strong, span);
  return block;
}

function stateBadge(state) {
  const labels = { ACTIVE: '활성', SUSPENDED: '정지', WITHDRAWN: '탈퇴', PENDING: '대기', APPROVED: '승인', REJECTED: '거부' };
  const badge = document.createElement('span');
  badge.className = 'state-badge';
  badge.dataset.state = state;
  badge.textContent = labels[state] || state;
  return badge;
}

function actionButton(label, className) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = `action-button ${className}`;
  button.textContent = label;
  return button;
}

function textCell(value) {
  const target = document.createElement('td');
  target.textContent = value;
  return target;
}

function cell(content) {
  const target = document.createElement('td');
  target.append(content);
  return target;
}

function paragraph(value) {
  const target = document.createElement('p');
  target.textContent = value;
  return target;
}

function formatDate(value) {
  return value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value)) : '-';
}

function formatDateTime(value) {
  return value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-';
}

function startLoading() {
  document.querySelector('[data-admin-error]').hidden = true;
  document.querySelector('[data-admin-loading]').hidden = false;
}

function finishLoading() {
  document.querySelector('[data-admin-loading]').hidden = true;
}

function fail(message) {
  finishLoading();
  const error = document.querySelector('[data-admin-error]');
  error.textContent = message;
  error.hidden = false;
}

function redirectToLogin() {
  const redirect = encodeURIComponent(location.pathname + location.search);
  window.location.replace(`/login?redirect=${redirect}`);
  throw new Error('Redirecting to login');
}
