import {authFetch, readApiBody, readCurrentUserId} from '/account/js/auth-client.js';

const page =
    document.getElementById(
        'orderDetailPage'
    );

const requestId =
    page?.dataset.requestId;

const currentUserId =
    readCurrentUserId();

if (currentUserId === null) {
    redirectToLogin();
}

const response =
    await authFetch(
        `/api/v1/requests/${requestId}`
    );

if (response.status === 401) {
    redirectToLogin();
}

if (!response.ok) {
    const body =
        await readApiBody(
            response
        );

    showError(
        body?.message
        || '주문 정보를 불러올 수 없습니다.'
    );
} else {
    const order =
        await response.json();

    renderOrder(order);

    await configureActions(
        order
    );
}

/**
 * 조회한 주문 정보를 상세 화면에 표시한다.
 */
function renderOrder(order) {
    document.getElementById(
        'orderLoading'
    ).hidden = true;

    document.getElementById(
        'orderContent'
    ).hidden = false;

    const status =
        document.getElementById(
            'orderStatus'
        );

    status.textContent =
        statusLabel(order.status);

    status.dataset.status =
        order.status || '';

    setField(
        'title',
        order.title
        || '제목 없음'
    );

    setField(
        'eventDateTime',
        formatDateTime(
            order.eventDateTime
        )
    );

    setField(
        'quantity',
        order.quantity ?? '-'
    );

    setField(
        'budget',
        formatBudget(order)
    );

    setField(
        'category',
        order.category || '-'
    );

    setField(
        'deliveryAddress',
        order.deliveryAddress || '-'
    );

    setField(
        'deliveryAddressDetail',
        order.deliveryAddressDetail || '-'
    );

    setField(
        'description',
        order.description
        || '등록된 상세 요청사항이 없습니다.'
    );

    renderReferenceImage(
        order.referenceImageUrl
    );
}

/**
 * 참고 이미지가 있으면 주문 상세 화면에 표시한다.
 */
function renderReferenceImage(
    referenceImageUrl
) {
    const section =
        document.getElementById(
            'referenceImageSection'
        );

    const image =
        document.getElementById(
            'referenceImage'
        );

    if (!referenceImageUrl) {
        section.hidden = true;
        image.removeAttribute('src');
        return;
    }

    image.src =
        referenceImageUrl;

    section.hidden =
        false;
}

/**
 * 주문 상태와 사용자 권한에 맞는 액션 버튼을 표시한다.
 */
async function configureActions(order) {
    if (
        order.status
        !== 'MATCHING'
    ) {
        return;
    }

    if (
        Number(order.buyerId)
        === currentUserId
    ) {
        document.getElementById(
            'orderOwnerActions'
        ).hidden = false;

        document.getElementById(
            'matchingLink'
        ).href =
            `/requests/${requestId}/matches`;

        document.getElementById(
            'editLink'
        ).href =
            `/requests/${requestId}/edit`;

        const cancelButton =
            document.getElementById(
                'cancelOrderButton'
            );

        cancelButton.addEventListener(
            'click',
            cancelOrder
        );

        return;
    }

    const eligibility =
        await authFetch(
            '/api/v1/proposals/eligibility'
        );

    if (eligibility.ok) {
        document.getElementById(
            'orderSellerActions'
        ).hidden = false;

        document.getElementById(
            'proposalLink'
        ).href =
            `/requests/${requestId}/proposals/new`;

        const sellerChatButton =
            document.getElementById(
                'sellerChatButton'
            );

        const hasChat =
            await hasActiveOrderChat(
                requestId
            );

        sellerChatButton.textContent =
            hasChat
                ? '💬 채팅 이어가기'
                : '💬 바로 채팅하기';

        sellerChatButton.addEventListener(
            'click',
            startChatForOrder
        );
    }
}

/**
 * 현재 주문을 기준으로 진행 중인 채팅방이 이미 있는지 확인한다.
 */
async function hasActiveOrderChat(orderRequestId) {
    if (readCurrentUserId() === null) {
        return false;
    }

    try {
        const response =
            await authFetch(
                '/api/v1/chat-rooms'
            );

        if (!response.ok) {
            return false;
        }

        const rooms =
            await readApiBody(response);

        return Array.isArray(rooms)
            && rooms.some(room =>
                Number(room.orderRequestId)
                === Number(orderRequestId)
                && room.originType === 'PROPOSAL'
                && room.status === 'ACTIVE'
            );

    } catch {
        return false;
    }
}

