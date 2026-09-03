import {authFetch, readApiBody} from '/account/js/auth-client.js';

/**
 * 견적 요청 상세 정보를 조회하고
 * 구매자/판매자 관점에 맞게 화면을 구성합니다.
 */

const loadingState =
    document.getElementById(
        'estimateDetailLoading'
    );

const errorState =
    document.getElementById(
        'estimateDetailError'
    );

const detailContent =
    document.getElementById(
        'estimateDetailContent'
    );

const statusElement =
    document.getElementById(
        'estimateStatus'
    );

const productLink =
    document.getElementById(
        'estimateProductLink'
    );

const productButton =
    document.getElementById(
        'estimateProductButton'
    );

const backButton =
    document.getElementById(
        'estimateBackButton'
    );

/**
 * 현재 URL에서 견적 요청 ID를 읽습니다.
 */
function readEstimateId() {
    const match =
        window.location.pathname.match(
            /\/estimates\/(\d+)/
        );

    return match
        ? Number(match[1])
        : null;
}

/**
 * 금액을 원 단위 문자열로 변환합니다.
 */
function formatMoney(value) {
    const amount =
        Number(value);

    if (!Number.isFinite(amount)) {
        return '-';
    }

    return `${amount.toLocaleString('ko-KR')}원`;
}

/**
 * 날짜/시간을 화면 표시용 문자열로 변환합니다.
 */
function formatDateTime(value) {
    if (!value) {
        return '-';
    }

    const date =
        new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return date.toLocaleString('ko-KR');
}

/**
 * 예산 유형을 사용자용 문구로 변환합니다.
 */
function budgetTypeLabel(type) {
    const labels = {
        PER_PERSON: '1인당 예산',
        TOTAL: '총 예산'
    };

    return labels[type]
        ?? type
        ?? '-';
}

/**
 * 견적 요청 상태를 사용자용 문구로 변환합니다.
 */
function statusLabel(status) {
    const labels = {
        REQUESTED: '요청됨',
        IN_TALK: '협의 중',
        ACCEPTED: '수락',
        REJECTED: '거절',
        WITHDRAWN: '철회'
    };

    return labels[status]
        ?? status
        ?? '-';
}

/**
 * 조회 결과를 상세 화면에 표시합니다.
 */
function renderEstimate(estimate) {
    statusElement.textContent =
        statusLabel(
            estimate.status
        );

    statusElement.dataset.status =
        estimate.status ?? '';

    document.getElementById(
        'estimateItemName'
    ).textContent =
        estimate.itemName || '-';

    document.getElementById(
        'estimateQuantity'
    ).textContent =
        estimate.quantity != null
            ? `${estimate.quantity}인분`
            : '-';

    document.getElementById(
        'estimateBudgetType'
    ).textContent =
        budgetTypeLabel(
            estimate.budgetType
        );

    document.getElementById(
        'estimateBudget'
    ).textContent =
        formatMoney(
            estimate.budget
        );

    document.getElementById(
        'estimateEventDateTime'
    ).textContent =
        formatDateTime(
            estimate.eventDateTime
        );

    document.getElementById(
        'estimateDeliveryAddress'
    ).textContent =
        estimate.deliveryAddress
        || '-';

    document.getElementById(
        'estimateDeliveryAddressDetail'
    ).textContent =
        estimate.deliveryAddressDetail
        || '-';

    document.getElementById(
        'estimateCreatedAt'
    ).textContent =
        formatDateTime(
            estimate.createdAt
        );

    document.getElementById(
        'estimateDescription'
    ).textContent =
        estimate.description
        || '별도로 작성한 요청 사항이 없습니다.';

    configureProductLink(
        estimate.productId
    );

    configureViewer(
        estimate
    );

    loadingState.hidden = true;
    errorState.hidden = true;
    detailContent.hidden = false;
}

/**
 * 견적 요청의 상품 상세 링크를 구성합니다.
 */
function configureProductLink(productId) {
    if (productId == null) {
        productLink.hidden = true;
        productButton.hidden = true;
        return;
    }

    const href =
        `/product/detail?id=${encodeURIComponent(productId)}`;

    productLink.href = href;
    productButton.href = href;

    productLink.hidden = false;
    productButton.hidden = false;
}

/**
 * 구매자 또는 판매자 관점에 맞게
 * 안내 문구와 돌아가기 경로를 구성합니다.
 */
function configureViewer(estimate) {
    const direction =
        document.getElementById(
            'estimateDirection'
        );

    if (estimate.seller) {
        direction.textContent =
            '구매자로부터 받은 견적 요청 조건입니다.';

        backButton.href =
            '/mypage/selling-estimates';

        backButton.textContent =
            '받은 견적 요청으로';

        return;
    }

    direction.textContent =
        '판매자에게 보낸 견적 요청 조건입니다.';

    backButton.href =
        '/mypage/buying-estimates';

    backButton.textContent =
        '보낸 견적 요청으로';
}

/**
 * 상세 조회 실패 메시지를 표시합니다.
 */
function showError(message) {
    loadingState.hidden = true;
    detailContent.hidden = true;

    errorState.textContent =
        message;

    errorState.hidden = false;
}

/**
 * 견적 요청 상세 정보를 서버에서 조회합니다.
 */
async function loadEstimate() {
    const estimateId =
        readEstimateId();

    if (estimateId === null) {
        showError(
            '유효한 견적 요청 번호가 없습니다.'
        );

        return;
    }

    try {
        const response =
            await authFetch(
                `/api/v1/estimates/${estimateId}`
            );

        if (response.status === 401) {
            const redirect =
                encodeURIComponent(
                    window.location.pathname
                );

            window.location.href =
                `/login?redirect=${redirect}`;

            return;
        }

        const body =
            await readApiBody(
                response
            );

        if (!response.ok) {
            showError(
                body?.message
                ?? '견적 요청 정보를 불러오지 못했습니다.'
            );

            return;
        }

        renderEstimate(
            body
        );

    } catch (error) {
        console.error(
            '견적 요청 상세 조회 실패:',
            error
        );

        showError(
            '견적 요청 정보를 불러오지 못했습니다.'
        );
    }
}

loadEstimate();