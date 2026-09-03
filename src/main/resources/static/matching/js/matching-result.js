import {authFetch, readApiBody} from '/account/js/auth-client.js';

const page =
    document.getElementById('matchingPage');

const loading =
    document.getElementById('matchingLoading');

const errorArea =
    document.getElementById('matchingError');

const resultsArea =
    document.getElementById('matchingResults');
const summaryArea =
    document.getElementById('matchingSummary');

const resultMeta =
    document.getElementById('matchingResultMeta');

const resultCount =
    document.getElementById('matchingResultCount');

const requestId =
    page?.dataset.requestId;

/**
 * 숫자를 원화 형식으로 표시한다.
 */
function formatMoney(value) {
    return Number(value ?? 0)
        .toLocaleString('ko-KR');
}

/**
 * 매칭 근거 태그 DOM을 생성한다.
 */
function createTag(text) {
    const tag =
        document.createElement('span');

    tag.className = 'matching-tag';
    tag.textContent = text;

    return tag;
}

/**
 * 주문 예산을 현재 예산 유형에 맞게 표시한다.
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

        return `1인당 ${formatMoney(order.budget)}원 · `
            + `총 ${formatMoney(totalBudget)}원`;
    }

    return `총 ${formatMoney(order.budget)}원`;
}

/**
 * 행사·배송 일시를 화면용 형식으로 변환한다.
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
 * 현재 매칭의 기준이 된 주문 조건을 표시한다.
 */
function renderOrderSummary(order) {
    document.getElementById(
        'summaryCategory'
    ).textContent =
        order.category || '-';

    document.getElementById(
        'summaryQuantity'
    ).textContent =
        `${order.quantity ?? '-'}명`;

    document.getElementById(
        'summaryBudget'
    ).textContent =
        formatBudget(order);

    document.getElementById(
        'summaryEventDateTime'
    ).textContent =
        formatDateTime(
            order.eventDateTime
        );

    document.getElementById(
        'summaryDeliveryAddress'
    ).textContent =
        order.deliveryAddress || '-';

    summaryArea.hidden = false;
}

/**
 * 상품 이미지 또는 기본 placeholder를 생성한다.
 */
function createProductImage(product) {
    const wrapper =
        document.createElement('div');

    wrapper.className =
        'matching-product-image';

    if (product.imageUrl) {
        const image =
            document.createElement('img');

        image.src = product.imageUrl;
        image.alt =
            `${product.productName} 이미지`;

        wrapper.appendChild(image);

        return wrapper;
    }

    const placeholder =
        document.createElement('span');

    placeholder.textContent =
        product.productName?.charAt(0)
        ?? '상품';

    wrapper.appendChild(placeholder);

    return wrapper;
}

/**
 * 매칭 결과 한 건을 카드로 생성한다.
 */
function createMatchCard(match, index) {
    const product =
        match.product;

    const card =
        document.createElement('article');

    card.className =
        'matching-card';

    const image =
        createProductImage(product);

    const content =
        document.createElement('div');

    content.className =
        'matching-card-content';

    const heading =
        document.createElement('div');

    heading.className =
        'matching-card-heading';

    const titleArea =
        document.createElement('div');

    const rank =
        document.createElement('span');

    rank.className =
        'matching-rank';

    rank.textContent =
        `${index + 1}위`;

    const title =
        document.createElement('h2');

    title.textContent =
        product.productName
        ?? '상품명 없음';

    titleArea.append(
        rank,
        title
    );

    const score =
        document.createElement('strong');

    score.className =
        'matching-score';

    score.textContent =
        `${match.score}점`;

    heading.append(
        titleArea,
        score
    );

    const meta =
        document.createElement('div');

    meta.className =
        'matching-product-meta';
    const price =
        document.createElement('strong');

    price.textContent =
        `1인분 ${formatMoney(
            product.servingPrice
        )}원`;

    const productInfo =
        document.createElement('span');

    productInfo.className =
        'matching-product-info';

    const minHeadcount =
        product.minHeadcount ?? '-';

    const maxHeadcount =
        product.maxHeadcount ?? '-';

    productInfo.textContent =
        `${product.category || '카테고리 없음'} · `
        + `${minHeadcount}~${maxHeadcount}명`;

    const route =
        document.createElement('span');

    route.textContent =
        `도로 이동거리 ${Number(
            match.routeDistanceKm
        ).toFixed(1)}km · 약 ${
            match.routeDurationMinutes
        }분`;

    meta.append(
        price,
        productInfo,
        route
    );

    const scoreBar =
        document.createElement('div');

    scoreBar.className =
        'matching-score-bar';

    const scoreFill =
        document.createElement('div');

    scoreFill.className =
        'matching-score-fill';

    scoreFill.style.width =
        `${Math.max(
            0,
            Math.min(100, match.score)
        )}%`;

    scoreBar.appendChild(scoreFill);

    const scoreDetail =
        document.createElement('div');

    scoreDetail.className =
        'matching-score-detail';

    scoreDetail.textContent =
        `예산 ${match.budgetScore} · `
        + `거리 ${match.distanceScore} · `
        + `수량 ${match.capacityScore} · `
        + `평점 ${match.ratingScore}`;

    const tags =
        document.createElement('div');

    tags.className =
        'matching-tags';

    (match.tags ?? [])
        .forEach(tag =>
            tags.appendChild(
                createTag(tag)
            )
        );
    const footer =
        document.createElement('div');

    footer.className =
        'matching-card-footer';

    const detailLink =
        document.createElement('a');

    detailLink.className =
        'matching-product-link';

    detailLink.href =
        `/product/detail?id=${product.id}`;

    detailLink.textContent =
        '상품 상세 보기';

    footer.appendChild(
        detailLink
    );
    content.append(
        heading,
        meta,
        scoreBar,
        scoreDetail,
        tags,
        footer
    );

    card.append(
        image,
        content
    );

    return card;
}

