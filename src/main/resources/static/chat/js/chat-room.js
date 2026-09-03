/*
 * 배치 경로: src/main/resources/static/chat/js/chat-room.js
 *
 * 왼쪽 채팅방 목록 + 오른쪽 채팅방(메시지 / 1차 협상 견적 / 공식 견적서)을 담당한다.
 *
 * 연동하는 API (chat_v8 / quote_v9 기준)
 *  - GET    /api/v1/chat-rooms                              내 채팅방 목록
 *  - GET    /api/v1/chat-rooms/{id}                         채팅방 상세
 *  - GET    /api/v1/chat-rooms/{id}/messages                이전 메시지
 *  - POST   /api/v1/chat-rooms/{id}/files                   파일 업로드 (multipart)
 *  - WS pub /pub/chat/message, sub /sub/chat/room/{id}      실시간 메시지
 *  - GET    /api/v1/chat-files/{fileId}/download            파일 다운로드
 *
 *  - POST/GET/PUT      /api/v1/chat-rooms/{id}/quote-negotiations           1차 협상 견적
 *  - POST               .../quote-negotiations/ai-summary                  AI 요약 (1회)
 *  - PUT                .../quote-negotiations/final                       AI 요약 후 마지막 수정
 *  - POST                .../quote-negotiations/lock                        최종 잠금 -> 공식 견적서 자동 발행
 *
 *  - POST   /api/v1/quotes/chat-rooms/{id}                  채팅방 안에서 공식 견적서 발송
 *  - GET    /api/v1/quotes/{quoteId}                        공식 견적서 조회
 *  - PUT    /api/v1/quotes/{quoteId}                        공식 견적서 수정 (보낸 사람, SENT 상태만)
 *  - PATCH  /api/v1/quotes/{quoteId}/status                 수락/거절/철회
 *  - POST   /api/v1/quotes/{quoteId}/payments                결제
 *
 * 인증/토큰은 팀 공통 모듈(account/js/auth-client.js)을 그대로 쓴다.
 */

import {authFetch, readAccessToken, readApiBody, readCurrentUserId,} from '../../account/js/auth-client.js';

const CONFIG = {
    API_BASE: '/api/v1',
    WS_ENDPOINT: '/ws-stomp',
};

// ------------------------------------------------------------------
// 인증
// ------------------------------------------------------------------

function redirectToLogin() {
    const redirect = encodeURIComponent(location.pathname + location.search);
    window.location.replace(`/login?redirect=${redirect}`);
    throw new Error('Redirecting to login');
}

if (!readAccessToken()) redirectToLogin();

const currentUserId = readCurrentUserId();
if (!currentUserId) redirectToLogin();

// ------------------------------------------------------------------
// 공통 fetch 래퍼 (auth-client.js의 authFetch 위에서 JSON/에러 처리만 얹는다)
// ------------------------------------------------------------------

async function api(path, options = {}) {
    const isFormData = options.body instanceof FormData;

    const response = await authFetch(CONFIG.API_BASE + path, {
        ...options,
        headers: {
            ...(isFormData ? {} : {'Content-Type': 'application/json'}),
            ...(options.headers || {}),
        },
    });

    if (response.status === 401) redirectToLogin();
    if (response.status === 204) return null;

    const data = await readApiBody(response);

    if (!response.ok) {
        const message = data && data.message ? data.message : `요청을 처리하지 못했습니다. (${response.status})`;
        const error = new Error(message);
        error.status = response.status;
        error.body = data;
        throw error;
    }

    return data;
}

async function authenticatedBlob(url) {
    const response = await authFetch(url);
    if (response.status === 401) redirectToLogin();
    if (!response.ok) throw new Error('파일을 불러오지 못했습니다.');
    return response.blob();
}

// ------------------------------------------------------------------
// 유틸
// ------------------------------------------------------------------

// "1인당 예산 * 수량"이 기본 규칙. TOTAL을 고르면 총액을 수량으로 나눠서 1인 단가로 환산.
function computeUnitPriceFromBudget(budget, budgetType, quantity) {
    if (!budget || !quantity) return null;
    if (budgetType === 'TOTAL') {
        return Math.round(budget / quantity);
    }
    return Math.round(budget);
}