/**
 * 판매자가 현재 주문의 구매자와 채팅방을 열고 해당 대화로 이동한다.
 */
async function startChatForOrder() {
    const chatButton =
        document.getElementById(
            'sellerChatButton'
        );

    chatButton.disabled = true;
    chatButton.textContent =
        '채팅방 여는 중...';

    try {
        const response =
            await authFetch(
                '/api/v1/chat-rooms',
                {
                    method: 'POST',
                    headers: {
                        'Content-Type':
                            'application/json'
                    },
                    body: JSON.stringify({
                        orderRequestId:
                            Number(requestId),
                        originType:
                            'PROPOSAL'
                    })
                }
            );

        const room =
            await readApiBody(
                response
            );

        if (!response.ok) {
            throw new Error(
                room?.message
                ?? '채팅방을 열지 못했습니다.'
            );
        }

        window.location.href =
            `/chat?roomId=${room.chatRoomId}`;

    } catch (error) {
        alert(
            error.message
            ?? '채팅방을 열지 못했습니다.'
        );

        chatButton.disabled = false;
        chatButton.textContent =
            '💬 바로 채팅하기';
    }
}

/**
 * 현재 주문을 취소한다.
 */
async function cancelOrder() {
    if (
        !confirm(
            '이 주문을 취소하시겠습니까?'
        )
    ) {
        return;
    }

    const response =
        await authFetch(
            `/api/v1/requests/${requestId}/cancel`,
            {
                method: 'PATCH'
            }
        );

    if (response.status === 401) {
        redirectToLogin();
    }

    if (!response.ok) {
        const body =
            await readApiBody(
                response
            );

        alert(
            body?.message
            || '주문 취소 중 문제가 발생했습니다.'
        );

        return;
    }

    window.location.reload();
}

/**
 * 주문 상세 필드에 값을 표시한다.
 */
function setField(field, value) {
    document.querySelector(
        `[data-order-field="${field}"]`
    ).textContent =
        value;
}

/**
 * 주문 조회 실패 메시지를 표시한다.
 */
function showError(message) {
    document.getElementById(
        'orderLoading'
    ).hidden = true;

    const error =
        document.getElementById(
            'orderError'
        );

    error.hidden = false;

    error.querySelector(
        'p'
    ).textContent =
        message;
}

/**
 * 로그인 화면으로 이동한다.
 */
function redirectToLogin() {
    const redirect =
        encodeURIComponent(
            location.pathname
            + location.search
        );

    location.replace(
        `/login?redirect=${redirect}`
    );

    throw new Error(
        'Redirecting to login'
    );
}

/**
 * 주문 예산 유형에 맞는 상세 표시 문구를 만든다.
 */
function formatBudget(order) {
    if (
        order.budgetType
        === 'PER_PERSON'
    ) {
        const totalBudget =
            order.totalBudget
            ?? Number(order.budget || 0)
            * Number(order.quantity || 0);

        return `1인당 ${formatNumber(order.budget)}원\n`
            + `총 ${formatNumber(totalBudget)}원`;
    }

    return `총 ${formatNumber(order.budget)}원`;
}

/**
 * 숫자를 천 단위 구분 형식으로 변환한다.
 */
function formatNumber(value) {
    return Number(
        value || 0
    ).toLocaleString(
        'ko-KR'
    );
}

/**
 * 행사 일시를 화면 표시 형식으로 변환한다.
 */
function formatDateTime(value) {
    if (!value) {
        return '-';
    }

    return new Intl.DateTimeFormat(
        'ko-KR',
        {
            dateStyle: 'medium',
            timeStyle: 'short'
        }
    ).format(
        new Date(value)
    );
}

/**
 * 주문 상태 코드를 화면 표시 문구로 변환한다.
 */
function statusLabel(status) {
    return {
        MATCHING: '매칭 중',
        IN_TALK: '협의 중',
        CONFIRMED: '확정',
        CANCELLED: '취소',
        CLOSED: '종료'
    }[status] || status;
}