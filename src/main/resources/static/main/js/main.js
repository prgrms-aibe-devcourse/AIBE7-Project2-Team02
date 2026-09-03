import {authFetch, readApiBody, readCurrentUserId} from '/account/js/auth-client.js';

/**
 * 메인페이지의 구매자 맞춤 매칭 미리보기를 처리한다.
 */

const context = document.getElementById('matchingPreviewContext');
const loading = document.getElementById('matchingPreviewLoading');
const message = document.getElementById('matchingPreviewMessage');
const cards = document.getElementById('matchingPreviewCards');
const actions = document.getElementById('matchingPreviewActions');
const allLink = document.getElementById('matchingAllLink');
const sellerMatchingSection =
    document.getElementById('sellerMatchingSection');
const sellerMatchingLoading =
    document.getElementById('sellerMatchingLoading');

const sellerMatchingMessage =
    document.getElementById('sellerMatchingMessage');

const sellerMatchingCards =
    document.getElementById('sellerMatchingCards');

const sellerMatchingActions =
    document.getElementById('sellerMatchingActions');
const productLoading =
    document.getElementById('productPreviewLoading');

const productMessage =
    document.getElementById('productPreviewMessage');

const productCards =
    document.getElementById('productPreviewCards');

const productActions =
    document.getElementById('productPreviewActions');

/**
 * 금액을 원화 표시 형식으로 변환한다.
 */
function formatMoney(value) {
    return Number(value ?? 0).toLocaleString('ko-KR');
}

/**
 * 안내 메시지와 이동 버튼을 표시한다.
 */
function showMessage(text, linkText, href) {
    loading.hidden = true;
    cards.hidden = true;
    actions.hidden = true;
    context.hidden = true;

    message.replaceChildren();

    const description = document.createElement('p');
    description.textContent = text;

    message.appendChild(description);

    if (linkText && href) {
        const link = document.createElement('a');

        link.className = 'button';
        link.href = href;
        link.textContent = linkText;

        message.appendChild(link);
    }

    message.hidden = false;
}

/**
 * 매칭 결과 카드 한 건을 생성한다.
 */
function createMatchCard(match, index) {
    const product = match.product;

    const card = document.createElement('article');
    card.className =
        'main-match-card main-buyer-match-card';

    const heading = document.createElement('div');
    heading.className = 'main-match-heading';

    const titleArea = document.createElement('div');

    const rank = document.createElement('span');
    rank.className = 'main-match-rank';
    rank.textContent = `${index + 1}위`;

    const title = document.createElement('h3');
    title.className = 'main-match-title';
    title.textContent = product?.productName ?? '상품명 없음';

    const score = document.createElement('strong');
    score.className = 'main-match-score';
    score.textContent = `${match.score}점`;

    titleArea.append(rank, title);
    heading.append(titleArea, score);

    const meta = document.createElement('div');
    meta.className = 'main-match-meta';

    const price = document.createElement('span');
    price.className = 'main-match-price';
    price.textContent =
        `1인분 ${formatMoney(product?.servingPrice)}원`;

    const route = document.createElement('span');
    route.textContent =
        `도로 이동거리 ${Number(match.routeDistanceKm ?? 0).toFixed(1)}km`
        + ` · 약 ${match.routeDurationMinutes ?? 0}분`;

    meta.append(price, route);

    const tags = document.createElement('div');
    tags.className = 'main-match-tags';

    (match.tags ?? [])
        .slice(0, 3)
        .forEach(text => {
            const tag = document.createElement('span');

            tag.className = 'main-match-tag';
            tag.textContent = text;

            tags.appendChild(tag);
        });

    card.append(heading, meta, tags);

    return card;
}

/**
 * 최근 MATCHING 상태 주문을 찾는다.
 */
function findRecentMatchingOrder(orders) {
    return orders
        .filter(order => order.status === 'MATCHING')
        .sort((left, right) =>
            Number(right.id) - Number(left.id)
        )[0] ?? null;
}

/**
 * 최근 주문의 매칭 결과를 조회하고 화면에 표시한다.
 */