function updateComposeTotalPreview() {
    const quantity = Number(el('quoteQuantity').value) || null;
    const budget = Number(el('quoteBudget').value) || null;
    const budgetType = el('quoteBudgetType').value;
    const unitPrice = computeUnitPriceFromBudget(budget, budgetType, quantity);
    const total = (unitPrice != null && quantity != null) ? unitPrice * quantity : null;
    el('quoteComposeTotalAmount').textContent = total != null ? won(total) : '0원';
}

const won = (n) => `${Number(n ?? 0).toLocaleString('ko-KR')}원`;

function formatDateTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleString('ko-KR', {
        month: 'numeric',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
}

function el(id) {
    return document.getElementById(id);
}

let toastTimer = null;

function showToast(message, isError = false) {
    const toast = el('chatToast');
    toast.textContent = message;
    toast.classList.toggle('is-error', isError);
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.hidden = true;
    }, 3200);
}

// ------------------------------------------------------------------
// 상태
// ------------------------------------------------------------------

const state = {
    rooms: [],
    selectedRoomId: null,
    selectedRoom: null,
    sellerProducts: [],   // 추가: 이 채팅방 판매자의 상품 목록
    selectedProductId: null, // 추가
    negotiation: null,    // 추가: QuoteNegotiation
    quote: null,          // LOCKED 이후에만 채워짐 (공식 Quote)
    stompClient: null,
    subscription: null,
    wsConnected: false,
};

// ------------------------------------------------------------------
// 채팅방 목록
// ------------------------------------------------------------------

function partnerLabel(room) {
    const iAmBuyer =
        currentUserId === room.buyerId;

    if (iAmBuyer) {
        return room.sellerName
            || `판매자 #${room.sellerId}`;
    }

    return room.buyerName
        || `구매자 #${room.buyerId}`;
}

function originLabel(originType) {
    return originType === 'PROPOSAL' ? '제안 대화' : '문의 대화';
}

async function loadRoomList() {
    el('chatListLoading').hidden = false;
    el('chatListError').hidden = true;
    el('chatListEmpty').hidden = true;

    try {
        const rooms = await api('/chat-rooms');
        state.rooms = rooms || [];
        renderRoomList();

        const params = new URLSearchParams(window.location.search);
        const requestedId = Number(params.get('roomId'));
        const targetId = requestedId && state.rooms.some((r) => r.chatRoomId === requestedId)
            ? requestedId
            : (state.rooms[0] ? state.rooms[0].chatRoomId : null);

        if (targetId) {
            selectRoom(targetId);
        }
    } catch (e) {
        el('chatListError').hidden = false;
        el('chatListError').textContent = e.message || '채팅방 목록을 불러오지 못했습니다.';
    } finally {
        el('chatListLoading').hidden = true;
    }
}

function renderRoomList() {
    const container = el('chatRoomList');

    container.querySelectorAll('.chat-room-item').forEach((node) => node.remove());

    if (state.rooms.length === 0) {
        el('chatListEmpty').hidden = false;
        return;
    }
    el('chatListEmpty').hidden = true;

    state.rooms
        .slice()
        .sort((a, b) => new Date(b.lastMessageAt || b.createdAt) - new Date(a.lastMessageAt || a.createdAt))
        .forEach((room) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'chat-room-item';
            button.classList.toggle('is-active', room.chatRoomId === state.selectedRoomId);
            button.dataset.roomId = String(room.chatRoomId);

            const top = document.createElement('div');
            top.className = 'chat-room-item-top';

            const name = document.createElement('span');
            name.className = 'chat-room-item-name';
            name.textContent = partnerLabel(room);

            const chip = document.createElement('span');
            chip.className = 'chat-room-item-chip';
            chip.classList.toggle('is-closed', room.status === 'CLOSED');
            chip.textContent = room.status === 'CLOSED' ? '종료됨' : '진행 중';

            top.append(name, chip);

            const sub = document.createElement('span');
            sub.className = 'chat-room-item-sub';
            sub.textContent = room.lastMessage || originLabel(room.originType);

            button.append(top, sub);
            button.addEventListener('click', () => selectRoom(room.chatRoomId));

            container.appendChild(button);
        });
}

// ------------------------------------------------------------------
// 채팅방 선택
// ------------------------------------------------------------------

