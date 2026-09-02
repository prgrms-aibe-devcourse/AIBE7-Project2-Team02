const adapters = {
    requests: adaptRequest,
    products: adaptProduct,
    purchases: adaptPurchase,
    sales: adaptSale,
    receivedOffers: (record) => adaptOffer(record, '받은 오퍼'),
    sentOffers: (record) => adaptOffer(record, '보낸 오퍼'),
    receivedEstimates: (record) => adaptEstimate(record, '받은 견적'),
    sentEstimates: (record) => adaptEstimate(record, '보낸 견적'),
    chats: adaptChatRoom,
};

const sourceLabels = {
    requests: '구매 요청',
    products: '등록 상품',
    purchases: '구매 거래',
    sales: '판매 거래',
    receivedOffers: '받은 제안',
    sentOffers: '보낸 제안',
    receivedEstimates: '받은 견적',
    sentEstimates: '보낸 견적',
    chats: '채팅',
};

export const mypageViews = Object.freeze({
    profile: {title: '계정 개요', kicker: 'ACCOUNT', sources: []},
    reports: {title: '신고 및 문의', kicker: 'REPORTS', sources: []},
    requests: {
        title: '등록 관리',
        kicker: 'REGISTRATION',
        group: 'registration',
        tabs: [
            {
                key: 'requests',
                label: '등록 주문',
                href: '/mypage/requests',
            },
            {
                key: 'products',
                label: '등록 상품',
                href: '/mypage/products',
                sellerOnly: true,
            },
        ],
        empty: [
            '등록한 주문이 없습니다.',
            '새 주문을 등록하면 이곳에서 확인할 수 있습니다.',
        ],
        sources: [
            {
                key: 'requests',
                label: '등록 주문',
                endpoint: '/api/v1/requests/me',
            },
        ],
    },

    products: {
        title: '등록 관리',
        kicker: 'REGISTRATION',
        group: 'registration',
        sellerOnly: true,
        tabs: [
            {
                key: 'requests',
                label: '등록 주문',
                href: '/mypage/requests',
            },
            {
                key: 'products',
                label: '등록 상품',
                href: '/mypage/products',
                sellerOnly: true,
            },
        ],
        empty: [
            '등록한 상품이 없습니다.',
            '판매할 상품을 등록하면 이곳에서 확인할 수 있습니다.',
        ],
        sources: [
            {
                key: 'products',
                label: '등록 상품',
                endpoint: '/api/v1/products/mine',
                sellerOnly: true,
            },
        ],
    },
    purchases: {
        title: '거래 내역',
        kicker: 'TRADES',
        group: 'trade',
        tabs: [
            {
                key: 'purchases',
                label: '구매 거래',
                href: '/mypage/purchases',
            },
            {
                key: 'sales',
                label: '판매 거래',
                href: '/mypage/sales',
                sellerOnly: true,
            },
        ],
        empty: [
            '구매 거래가 없습니다.',
            '구매자로 진행한 거래가 시작되면 이곳에서 확인할 수 있습니다.',
        ],
        pending: [
            '구매 거래를 불러올 수 없습니다.',
            '거래 조회 API 연결 상태를 확인해 주세요.',
        ],
        sources: [
            {
                key: 'purchases',
                label: '구매 거래',
                endpoint: '/api/v1/orders/purchases?page=0&size=100',
            },
        ],
    },
    sales: {
        title: '거래 내역',
        kicker: 'TRADES',
        group: 'trade',
        sellerOnly: true,
        tabs: [
            {
                key: 'purchases',
                label: '구매 거래',
                href: '/mypage/purchases',
            },
            {
                key: 'sales',
                label: '판매 거래',
                href: '/mypage/sales',
                sellerOnly: true,
            },
        ],
        empty: [
            '판매 거래가 없습니다.',
            '판매자로 진행한 거래가 시작되면 이곳에서 확인할 수 있습니다.',
        ],
        pending: [
            '판매 거래를 불러올 수 없습니다.',
            '거래 조회 API 연결 상태를 확인해 주세요.',
        ],
        sources: [
            {
                key: 'sales',
                label: '판매 거래',
                endpoint: '/api/v1/orders/sales?page=0&size=100',
                sellerOnly: true,
            },
        ],
    },
    offers: {
        title: '제안 관리',
        kicker: 'PROPOSALS',
        empty: ['주고받은 제안이 없습니다.', '제안이나 견적 요청이 생성되면 이곳에 표시됩니다.'],
        sources: [
            {key: 'receivedOffers', label: '받은 제안', endpoint: '/api/v1/proposals/received?page=0&size=100'},
            {key: 'sentOffers', label: '보낸 제안', endpoint: '/api/v1/proposals/sent?page=0&size=100', sellerOnly: true},
            {key: 'receivedEstimates', label: '받은 견적', endpoint: '/api/v1/estimates/received', sellerOnly: true},
            {key: 'sentEstimates', label: '보낸 견적', endpoint: '/api/v1/estimates/sent'},
        ],
    },
    chats: {
        title: '채팅',
        kicker: 'CHATS',
        empty: ['참여 중인 채팅방이 없습니다.', '거래 대화가 시작되면 채팅방 요약이 이곳에 표시됩니다.'],
        sources: [{key: 'chats', label: '채팅', endpoint: '/api/v1/chat-rooms?page=0&size=100'}],
    },
});