async function loadMatchingPreview() {
    if (readCurrentUserId() === null) {
        showMessage(
            '로그인 후 맞춤 매칭을 확인할 수 있습니다.',
            '로그인',
            '/login?redirect=/'
        );
        return;
    }

    try {
        const orderResponse =
            await authFetch('/api/v1/requests/me');

        if (orderResponse.status === 401) {
            showMessage(
                '로그인이 필요합니다.',
                '로그인',
                '/login?redirect=/'
            );
            return;
        }

        const orders =
            await readApiBody(orderResponse);

        if (!orderResponse.ok) {
            throw new Error(
                orders?.message
                ?? '주문 정보를 불러오지 못했습니다.'
            );
        }

        if (!Array.isArray(orders) || orders.length === 0) {
            showMessage(
                '등록한 주문이 없습니다.',
                '주문하기',
                '/requests/new'
            );
            return;
        }

        const recentOrder =
            findRecentMatchingOrder(orders);

        if (!recentOrder) {
            showMessage(
                '현재 매칭 중인 주문이 없습니다.',
                '주문하기',
                '/requests/new'
            );
            return;
        }

        const matchResponse =
            await authFetch(
                `/api/v1/requests/${recentOrder.id}/matches`
            );

        if (matchResponse.status === 401) {
            showMessage(
                '로그인이 필요합니다.',
                '로그인',
                '/login?redirect=/'
            );
            return;
        }

        const matches =
            await readApiBody(matchResponse);

        if (!matchResponse.ok) {
            throw new Error(
                matches?.message
                ?? '매칭 결과를 불러오지 못했습니다.'
            );
        }

        if (!Array.isArray(matches)
            || matches.length === 0) {

            showMessage(
                '현재 조건에 맞는 상품이 없습니다.',
                '주문 조건 수정하기',
                `/requests/${recentOrder.id}/edit`
            );
            return;
        }

        const previewMatches = [...matches]
            .sort((left, right) =>
                Number(right.score) - Number(left.score)
            )
            .slice(0, 3);

        cards.replaceChildren();

        previewMatches.forEach(
            (match, index) =>
                cards.appendChild(
                    createMatchCard(match, index)
                )
        );

        context.textContent =
            recentOrder.title
                ? `“${recentOrder.title}” 주문 기준 추천`
                : '최근 주문 기준 추천';

        allLink.href =
            `/requests/${recentOrder.id}/matches`;

        loading.hidden = true;
        message.hidden = true;
        context.hidden = false;
        cards.hidden = false;
        actions.hidden = false;

    } catch (error) {
        showMessage(
            error.message
            ?? '맞춤 매칭을 불러오지 못했습니다.'
        );
    }
}

/**
 * 주문 행사 일시를 화면 표시 형식으로 변환한다.
 */
function formatEventDateTime(value) {
    if (!value) {
        return '행사 일시 미정';
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    }).format(date);
}

/**
 * 주문의 예산 조건을 화면 표시 형식으로 변환한다.
 */
function formatOrderBudget(order) {
    if (order?.budget == null) {
        return '예산 미정';
    }

    const label =
        order.budgetType === 'PER_PERSON'
            ? '1인 예산'
            : '총 예산';

    return `${label} ${formatMoney(order.budget)}원`;
}

/**
 * 판매자 맞춤 주문 영역에 안내 메시지를 표시한다.
 */
function showSellerMatchingMessage(text) {
    sellerMatchingLoading.hidden = true;
    sellerMatchingCards.hidden = true;

    sellerMatchingMessage.textContent = text;
    sellerMatchingMessage.hidden = false;

    sellerMatchingActions.hidden = false;
}

/**
 * 판매자 맞춤 주문 카드 한 건을 생성한다.
 */
