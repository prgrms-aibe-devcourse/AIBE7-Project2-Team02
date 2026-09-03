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

import {
    authFetch,
    readAccessToken,
    readApiBody,
    readCurrentUserId,
} from '../../account/js/auth-client.js';

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
            ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
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
    negotiation: null,
    quote: null,
    stompClient: null,
    subscription: null,
    wsConnected: false,
};

// ------------------------------------------------------------------
// 채팅방 목록
// ------------------------------------------------------------------

function partnerLabel(room) {
    const iAmBuyer = currentUserId === room.buyerId;
    const role = iAmBuyer ? '판매자' : '구매자';
    const id = iAmBuyer ? room.sellerId : room.buyerId;
    return `${role} #${id}`;
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

        await Promise.all([loadNegotiation(), loadQuote(room.quoteId)]);
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
        connectHeaders: { Authorization: `Bearer ${readAccessToken()}` },
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

// ------------------------------------------------------------------
// 1차 협상 견적 (QuoteNegotiation)
// ------------------------------------------------------------------

const negotiationStatusText = {
    NEGOTIATING: '협상 중',
    AI_SUMMARIZED: 'AI 요약 완료',
    LOCKED: '확정됨',
};

async function loadNegotiation() {
    try {
        state.negotiation = await api(`/chat-rooms/${state.selectedRoomId}/quote-negotiations`);
    } catch (e) {
        state.negotiation = e.status === 404 ? null : state.negotiation;
    }
    renderNegotiation();
}

function renderNegotiation() {
    const n = state.negotiation;
    const badge = el('negotiationStatusBadge');
    const form = el('negotiationForm');
    const empty = el('negotiationEmpty');

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
    badge.className = `chat-negotiation-status status-${n.status.toLowerCase()}`;

    el('negoQuantity').value = n.quantity ?? '';
    el('negoUnitPrice').value = n.unitPrice ?? '';
    el('negoDeliveryFee').value = n.deliveryFee ?? '';
    el('negoTotalAmount').textContent = won(n.totalAmount);

    const notesEl = el('negoAdditionalNotes');
    if (n.additionalNotes) {
        notesEl.hidden = false;
        notesEl.textContent = `AI 요약: ${n.additionalNotes}`;
    } else {
        notesEl.hidden = true;
    }

    const editable = n.status === 'NEGOTIATING' || n.status === 'AI_SUMMARIZED';
    ['negoQuantity', 'negoUnitPrice', 'negoDeliveryFee'].forEach((id) => {
        el(id).disabled = !editable;
    });

    el('negotiationSaveButton').hidden = !editable;
    el('negotiationAiButton').hidden = !(n.status === 'NEGOTIATING' && !n.aiSummaryUsed);
    el('negotiationLockButton').hidden = n.status !== 'AI_SUMMARIZED';

    const hint = el('negotiationHint');
    if (n.status === 'LOCKED') {
        hint.textContent = '협상이 확정되어 더 이상 수정할 수 없습니다. 아래 공식 견적서를 확인하세요.';
    } else if (n.status === 'AI_SUMMARIZED') {
        hint.textContent = 'AI 요약 이후 마지막 한 번만 수정할 수 있습니다.';
    } else {
        hint.textContent = '';
    }
}

async function startNegotiation() {
    try {
        state.negotiation = await api(`/chat-rooms/${state.selectedRoomId}/quote-negotiations`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
        renderNegotiation();
    } catch (e) {
        showToast(e.message || '협상 견적서를 시작하지 못했습니다.', true);
    }
}

async function saveNegotiation() {
    const body = JSON.stringify({
        quantity: Number(el('negoQuantity').value),
        unitPrice: Number(el('negoUnitPrice').value),
        deliveryFee: el('negoDeliveryFee').value ? Number(el('negoDeliveryFee').value) : 0,
    });

    const path = state.negotiation.status === 'AI_SUMMARIZED'
        ? `/chat-rooms/${state.selectedRoomId}/quote-negotiations/final`
        : `/chat-rooms/${state.selectedRoomId}/quote-negotiations`;

    try {
        state.negotiation = await api(path, { method: 'PUT', body });
        renderNegotiation();
        showToast('협상 견적서를 저장했습니다.');
    } catch (e) {
        showToast(e.message || '저장에 실패했습니다.', true);
    }
}

async function runAiSummary() {
    try {
        state.negotiation = await api(`/chat-rooms/${state.selectedRoomId}/quote-negotiations/ai-summary`, {
            method: 'POST',
        });
        renderNegotiation();
        showToast('AI 요약을 반영했습니다.');
    } catch (e) {
        showToast(e.message || 'AI 요약에 실패했습니다.', true);
    }
}

async function lockNegotiation() {
    if (!window.confirm('협상을 최종 확정하면 더 이상 수정할 수 없습니다. 계속할까요?')) return;
    try {
        state.negotiation = await api(`/chat-rooms/${state.selectedRoomId}/quote-negotiations/lock`, {
            method: 'POST',
        });
        renderNegotiation();
        showToast('협상을 확정하고 공식 견적서를 발행했습니다.');

        const room = await api(`/chat-rooms/${state.selectedRoomId}`);
        state.selectedRoom = room;
        await loadQuote(room.quoteId);
    } catch (e) {
        showToast(e.message || '확정에 실패했습니다.', true);
    }
}

// ------------------------------------------------------------------
// 공식 견적서 (Quote)
// ------------------------------------------------------------------

const quoteStatusText = {
    SENT: '발송됨',
    ACCEPTED: '수락됨',
    REJECTED: '거절됨',
    WITHDRAWN: '철회됨',
};

async function loadQuote(quoteId) {
    if (!quoteId) {
        state.quote = null;
        renderQuote();
        return;
    }
    try {
        state.quote = await api(`/quotes/${quoteId}`);
    } catch (e) {
        state.quote = null;
    }
    renderQuote();
}

function renderQuote() {
    const q = state.quote;
    const empty = el('quoteEmpty');
    const detail = el('quoteDetail');
    const badge = el('quoteStatusBadge');
    const composeForm = el('quoteComposeForm');

    if (!q) {
        empty.hidden = false;
        detail.hidden = true;
        badge.hidden = true;
        composeForm.hidden = true;
        return;
    }

    empty.hidden = true;
    composeForm.hidden = true;
    detail.hidden = false;
    badge.hidden = false;
    badge.textContent = quoteStatusText[q.status] || q.status;
    badge.className = `chat-quote-status-badge status-${q.status.toLowerCase()}`;

    el('quoteDetailQuantity').textContent = `${q.quantity}명`;
    el('quoteDetailUnitPrice').textContent = won(q.unitPrice);
    el('quoteDetailDeliveryFee').textContent = won(q.deliveryFee);
    el('quoteDetailTotal').textContent = won(q.totalAmount);

    renderQuoteActions(q);
}

function renderQuoteActions(q) {
    const area = el('quoteActionArea');
    area.innerHTML = '';

    const senderId = q.senderRole === 'BUYER' ? q.buyerId : q.sellerId;
    const isMine = senderId === currentUserId;

    if (q.status === 'SENT') {
        if (isMine) {
            area.appendChild(makeButton('제안 철회', 'chat-btn-danger', () => updateQuoteStatus('WITHDRAWN')));
        } else {
            area.appendChild(makeButton('수락', 'chat-btn-primary', () => updateQuoteStatus('ACCEPTED')));
            area.appendChild(makeButton('거절', 'chat-btn-danger', () => updateQuoteStatus('REJECTED')));
        }
        return;
    }

    if (q.status === 'ACCEPTED') {
        if (currentUserId === q.buyerId) {
            area.appendChild(makeButton('결제하기', 'chat-btn-primary', payForQuote));
        } else {
            const note = document.createElement('p');
            note.className = 'chat-quote-hint';
            note.textContent = '구매자의 결제를 기다리는 중입니다.';
            area.appendChild(note);
        }
        return;
    }

    const note = document.createElement('p');
    note.className = 'chat-quote-hint';
    note.textContent = q.status === 'REJECTED' ? '견적이 거절되어 대화가 종료되었습니다.' : '견적이 철회되어 대화가 종료되었습니다.';
    area.appendChild(note);
}

function makeButton(label, className, handler) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `chat-btn ${className}`;
    button.textContent = label;
    button.addEventListener('click', handler);
    return button;
}

async function updateQuoteStatus(status) {
    try {
        state.quote = await api(`/quotes/${state.quote.quoteId}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status }),
        });
        renderQuote();
        showToast('견적 상태를 변경했습니다.');

        if (status === 'REJECTED' || status === 'WITHDRAWN') {
            const room = await api(`/chat-rooms/${state.selectedRoomId}`);
            state.selectedRoom = room;
            renderRoomHeader(room);
        }
    } catch (e) {
        showToast(e.message || '상태 변경에 실패했습니다.', true);
    }
}

async function payForQuote() {
    try {
        await api(`/quotes/${state.quote.quoteId}/payments`, { method: 'POST' });
        showToast('결제를 완료했습니다.');
    } catch (e) {
        showToast(e.message || '결제에 실패했습니다.', true);
    }
}

async function sendNewQuote(event) {
    event.preventDefault();
    const body = JSON.stringify({
        quantity: Number(el('quoteQuantity').value),
        unitPrice: Number(el('quoteUnitPrice').value),
        deliveryFee: el('quoteDeliveryFee').value ? Number(el('quoteDeliveryFee').value) : 0,
    });

    try {
        const quote = await api(`/quotes/chat-rooms/${state.selectedRoomId}`, { method: 'POST', body });
        state.quote = quote;
        if (state.selectedRoom) state.selectedRoom.quoteId = quote.quoteId;
        renderQuote();
        showToast('견적서를 보냈습니다.');
    } catch (e) {
        showToast(e.message || '견적서 발송에 실패했습니다.', true);
    }
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

    el('negotiationStartButton').addEventListener('click', startNegotiation);
    el('negotiationForm').addEventListener('submit', (event) => {
        event.preventDefault();
        saveNegotiation();
    });
    el('negotiationAiButton').addEventListener('click', runAiSummary);
    el('negotiationLockButton').addEventListener('click', lockNegotiation);

    el('quoteComposeToggle').addEventListener('click', () => {
        el('quoteEmpty').hidden = true;
        el('quoteComposeForm').hidden = false;
    });
    el('quoteComposeCancel').addEventListener('click', () => {
        el('quoteComposeForm').hidden = true;
        el('quoteEmpty').hidden = false;
    });
    el('quoteComposeForm').addEventListener('submit', sendNewQuote);

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
    el('newChatForm').addEventListener('submit', createNewChatRoom);
}

// ------------------------------------------------------------------
// 새 채팅방 개설 (테스트/초기 진입용)
// ------------------------------------------------------------------

async function createNewChatRoom(event) {
    event.preventDefault();

    const originType = el('newChatOriginType').value;
    const body = {
        sellerId: Number(el('newChatSellerId').value),
        originType,
        proposalId: originType === 'PROPOSAL' && el('newChatProposalId').value
            ? Number(el('newChatProposalId').value)
            : null,
    };

    try {
        const room = await api('/chat-rooms', { method: 'POST', body: JSON.stringify(body) });
        el('newChatForm').hidden = true;
        el('newChatForm').reset();
        el('newChatProposalIdField').hidden = true;
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