async function selectRoom(roomId) {
    state.selectedRoomId = roomId;

    const url = new URL(window.location.href);
    url.searchParams.set('roomId', roomId);
    window.history.replaceState({}, '', url);

    document.querySelectorAll('.chat-room-item').forEach((node) => {
        node.classList.toggle('is-active', Number(node.dataset.roomId) === roomId);
    });

    el('chatRoomEmpty').hidden = true;
    el('chatRoomActive').hidden = false;
    el('chatMessageList').innerHTML = '<p class="chat-state-line">불러오는 중...</p>';

    try {
        const room = await api(`/chat-rooms/${roomId}`);
        state.selectedRoom = room;
        renderRoomHeader(room);

        const messages = await api(`/chat-rooms/${roomId}/messages`);
        renderMessages(messages || []);

        subscribeRoom(roomId);

        // [교체] 상품 목록 + 협상 견적서를 함께 로딩
        // 기존: await loadSellerProducts(room.sellerId);
        await loadSellerProducts(room.chatRoomId);
        await loadNegotiation(roomId);
    } catch (e) {
        showToast(e.message || '채팅방을 불러오지 못했습니다.', true);
    }
}

function renderRoomHeader(room) {
    el('chatPartnerLabel').textContent = partnerLabel(room);
    el('chatRoomMeta').textContent = `${originLabel(room.originType)} · 시작일 ${formatDateTime(room.createdAt)}`;

    const statusEl = el('chatRoomStatus');
    statusEl.textContent = room.status === 'CLOSED' ? '종료됨' : '진행 중';
    statusEl.classList.toggle('is-closed', room.status === 'CLOSED');
    const reportButton = el('chatReportButton');
    reportButton.href = `/mypage/reports?${new URLSearchParams({
        targetType: 'CHAT_ROOM',
        targetId: room.chatRoomId,
    })}`;
    reportButton.hidden = false;
}

// ------------------------------------------------------------------
// 메시지
// ------------------------------------------------------------------

function renderMessages(messages) {
    const list = el('chatMessageList');
    list.innerHTML = '';
    messages.forEach((m) => appendMessage(m, false));
    scrollMessagesToBottom();
}

function scrollMessagesToBottom() {
    const list = el('chatMessageList');
    list.scrollTop = list.scrollHeight;
}

function appendMessage(message, scrollAfter = true) {
    if (message.chatRoomId !== state.selectedRoomId) return;

    const row = document.createElement('div');
    row.className = 'chat-bubble-row';
    row.classList.toggle('is-mine', message.senderId === currentUserId);

    const bubble = document.createElement('div');
    bubble.className = 'chat-bubble';

    if (message.messageType === 'TEXT') {
        const text = document.createElement('span');
        text.textContent = message.message;
        bubble.appendChild(text);
    } else {
        bubble.appendChild(buildFileBubble(message));
    }

    const time = document.createElement('span');
    time.className = 'chat-bubble-time';
    time.textContent = formatDateTime(message.createdAt);
    bubble.appendChild(time);

    row.appendChild(bubble);
    el('chatMessageList').appendChild(row);

    if (scrollAfter) scrollMessagesToBottom();
}

function buildFileBubble(message) {
    const wrap = document.createElement('div');
    wrap.className = 'chat-bubble-file';

    if (message.messageType === 'IMAGE') {
        const img = document.createElement('img');
        img.alt = message.originalFileName || '첨부 이미지';
        img.loading = 'lazy';
        authenticatedBlob(message.message)
            .then((blob) => {
                img.src = URL.createObjectURL(blob);
            })
            .catch(() => {
                img.replaceWith(document.createTextNode('이미지를 불러오지 못했습니다.'));
            });
        img.addEventListener('click', () => window.open(img.src, '_blank'));
        wrap.appendChild(img);
        return wrap;
    }

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'chat-bubble-file-link';
    button.textContent = `📄 ${message.originalFileName || '첨부파일'}`;
    button.addEventListener('click', async () => {
        try {
            const blob = await authenticatedBlob(message.message);
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = message.originalFileName || 'download';
            link.click();
        } catch (e) {
            showToast('파일을 다운로드하지 못했습니다.', true);
        }
    });
    wrap.appendChild(button);
    return wrap;
}

async function sendTextMessage(text) {
    if (!state.stompClient || !state.stompClient.connected) {
        showToast('연결 중입니다. 잠시 후 다시 시도해주세요.', true);
        return;
    }
    state.stompClient.publish({
        destination: '/pub/chat/message',
        body: JSON.stringify({
            chatRoomId: state.selectedRoomId,
            messageType: 'TEXT',
            message: text,
        }),
    });
}