function createSellerMatchCard(match, index) {
    const order = match.orderRequest;
    const product = match.sourceProduct;

    const card = document.createElement('article');
    card.className =
        'main-match-card main-seller-match-card';

    const heading = document.createElement('div');
    heading.className = 'main-match-heading';

    const titleArea = document.createElement('div');

    const rank = document.createElement('span');
    rank.className = 'main-match-rank';
    rank.textContent = `${index + 1}위`;

    const title = document.createElement('h3');
    title.className = 'main-match-title';
    title.textContent =
        order?.title ?? '주문 제목 없음';

    const score = document.createElement('strong');
    score.className = 'main-match-score';
    score.textContent =
        `${Number(match.totalScore ?? 0).toFixed(1)}점`;

    titleArea.append(rank, title);
    heading.append(titleArea, score);

    const meta = document.createElement('div');
    meta.className = 'main-match-meta';

    const sourceProduct = document.createElement('span');
    sourceProduct.className = 'main-match-price';
    sourceProduct.textContent =
        `판매 조건: ${product?.productName ?? '상품명 없음'}`;

    const quantity = document.createElement('span');
    quantity.textContent =
        `수량 ${Number(order?.quantity ?? 0).toLocaleString('ko-KR')}명`;

    const budget = document.createElement('span');
    budget.textContent =
        formatOrderBudget(order);

    const category = document.createElement('span');
    category.textContent =
        `카테고리 ${order?.category ?? '미지정'}`;

    const eventDate = document.createElement('span');
    eventDate.textContent =
        formatEventDateTime(order?.eventDateTime);

    meta.append(
        sourceProduct,
        quantity,
        budget,
        category,
        eventDate
    );

    const tags = document.createElement('div');
    tags.className = 'main-match-tags';

    [...(match.scoreItems ?? [])]
        .sort(
            (left, right) =>
                Number(right.contribution ?? 0)
                - Number(left.contribution ?? 0)
        )
        .slice(0, 3)
        .forEach(item => {
            const tag = document.createElement('span');

            tag.className = 'main-match-tag';
            tag.textContent =
                item.label ?? '매칭 조건';

            tags.appendChild(tag);
        });

    const footer = document.createElement('div');
    footer.className = 'main-seller-match-footer';

    const detailLink = document.createElement('a');
    detailLink.className = 'button';
    detailLink.href = `/requests/${order.id}`;
    detailLink.textContent = '주문 보기';

    footer.appendChild(detailLink);

    card.append(
        heading,
        meta,
        tags,
        footer
    );

    return card;
}

/**
 * 상품별 추천 결과에서 주문별 최고 점수 결과만 남긴다.
 */
function mergeSellerRecommendations(matches) {
    const bestByOrderId = new Map();

    matches.forEach(match => {
        const orderId = match.orderRequest?.id;

        if (orderId == null) {
            return;
        }

        const existing =
            bestByOrderId.get(orderId);

        if (
            !existing
            || Number(match.totalScore ?? 0)
            > Number(existing.totalScore ?? 0)
        ) {
            bestByOrderId.set(orderId, match);
        }
    });

    return [...bestByOrderId.values()]
        .sort(
            (left, right) =>
                Number(right.totalScore ?? 0)
                - Number(left.totalScore ?? 0)
        );
}

/**
 * 판매자의 상품 한 건에 대한 추천 주문 목록을 조회한다.
 */
async function fetchSellerRecommendations(product) {
    const response =
        await authFetch(
            `/api/products/${product.id}`
            + '/order-requests/recommendations'
        );

    const result =
        await readApiBody(response);

    if (!response.ok) {
        throw new Error(
            result?.message
            ?? '상품의 맞춤 주문을 불러오지 못했습니다.'
        );
    }

    if (!Array.isArray(result)) {
        return [];
    }

    return result.map(match => ({
        ...match,
        sourceProduct: product
    }));
}

/**
 * 승인 판매자의 상품을 기준으로 맞춤 주문 미리보기를 조회한다.
 */
async function loadSellerRecommendations() {
    try {
        const productResponse =
            await authFetch('/api/v1/products/mine');

        const products =
            await readApiBody(productResponse);

        if (!productResponse.ok) {
            throw new Error(
                products?.message
                ?? '내 상품을 불러오지 못했습니다.'
            );
        }

        const activeProducts =
            Array.isArray(products)
                ? products.filter(product => !product.hidden)
                : [];

        if (activeProducts.length === 0) {
            showSellerMatchingMessage(
                '등록된 판매 조건이 없습니다.'
            );
            return;
        }

        // 한 상품의 추천 조회 실패가 전체 메인페이지를 막지 않도록 개별 처리한다.
        const recommendationResults =
            await Promise.allSettled(
                activeProducts.map(
                    fetchSellerRecommendations
                )
            );

        const allMatches =
            recommendationResults
                .filter(
                    result =>
                        result.status === 'fulfilled'
                )
                .flatMap(result => result.value);

        const recommendations =
            mergeSellerRecommendations(allMatches)
                .slice(0, 3);

        sellerMatchingLoading.hidden = true;

        if (recommendations.length === 0) {
            showSellerMatchingMessage(
                '현재 판매 조건에 맞는 주문이 없습니다.'
            );
            return;
        }

        sellerMatchingCards.replaceChildren();

        recommendations.forEach(
            (match, index) => {
                sellerMatchingCards.appendChild(
                    createSellerMatchCard(
                        match,
                        index
                    )
                );
            }
        );

        sellerMatchingMessage.hidden = true;
        sellerMatchingCards.hidden = false;
        sellerMatchingActions.hidden = false;

    } catch (error) {
        showSellerMatchingMessage(
            error.message
            ?? '판매자 맞춤 주문을 불러오지 못했습니다.'
        );
    }
}