export function adaptMypagePayload(sourceKey, payload) {
    const adapter = adapters[sourceKey];

    if (!adapter) {
        throw new Error(`Unknown mypage source: ${sourceKey}`);
    }

    const records = extractRecords(payload);

    // 거래 내역에서는 최종 견적서가 생성된 거래만 표시한다.
    return records.map((record) => ({
        ...adapter(record),
        sourceKey,
        sourceLabel: sourceLabels[sourceKey] || sourceKey,
    }));
}

/**
 * 공식 견적 단계 이후 결과가 확정된 거래인지 확인합니다.
 */
function isCompletedTradeRecord(record) {
    if (value(record, 'sourceType') !== 'QUOTE') {
        return false;
    }

    const paymentStatus =
        value(record, 'paymentStatus');

    const sourceStatus =
        value(record, 'sourceStatus', 'status');

    if (paymentStatus === 'COMPLETED') {
        return true;
    }

    if (paymentStatus === 'CANCELLED') {
        return true;
    }

    return [
        'REJECTED',
        'WITHDRAWN',
        'CANCELLED',
        'CANCELED',
    ].includes(sourceStatus);
}

export function filterMypageRecords(records, query = '', statusCode = '') {
    const normalizedQuery = query.trim().toLocaleLowerCase('ko-KR');
    return records.filter((record) => {
        const matchesStatus = !statusCode || record.statusCode === statusCode;
        if (!matchesStatus || !normalizedQuery) return matchesStatus;
        return [record.title, record.detail, record.meta, record.sourceLabel, record.status]
            .some((candidate) => candidate?.toLocaleLowerCase('ko-KR').includes(normalizedQuery));
    });
}

export function extractPage(payload) {
    return {
        page: numberOrNull(payload?.page ?? payload?.number),
        totalPages: numberOrNull(payload?.totalPages),
        totalElements: numberOrNull(payload?.totalElements),
    };
}

function extractRecords(payload) {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload?.content)) return payload.content;
    if (Array.isArray(payload?.items)) return payload.items;
    if (Array.isArray(payload?.data)) return payload.data;
    return payload ? [payload] : [];
}

// Domain DTO changes are intentionally absorbed by the adapters below.
function adaptRequest(record) {
    const id = value(record, 'requestId', 'id');
    return viewRecord(
        `request-${id}`,
        value(record, 'title', 'requestTitle', 'name') || '구매 요청',
        value(record, 'status', 'requestStatus'),
        value(record, 'description', 'deliveryAddress', 'message'),
        joinMeta(
            labeledDate('행사', value(record, 'eventDateTime', 'eventDate')),
            labeled('수량', value(record, 'quantity'), '명'),
            labeledMoney('예산', value(record, 'budget')),
            labeled('카테고리', value(record, 'category')),
        ),
        id ? `/requests/${encodeURIComponent(id)}` : '',
        '주문 보기',
        value(record, 'eventDateTime', 'createdAt', 'requestedAt'),
    );
}

function adaptProduct(record) {
    const id = value(record, 'id', 'productId');
    const hidden = record?.hidden === true;

    const view = viewRecord(
        `product-${id}`,
        value(record, 'productName') || '등록 상품',
        hidden ? 'HIDDEN' : 'VISIBLE',
        value(record, 'description'),
        joinMeta(
            labeled('카테고리', value(record, 'category')),
            labeledMoney('1인 가격', value(record, 'servingPrice')),
            labeled('최소 주문', value(record, 'minHeadcount'), '인분'),
            labeled('최대 주문', value(record, 'maxHeadcount'), '인분'),
            labeledDate('수정', value(record, 'updatedAt')),
        ),
        id ? `/product/detail?id=${encodeURIComponent(id)}` : '',
        '상품 보기',
        value(record, 'updatedAt', 'createdAt'),
    );

    return {
        ...view,
        status: hidden ? '숨김' : '판매 중',
    };
}

function adaptPurchase(record) {
    return adaptTradeActivity(record, 'purchase');
}

function adaptSale(record) {
    return adaptTradeActivity(record, 'sale');
}