async function uploadAndSendFile(file) {
    const statusEl = el('chatUploadStatus');
    statusEl.hidden = false;
    statusEl.textContent = `${file.name} 업로드 중...`;

    try {
        const formData = new FormData();
        formData.append('file', file);

        const uploaded = await api(`/chat-rooms/${state.selectedRoomId}/files`, {
            method: 'POST',
            body: formData,
        });

        if (state.stompClient && state.stompClient.connected) {
            state.stompClient.publish({
                destination: '/pub/chat/message',
                body: JSON.stringify({
                    chatRoomId: state.selectedRoomId,
                    messageType: uploaded.fileType,
                    message: uploaded.originalFileName,
                    fileId: uploaded.id,
                    originalFileName: uploaded.originalFileName,
                    fileSize: uploaded.fileSize,
                }),
            });
        }
        statusEl.hidden = true;
    } catch (e) {
        statusEl.textContent = e.message || '파일 업로드에 실패했습니다.';
    }
}

// ------------------------------------------------------------------
// 실시간 연결 (STOMP over SockJS)
// ------------------------------------------------------------------

function connectWebSocket() {
    const socket = new SockJS(CONFIG.WS_ENDPOINT);
    const client = new StompJs.Client({
        webSocketFactory: () => socket,
        connectHeaders: {Authorization: `Bearer ${readAccessToken()}`},
        reconnectDelay: 4000,
        onConnect: () => {
            state.wsConnected = true;
            if (state.selectedRoomId) subscribeRoom(state.selectedRoomId);
        },
        onWebSocketClose: () => {
            state.wsConnected = false;
        },
        onStompError: (frame) => {
            console.error('STOMP 오류', frame.headers, frame.body);
        },
    });
    client.activate();
    state.stompClient = client;
}

function subscribeRoom(roomId) {
    if (!state.stompClient || !state.stompClient.connected) return;
    if (state.subscription) {
        state.subscription.unsubscribe();
        state.subscription = null;
    }
    state.subscription = state.stompClient.subscribe(`/sub/chat/room/${roomId}`, (frame) => {
        const message = JSON.parse(frame.body);
        appendMessage(message, true);
    });
}

//
//
//

async function loadSellerProducts(chatRoomId) {
    try {
        const sellerAccountId = await api(`/chat-rooms/${chatRoomId}/seller-account-id`);
        const products = await api(`/products/search?ownerAccountId=${sellerAccountId}`);
        state.sellerProducts = products || [];
    } catch (e) {
        state.sellerProducts = [];
    }
    populateProductSelect();
}

function populateProductSelect() {
    const select = el('quoteProductId');
    select.innerHTML = '<option value="">상품 선택 안 함</option>';
    state.sellerProducts.forEach((p) => {
        const option = document.createElement('option');
        option.value = String(p.id);
        option.textContent = p.productName;
        select.appendChild(option);
    });

    const currentProductId = state.selectedRoom?.productId ?? null;
    select.value = currentProductId ? String(currentProductId) : '';
    updateProductNameDisplay(currentProductId);
}

function updateBudgetLabel() {
    const type = el('quoteBudgetType').value;
    el('quoteBudgetLabel').textContent =
        type === 'TOTAL' ? '총 예산' : type === 'PER_PERSON' ? '1인당 예산' : '예산';
}

function updateProductNameDisplay(productId) {
    const product = state.sellerProducts.find((p) => p.id === Number(productId));

    const quoteItemNameEl = el('quoteItemName');
    quoteItemNameEl.value = product ? product.productName : '';
    quoteItemNameEl.readOnly = true; // readonly 속성 적용
}

async function onProductSelectChange(event) {
    const newProductId = event.target.value ? Number(event.target.value) : null;
    updateProductNameDisplay(newProductId);
    if (!newProductId) return;

    try {
        const updated = await api(`/chat-rooms/${state.selectedRoomId}/product`, {
            method: 'PATCH',
            body: JSON.stringify({productId: newProductId}),
        });
        state.selectedRoom = updated;
        showToast('연결된 상품을 변경했습니다.');
    } catch (e) {
        showToast(e.message || '상품 변경에 실패했습니다.', true);
    }
}

// ------------------------------------------------------------------
// 공식 견적서 (Quote)
// ------------------------------------------------------------------