/**
 * 매칭 결과 목록을 화면에 표시한다.
 */
function renderMatches(matches) {
    resultsArea.replaceChildren();

    const matchCount =
        Array.isArray(matches)
            ? matches.length
            : 0;

    resultCount.textContent =
        `총 ${matchCount}개의 상품`;

    resultMeta.hidden = false;

    if (!Array.isArray(matches)
        || matches.length === 0) {

        const empty =
            document.createElement('div');

        empty.className =
            'matching-empty';

        const title =
            document.createElement('strong');

        title.textContent =
            '현재 조건에 맞는 상품이 없습니다.';

        const description =
            document.createElement('p');

        description.textContent =
            '예산, 카테고리, 수량 또는 배송 조건을 조정한 뒤 다시 확인해주세요.';

        const editLink =
            document.createElement('a');

        editLink.href =
            `/requests/${requestId}/edit`;

        editLink.textContent =
            '주문 조건 수정하기';

        empty.append(
            title,
            description,
            editLink
        );

        resultsArea.appendChild(empty);

        return;
    }

    matches.forEach(
        (match, index) =>
            resultsArea.appendChild(
                createMatchCard(
                    match,
                    index
                )
            )
    );
}

/**
 * 현재 주문의 매칭 결과를 API에서 조회한다.
 */
async function loadMatches() {
    try {
        const response =
            await authFetch(
                `/api/v1/requests/${requestId}/matches`
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

        const orderResponse =
            await authFetch(
                `/api/v1/requests/${requestId}`
            );

        if (orderResponse.status === 401) {
            const redirect =
                encodeURIComponent(
                    window.location.pathname
                );

            window.location.href =
                `/login?redirect=${redirect}`;

            return;
            const price =
                document.createElement('strong');

            price.textContent =
                `1인분 ${formatMoney(
                    product.servingPrice
                )}원`;

            const productInfo =
                document.createElement('span');

            productInfo.className =
                'matching-product-info';

            const minHeadcount =
                product.minHeadcount ?? '-';

            const maxHeadcount =
                product.maxHeadcount ?? '-';

            productInfo.textContent =
                `${product.category || '카테고리 없음'} · `
                + `${minHeadcount}~${maxHeadcount}명`;

            const route =
                document.createElement('span');

            route.textContent =
                `도로 이동거리 ${Number(
                    match.routeDistanceKm
                ).toFixed(1)}km · 약 ${
                    match.routeDurationMinutes
                }분`;

            meta.append(
                price,
                productInfo,
                route
            );
        }

        const order =
            await readApiBody(
                orderResponse
            );

        if (!orderResponse.ok) {
            throw new Error(
                order?.message
                ?? '매칭 기준 주문을 불러오지 못했습니다.'
            );
        }

        renderOrderSummary(order);

        const body =
            await readApiBody(response);

        if (response.status === 403) {
            throw new Error(
                '본인이 등록한 주문의 매칭 결과만 확인할 수 있습니다.'
            );
        }

        if (!response.ok) {
            throw new Error(
                body?.message
                ?? '매칭 결과를 불러오지 못했습니다.'
            );
        }

        renderMatches(body);

        loading.hidden = true;
        resultsArea.hidden = false;

    } catch (error) {
        loading.hidden = true;

        errorArea.textContent =
            error.message;

        errorArea.hidden = false;
    }
}

if (requestId) {
    loadMatches();
}