function adaptTradeActivity(record, perspective) {
    const id = value(record, 'activityId', 'sourceId', 'id');
    const sourceType = value(record, 'sourceType') || 'QUOTE';
    const sourceId = value(record, 'sourceId', 'quoteId', 'id');
    const state = tradeDisplayState(record);
    const href = tradeActivityHref(record);
    const paymentId = value(record, 'paymentId');
    const reviewPaymentId =
        perspective === 'purchase' && state.code === 'COMPLETED' && paymentId
            ? paymentId
            : null;
    const view = viewRecord(
        `${perspective}-${id}`,
        value(record, 'itemName', 'title', 'requestTitle', 'productName')
        || `${activityTypeLabel(sourceType)} #${sourceId}`,
        state.code,
        value(record, 'description', 'additionalNotes'),
        joinMeta(
            activityTypeLabel(sourceType),
            value(record, 'direction') === 'SENT' ? '내가 보냄' : '내가 받음',
            labeled('상태', statusLabel(value(record, 'sourceStatus', 'status'))),
            labeled('수량', value(record, 'quantity'), '명'),
            labeledMoney(perspective === 'purchase' ? '금액' : '예상 정산', value(record, 'totalAmount')),
            labeledDate('행사', value(record, 'eventDateTime')),
            labeledDate('등록', value(record, 'createdAt')),
            labeledDate('결제', value(record, 'paidAt')),
        ),
        href,
        href ? '거래 보기' : '',
        value(record, 'paidAt', 'createdAt', 'eventDateTime'),
        reviewPaymentId,
        sourceType,
        sourceId,
    );

    return {
        ...view,
        status: state.label,
    };
}

function tradeDisplayState(record) {
    const paymentStatus =
        value(record, 'paymentStatus');

    const sourceStatus =
        value(record, 'sourceStatus', 'status');

    const sourceType =
        value(record, 'sourceType');

    const direction =
        value(record, 'direction');

    if (paymentStatus === 'COMPLETED') {
        return {
            code: 'COMPLETED',
            label: '완료',
        };
    }

    if (
        paymentStatus === 'CANCELLED'
        || [
            'REJECTED',
            'WITHDRAWN',
            'CANCELLED',
            'CANCELED',
        ].includes(sourceStatus)
    ) {
        return {
            code: 'CLOSED',
            label: '거래 종료',
        };
    }

    if (
        paymentStatus
        || ['ACCEPTED', 'IN_TALK'].includes(sourceStatus)
    ) {
        return {
            code: 'IN_PROGRESS',
            label: '진행 중',
        };
    }

    if (sourceType === 'PROPOSAL') {
        return {code: 'PROPOSED', label: '제안'};
    }

    if (sourceType === 'ESTIMATE') {
        return direction === 'SENT'
            ? {
                code: 'PROPOSED',
                label: '견적 요청 보냄',
            }
            : {
                code: 'PROPOSED',
                label: '견적 요청 받음',
            };
    }

    if (sourceType === 'QUOTE') {
        return direction === 'SENT'
            ? {
                code: 'PROPOSED',
                label: '견적 보냄',
            }
            : {
                code: 'PROPOSED',
                label: '견적 받음',
            };
    }

    return {
        code: 'PROPOSED',
        label: '거래 확인',
    };
}

function tradeActivityHref(record) {
    const sourceType = value(record, 'sourceType');
    if (sourceType === 'PROPOSAL' && value(record, 'requestId')) {
        return `/requests/${encodeURIComponent(value(record, 'requestId'))}`;
    }
    if (sourceType === 'ESTIMATE' && value(record, 'sourceId')) {
        return `/estimates/${encodeURIComponent(value(record, 'sourceId'))}`;
    }
    if (sourceType === 'QUOTE' && value(record, 'chatRoomId')) {
        return `/chat?roomId=${encodeURIComponent(value(record, 'chatRoomId'))}`;
    }
    return '';
}

function activityTypeLabel(sourceType) {
    return {
        PROPOSAL: '주문 제안',
        ESTIMATE: '견적 요청',
        QUOTE: '견적 거래',
    }[sourceType] || '거래';
}

function adaptOffer(record, direction) {
    const id = value(record, 'proposalId', 'id');
    const requestId = value(record, 'requestId');
    return viewRecord(
        `proposal-${id}-${direction}`,
        value(record, 'itemName', 'requestTitle', 'title') || `${direction} #${id}`,
        value(record, 'status', 'proposalStatus'),
        value(record, 'description', 'message', 'sellerName', 'buyerName'),
        joinMeta(
            direction,
            labeled('수량', value(record, 'quantity'), '명'),
            labeledMoney('1인 단가', value(record, 'unitPrice')),
            labeledMoney('총액', value(record, 'totalAmount')),
            labeledDate('제안', value(record, 'createdAt', 'proposedAt')),
        ),
        requestId ? `/requests/${encodeURIComponent(requestId)}` : '/proposals',
        requestId ? '주문 보기' : '제안 보기',
        value(record, 'createdAt', 'proposedAt'),
        null,
        'PROPOSAL',
        id,
    );
}