/**
 * 승인 판매자에게 판매자 맞춤 주문 영역을 표시한다.
 */
async function loadSellerMatchingSection() {
    try {
        const response =
            await authFetch('/api/v1/account/me');

        if (!response.ok) {
            return;
        }

        const profile =
            await readApiBody(response);

        if (profile?.role !== 'SELLER') {
            return;
        }

        sellerMatchingSection.hidden = false;

        await loadSellerRecommendations();

    } catch {
        // 판매자 영역 조회 실패는 구매자 맞춤 매칭에 영향을 주지 않는다.
    }
}

if (readCurrentUserId() !== null) {
    loadSellerMatchingSection();
}

/**
 * 메인페이지 상품 미리보기 카드 한 건을 생성한다.
 */
function createProductCard(product) {
    const card = document.createElement('article');
    card.className = 'main-product-card';

    const imageArea = document.createElement('div');
    imageArea.className = 'main-product-image';

    if (product.imageUrl) {
        const image = document.createElement('img');

        image.src = product.imageUrl;
        image.alt = `${product.productName ?? '상품'} 이미지`;

        imageArea.appendChild(image);
    } else {
        const placeholder = document.createElement('span');

        placeholder.className = 'main-product-placeholder';
        placeholder.textContent =
            product.productName?.charAt(0) ?? '상품';

        imageArea.appendChild(placeholder);
    }

    const content = document.createElement('div');
    content.className = 'main-product-content';

    const title = document.createElement('h3');
    title.className = 'main-product-title';
    title.textContent =
        product.productName ?? '상품명 없음';

    const meta = document.createElement('div');
    meta.className = 'main-product-meta';

    const category = document.createElement('span');
    category.textContent =
        product.category ?? '카테고리 없음';

    const price = document.createElement('span');
    price.className = 'main-product-price';
    price.textContent =
        `1인분 ${formatMoney(product.servingPrice)}원`;

    meta.append(category, price);

    const rating = document.createElement('div');
    rating.className = 'main-product-rating';

    rating.textContent =
        product.ratingAvg != null
            ? `평점 ${Number(product.ratingAvg).toFixed(1)}`
            : '아직 평점이 없습니다.';

    content.append(title, meta, rating);
    card.append(imageArea, content);

// 상품 카드 전체를 클릭하면 상세 페이지로 이동한다.
    if (product.id != null) {
        const detailUrl =
            `/product/detail?id=${encodeURIComponent(product.id)}`;

        card.style.cursor = 'pointer';
        card.tabIndex = 0;
        card.setAttribute('role', 'link');

        card.addEventListener('click', () => {
            window.location.href = detailUrl;
        });

        card.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                window.location.href = detailUrl;
            }
        });
    }

    return card;
}

/**
 * 등록 상품 중 평점과 최신순을 기준으로 미리보기를 표시한다.
 */
async function loadProductPreview() {
    try {
        const response =
            await fetch('/api/v1/products/search');

        if (!response.ok) {
            throw new Error(
                '상품을 불러오지 못했습니다.'
            );
        }

        const products =
            await response.json();

        productLoading.hidden = true;

        if (!Array.isArray(products)
            || products.length === 0) {

            productMessage.textContent =
                '현재 등록된 상품이 없습니다.';

            productMessage.hidden = false;
            return;
        }

        const previewProducts = [...products]
            .sort((left, right) => {
                const ratingDifference =
                    Number(right.ratingAvg ?? 0)
                    - Number(left.ratingAvg ?? 0);

                if (ratingDifference !== 0) {
                    return ratingDifference;
                }

                return new Date(right.updatedAt).getTime()
                    - new Date(left.updatedAt).getTime();
            })
            .slice(0, 3);

        productCards.replaceChildren();

        previewProducts.forEach(product => {
            productCards.appendChild(
                createProductCard(product)
            );
        });

        productCards.hidden = false;
        productActions.hidden = false;

    } catch (error) {
        productLoading.hidden = true;

        productMessage.textContent =
            error.message
            ?? '상품을 불러오지 못했습니다.';

        productMessage.hidden = false;
    }
}

loadMatchingPreview();
loadProductPreview();