async function loadNegotiation(chatRoomId) {
    try {
        state.negotiation = await api(`/chat-rooms/${chatRoomId}/quote-negotiations`);
    } catch (e) {
        state.negotiation = null; // 아직 생성 안 됨
    }

    if (state.negotiation?.status === 'LOCKED' && state.negotiation.resultingQuoteId) {
        try {
            state.quote = await api(`/quotes/${state.negotiation.resultingQuoteId}`);
        } catch (e) {
            state.quote = null;
        }
    } else {
        state.quote = null;
    }

    renderQuoteCard();
}

async function createNegotiation() {
    try {
        state.negotiation = await api(`/chat-rooms/${state.selectedRoomId}/quote-negotiations`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
        showToast('1차 견적서를 생성했습니다.');
        renderQuoteCard();
    } catch (e) {
        showToast(e.message || '1차 견적서 생성에 실패했습니다.', true);
    }
}

function fillFormFromNegotiation(n) {
    el('quoteQuantity').value = n.quantity ?? '';
    el('quoteComposeTotalAmount').textContent = n.totalAmount != null ? won(n.totalAmount) : '0원';

    const meta = decodeNotes(n.additionalNotes);
    el('quoteEventDateTime').value = meta.eventDateTime || '';
    el('quoteBudgetType').value = meta.budgetType || '';
    el('quoteBudget').value = meta.budget || '';
    el('quoteDeliveryAddress').value = meta.deliveryAddress || '';
    el('quoteDescription').value = meta.description || '';
    updateBudgetLabel();
    updateComposeTotalPreview();

    const notesEl = el('quoteDetailNotes');
    if (meta.summary) {
        notesEl.hidden = false;
        notesEl.textContent = `AI 요약: ${meta.summary}`;
    } else {
        notesEl.hidden = true;
    }
}

const negotiationStatusText = {
    NEGOTIATING: '협상 중',
    AI_SUMMARIZED: 'AI 요약 완료',
    LOCKED: '최종 확정',
};

function renderQuoteCard() {
    const n = state.negotiation;
    const empty = el('quoteEmpty');
    const form = el('quoteComposeForm');
    const badge = el('quoteStatusBadge');

    if (!n) {
        empty.hidden = false;
        form.hidden = true;
        badge.hidden = true;
        return;
    }

    empty.hidden = true;
    form.hidden = false;
    badge.hidden = false;
    badge.textContent = negotiationStatusText[n.status] || n.status;
    badge.className = `chat-quote-status-badge status-${n.status.toLowerCase()}`;

    fillFormFromNegotiation(n);

    const readOnly = n.status === 'LOCKED';
    ['quoteQuantity', 'quoteEventDateTime', 'quoteBudgetType',
        'quoteBudget', 'quoteDeliveryAddress', 'quoteDescription', 'quoteProductId']
        .forEach((id) => {
            el(id).disabled = readOnly;
        });

    renderQuoteActions(n);
}

function collectNegotiationEditPayload() {
    const quantity = Number(el('quoteQuantity').value) || null;
    const budgetType = el('quoteBudgetType').value;
    const budget = Number(el('quoteBudget').value) || null;
    const eventDateTime = el('quoteEventDateTime').value;
    const deliveryAddress = el('quoteDeliveryAddress').value.trim();
    const description = el('quoteDescription').value.trim();

    const unitPrice = computeUnitPriceFromBudget(budget, budgetType, quantity);
    const existingSummary = decodeNotes(state.negotiation?.additionalNotes).summary;

    return {
        quantity,
        unitPrice,
        deliveryFee: state.negotiation?.deliveryFee ?? null,
        additionalNotes: encodeNotes({ eventDateTime, budgetType, budget, deliveryAddress, description }, existingSummary),
    };
}

function renderQuoteActions(n) {
    const area = el('quoteActionArea');
    area.innerHTML = '';

    if (n.status === 'NEGOTIATING' || n.status === 'AI_SUMMARIZED') {
        area.appendChild(makeButton('수정하기', 'chat-btn-ghost', saveNegotiationEdit));
        area.appendChild(makeButton('최종결정', 'chat-btn-primary', finalizeNegotiation));
        area.appendChild(makeButton('AI 요약', 'chat-btn-ghost', runAiSummary, n.aiSummaryUsed));
        return;
    }

    if (n.status === 'LOCKED') {
        if (state.quote && currentUserId === state.quote.buyerId) {
            area.appendChild(makeButton('결제하기', 'chat-btn-primary', payForQuote));
        }
        area.appendChild(makeButton('철회하기', 'chat-btn-danger', withdrawQuote));
    }
}

function makeButton(label, className, handler, disabled = false) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `chat-btn ${className}`;
    button.textContent = label;
    button.disabled = disabled;
    button.addEventListener('click', handler);
    return button;
}