function adaptEstimate(record, direction) {
    const id = value(record, 'estimateId', 'id');
    return viewRecord(
        `estimate-${id}-${direction}`,
        value(record, 'itemName', 'requestTitle', 'title') || `${direction} #${id}`,
        value(record, 'status', 'estimateStatus'),
        value(record, 'description', 'message', 'sellerName', 'buyerName'),
        joinMeta(
            direction,
            labeledMoney('예산', value(record, 'budget')),
            labeledDate('행사', value(record, 'eventDateTime')),
            labeledDate('요청', value(record, 'createdAt')),
        ),
        id ? `/estimates/${encodeURIComponent(id)}` : '/estimates',
        '견적 보기',
        value(record, 'createdAt', 'eventDateTime'),
        null,
        'ESTIMATE',
        id,
    );
}

function adaptChatRoom(record) {
    const id = value(record, 'chatRoomId', 'id');
    const originType = value(record, 'originType');
    return viewRecord(
        `chat-${id}`,
        value(record, 'requestTitle', 'title', 'counterpartName')
        || `${originLabel(originType)} 채팅방 #${id}`,
        value(record, 'status', 'chatStatus') || '진행 중',
        value(record, 'lastMessage', 'message') || '아직 메시지가 없습니다.',
        joinMeta(
            labeled('유형', originLabel(originType)),
            chatReference(record),
            labeledDate('최근 메시지', value(record, 'lastMessageAt')),
            labeledDate('개설', value(record, 'createdAt')),
        ),
        id ? `/chat?roomId=${encodeURIComponent(id)}` : '/chat',
        '채팅 열기',
        value(record, 'lastMessageAt', 'updatedAt', 'createdAt'),
        null,
        'CHAT_ROOM',
        id,
    );
}

function viewRecord(
    key, title, status, detail, meta, href = '', actionLabel = '', sortValue = '',
    reviewPaymentId = null, reportTargetType = null, reportTargetId = null
) {
    const statusCode = status || 'ACTIVE';
    return {
        key,
        title,
        status: statusLabel(statusCode),
        statusCode,
        detail: detail || '',
        meta: meta || '',
        href,
        actionLabel,
        sortAt: timestamp(sortValue),
        reviewPaymentId,
        reportTargetType,
        reportTargetId,
    };
}

function value(record, ...keys) {
    for (const key of keys) {
        const candidate = record?.[key];
        if (candidate !== undefined && candidate !== null && candidate !== '') return String(candidate);
    }
    return '';
}

function joinMeta(...parts) {
    return parts.filter(Boolean).join(' · ');
}

function labeled(label, candidate, suffix = '') {
    return candidate ? `${label} ${candidate}${suffix}` : '';
}

function labeledMoney(label, candidate) {
    if (!candidate) return '';
    const amount = Number(candidate);
    return Number.isFinite(amount) ? `${label} ${amount.toLocaleString('ko-KR')}원` : `${label} ${candidate}`;
}

function labeledDate(label, candidate) {
    if (!candidate) return '';
    const date = new Date(candidate);
    return Number.isNaN(date.getTime()) ? `${label} ${candidate}` : `${label} ${date.toLocaleString('ko-KR')}`;
}

function timestamp(candidate) {
    if (!candidate) return 0;
    const time = new Date(candidate).getTime();
    return Number.isNaN(time) ? 0 : time;
}

function statusLabel(status) {
    const labels = {
        MATCHING: '매칭 중',
        IN_TALK: '협의 중',
        CONFIRMED: '확정',
        COMPLETED: '완료',
        CANCELED: '취소',
        CANCELLED: '취소',
        SENT: '전송됨',
        REQUESTED: '요청됨',
        ACCEPTED: '수락',
        REJECTED: '거절',
        WITHDRAWN: '철회',
        ACTIVE: '진행 중',
        IN_PROGRESS: '진행 중',
        PROPOSED: '제안',
        CLOSED: '종료',
        PENDING: '대기',
        FAILED: '실패',
    };
    return labels[status] || status || '진행 중';
}

function originLabel(originType) {
    return {
        PROPOSAL: '제안 기반',
        INQUIRY: '문의',
    }[originType] || '거래';
}

function chatReference(record) {
    const references = [
        labeled('제안', value(record, 'proposalId') ? `#${value(record, 'proposalId')}` : ''),
        labeled('견적', value(record, 'quoteId') ? `#${value(record, 'quoteId')}` : ''),
    ];
    return references.filter(Boolean).join(' · ') || '연결된 거래 채팅입니다.';
}

function numberOrNull(value) {
    return Number.isInteger(value) ? value : null;
}