async function saveNegotiationEdit() {
    const path = state.negotiation.status === 'AI_SUMMARIZED'
        ? `/chat-rooms/${state.selectedRoomId}/quote-negotiations/final`
        : `/chat-rooms/${state.selectedRoomId}/quote-negotiations`;

    try {
        state.negotiation = await api(path, {method: 'PUT', body: JSON.stringify(collectNegotiationEditPayload())});
        renderQuoteCard();
        showToast('견적을 수정했습니다.');
    } catch (e) {
        showToast(e.message || '수정에 실패했습니다.', true);
    }
}

async function runAiSummary() {
    if (!confirm('AI 요약은 채팅방당 1회만 가능하고, 실제 AI 호출 비용이 발생합니다. 계속할까요?')) return;
    try {
        state.negotiation = await api(`/chat-rooms/${state.selectedRoomId}/quote-negotiations/ai-summary`, {method: 'POST'});
        renderQuoteCard();
        showToast('AI 요약이 완료됐습니다. 필요하면 마지막으로 한 번 더 수정할 수 있어요.');
    } catch (e) {
        showToast(e.message || 'AI 요약에 실패했습니다.', true);
    }
}

async function finalizeNegotiation() {
    if (!confirm('최종결정하면 더 이상 수정할 수 없습니다. 계속할까요?')) return;
    try {
        state.negotiation = await api(`/chat-rooms/${state.selectedRoomId}/quote-negotiations/lock`, {method: 'POST'});
        if (state.negotiation.resultingQuoteId) {
            state.quote = await api(`/quotes/${state.negotiation.resultingQuoteId}`);
        }
        renderQuoteCard();
        showToast('최종 확정되었습니다.');
    } catch (e) {
        showToast(e.message || '최종결정에 실패했습니다.', true);
    }
}

async function payForQuote() {
    try {
        await api(`/quotes/${state.quote.quoteId}/payments`, {method: 'POST'});
        showToast('결제를 완료했습니다.');
    } catch (e) {
        showToast(e.message || '결제에 실패했습니다.', true);
    }
}

// [주의] 백엔드가 ACCEPTED 이후 WITHDRAWN 전이를 아직 지원하지 않습니다 (409 예상).
// 지원 여부를 정하기 전까지는 에러 토스트만 뜰 겁니다.
async function withdrawQuote() {
    try {
        await api(`/quotes/${state.quote.quoteId}/status`, {
            method: 'PATCH',
            body: JSON.stringify({status: 'WITHDRAWN'}),
        });
        showToast('견적을 철회했습니다.');
        await loadNegotiation(state.selectedRoomId);
    } catch (e) {
        showToast(e.message || '철회에 실패했습니다. (백엔드 미지원일 수 있음)', true);
    }
}

// [추가] additionalNotes 패킹 포맷 — 백엔드 QuoteNegotiationNotesCodec와 반드시 1:1 동일해야 함.
// QuoteNegotiation에 전용 컬럼이 생기면(estimate 필드 마이그레이션) 이 두 함수는 통째로 삭제 대상.
const META_OPEN = '[MATCHEAT_META]';
const META_CLOSE = '[/MATCHEAT_META]';
const SUMMARY_OPEN = '[AI_SUMMARY]';
const SUMMARY_CLOSE = '[/AI_SUMMARY]';

function encodeNotes({ eventDateTime, budgetType, budget, deliveryAddress, description }, existingSummary) {
    const lines = [];
    const push = (key, value) => {
        if (value === undefined || value === null || value === '') return;
        lines.push(`${key}=${String(value).replace(/\n/g, '\\n')}`);
    };
    push('eventDateTime', eventDateTime);
    push('budgetType', budgetType);
    push('budget', budget);
    push('deliveryAddress', deliveryAddress);
    push('description', description);

    return [META_OPEN, ...lines, META_CLOSE, SUMMARY_OPEN, existingSummary || '', SUMMARY_CLOSE].join('\n');
}

function decodeNotes(raw) {
    const empty = { eventDateTime: '', budgetType: '', budget: '', deliveryAddress: '', description: '', summary: '' };
    if (!raw) return empty;

    const metaMatch = raw.match(/\[MATCHEAT_META\]\n([\s\S]*?)\n\[\/MATCHEAT_META\]/);
    const summaryMatch = raw.match(/\[AI_SUMMARY\]\n([\s\S]*?)\n\[\/AI_SUMMARY\]/);

    if (!metaMatch && !summaryMatch) {
        // 이전 포맷(순수 자유 텍스트) 호환 — 통째로 요약칸에 보여준다.
        return { ...empty, summary: raw };
    }

    const result = { ...empty };
    if (metaMatch) {
        metaMatch[1].split('\n').filter(Boolean).forEach((line) => {
            const idx = line.indexOf('=');
            if (idx === -1) return;
            const key = line.slice(0, idx);
            const value = line.slice(idx + 1).replace(/\\n/g, '\n');
            if (key in result) result[key] = value;
        });
    }
    if (summaryMatch) result.summary = summaryMatch[1];
    return result;
}

// ------------------------------------------------------------------
// 이벤트 바인딩
// ------------------------------------------------------------------

function bindEvents() {
    el('chatMessageForm').addEventListener('submit', (event) => {
        event.preventDefault();
        const input = el('chatMessageInput');
        const text = input.value.trim();
        if (!text) return;
        sendTextMessage(text);
        input.value = '';
    });

    el('chatFileInput').addEventListener('change', (event) => {
        const file = event.target.files[0];
        if (file) uploadAndSendFile(file);
        event.target.value = '';
    });

    ['quoteQuantity', 'quoteBudget', 'quoteBudgetType'].forEach((id) => {
        el(id).addEventListener('input', updateComposeTotalPreview);
        el(id).addEventListener('change', updateComposeTotalPreview);
    });
    el('quoteBudgetType').addEventListener('change', updateBudgetLabel);
    el('quoteProductId').addEventListener('change', onProductSelectChange);
    el('quoteComposeToggle').addEventListener('click', createNegotiation);
    el('quoteComposeForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!state.negotiation) return;
        await saveNegotiationEdit();
    });

    el('newChatToggle').addEventListener('click', () => {
        el('newChatForm').hidden = !el('newChatForm').hidden;
    });
    el('newChatCancel').addEventListener('click', () => {
        el('newChatForm').hidden = true;
        el('newChatForm').reset();
    });
    el('newChatOriginType').addEventListener('change', (event) => {
        el('newChatProposalIdField').hidden = event.target.value !== 'PROPOSAL';
    });
    el('newChatMode').addEventListener('change', (event) => {
        const isProduct = event.target.value === 'PRODUCT_ID';
        el('newChatSellerIdField').hidden = isProduct;
        el('newChatProductIdField').hidden = !isProduct;
    });
    el('newChatForm').addEventListener('submit', createNewChatRoom);
}

// ------------------------------------------------------------------
// 새 채팅방 개설 (테스트/초기 진입용)
// ------------------------------------------------------------------

async function createNewChatRoom(event) {
    event.preventDefault();

    const mode = el('newChatMode').value;
    const originType = el('newChatOriginType').value;
    const productIdValue = el('newChatProductId').value;

    const body = {
        originType,
        proposalId: originType === 'PROPOSAL' && el('newChatProposalId').value
            ? Number(el('newChatProposalId').value)
            : null,
    };

    // 2.1: 상품 ID로 "바로 채팅하기" — 서버가 상품 등록자를 판매자로 자동 지정한다.
    if (mode === 'PRODUCT_ID') {
        body.productId = Number(productIdValue);
    } else {
        body.sellerId = Number(el('newChatSellerId').value);
    }

    try {
        const room = await api('/chat-rooms', { method: 'POST', body: JSON.stringify(body) });

        // 이 방이 어떤 상품에서 시작됐는지 캐싱해서, 2.2 미리보기가 새로고침 후에도 뜨게 한다.

        el('newChatForm').hidden = true;
        el('newChatForm').reset();
        el('newChatProposalIdField').hidden = true;
        el('newChatProductIdField').hidden = true;
        showToast('채팅방을 개설했습니다.');

        await loadRoomList();
        selectRoom(room.chatRoomId);
    } catch (e) {
        showToast(e.message || '채팅방 개설에 실패했습니다.', true);
    }
}

// ------------------------------------------------------------------
// 시작
// ------------------------------------------------------------------

bindEvents();
connectWebSocket();
loadRoomList();
