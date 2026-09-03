import {authFetch, readApiBody, readCurrentUserId, readCurrentUserRole} from '/account/js/auth-client.js';

/**
 * 판매 조건 목록을 검색·조회하고, 게시판형 카드로 렌더링한다.
 * "새 판매 조건 등록" 버튼은 승인된 판매자에게만,
 * "숨김 처리" 버튼은 본인 소유(또는 관리자)에게만 보인다.
 */
const productList =
    document.getElementById('productList');

const searchForm =
    document.getElementById('searchForm');

const resetBtn =
    document.getElementById('resetBtn');

const quantityInput =
    document.getElementById('quantity');

const categoryInput =
    document.getElementById('category');

const servingPriceInput =
    document.getElementById('servingPrice');

const newProductButton =
    document.getElementById('newProductButton');

const productCount =
    document.getElementById('productCount');

const productPagination =
    document.getElementById('productPagination');

const PAGE_SIZE = 9;

let currentPage = 1;
let currentProducts = [];

const currentUserId =
    readCurrentUserId();

const currentUserRole =
    readCurrentUserRole();

const isSeller =
    currentUserRole === 'SELLER';

const dayOfWeekMap = {
    MONDAY: '월요일',
    TUESDAY: '화요일',
    WEDNESDAY: '수요일',
    THURSDAY: '목요일',
    FRIDAY: '금요일',
    SATURDAY: '토요일',
    SUNDAY: '일요일'
};

/**
 * 승인된 판매자에게 상품 등록 버튼을 표시한다.
 */
async function showRegisterButtonIfApprovedSeller() {
    if (
        currentUserId === null
        || !isSeller
        || !newProductButton
    ) {
        return;
    }

    try {
        const response =
            await authFetch('/api/v1/account/me');

        if (!response.ok) {
            return;
        }

        const account =
            await readApiBody(response);

        if (account?.sellerStatus === 'APPROVED') {
            newProductButton.style.display = '';
        }
    } catch {
        // 계정 정보를 못 가져와도 목록 조회 자체는 계속 진행한다.
    }
}

function formatMoney(value) {
    return value != null
        ? `${Number(value).toLocaleString()}원`
        : '-';
}

function formatUnavailableDates(unavailableDates) {
    if (
        !Array.isArray(unavailableDates)
        || unavailableDates.length === 0
    ) {
        return '-';
    }

    return unavailableDates.join(', ');
}

function buildSearchParams() {
    const params =
        new URLSearchParams();

    const quantity =
        quantityInput.value.trim();

    const category =
        categoryInput.value.trim();

    const servingPrice =
        servingPriceInput.value.trim();

    if (quantity) {
        params.set('quantity', quantity);
    }

    if (category) {
        params.set('category', category);
    }

    if (servingPrice) {
        params.set(
            'servingPrice',
            servingPrice
        );
    }

    return params;
}

function renderEmpty(message) {
    productList.innerHTML =
        `<p>${message}</p>`;
}

function renderItems(items) {
    currentProducts =
        Array.isArray(items)
            ? items
            : [];

    const totalPages =
        Math.ceil(
            currentProducts.length
            / PAGE_SIZE
        );

    if (currentPage > totalPages) {
        currentPage =
            totalPages || 1;
    }

    productCount.textContent =
        `총 ${currentProducts.length}개의 상품`;

    if (!currentProducts.length) {
        renderEmpty(
            '조건에 맞는 상품이 없습니다.'
        );

        productPagination.innerHTML = '';

        return;
    }

    const start =
        (currentPage - 1)
        * PAGE_SIZE;

    const pageItems =
        currentProducts.slice(
            start,
            start + PAGE_SIZE
        );

    productList.innerHTML =
        pageItems.map(product => `
            <article class="product-item">
                <a
                    class="product-item-image"
                    href="/product/detail?id=${product.id}"
                >
                    ${
            product.imageUrl
                ? `
                                <img
                                    src="${encodeURI(product.imageUrl)}"
                                    alt="${product.productName ?? '상품 이미지'}"
                                >
                            `
                : `
                                <div class="product-item-image-placeholder">
                                    <span>이미지 없음</span>
                                </div>
                            `
        }
                </a>

                <div class="product-item-body">
                    <div class="product-item-category">
                        ${product.category ?? '카테고리 없음'}
                    </div>

                    <div class="product-item-title-row">
                        <h2>
                            <a href="/product/detail?id=${product.id}">
                                ${product.productName ?? '(상품명 없음)'}
                            </a>
                        </h2>

                        ${
            product.ratingAvg != null
                ? `
                                    <span class="product-rating">
                                        ★ ${Number(product.ratingAvg).toFixed(1)}
                                    </span>
                                `
                : ''
        }
                    </div>

                    <div class="product-item-bottom">
                        <div class="product-item-price">
                            <strong>
                                ${formatMoney(product.servingPrice)}
                            </strong>
                            <span>/인분</span>
                        </div>

                        <span class="product-min-order">
                            최소 ${product.minHeadcount ?? '-'}인분
                        </span>
                    </div>
                </div>
            </article>
        `).join('');

    renderPagination(totalPages);
}

/**
 * 상품 목록의 페이지 이동 버튼을 렌더링한다.
 */
function renderPagination(totalPages) {
    if (totalPages <= 1) {
        productPagination.innerHTML = '';
        return;
    }

    let html = `
        <button
            type="button"
            data-page="${currentPage - 1}"
            ${currentPage === 1 ? 'disabled' : ''}
        >
            ‹
        </button>
    `;

    for (
        let page = 1;
        page <= totalPages;
        page++
    ) {
        html += `
            <button
                type="button"
                class="${page === currentPage ? 'is-active' : ''}"
                data-page="${page}"
            >
                ${page}
            </button>
        `;
    }

    html += `
        <button
            type="button"
            data-page="${currentPage + 1}"
            ${currentPage === totalPages ? 'disabled' : ''}
        >
            ›
        </button>
    `;

    productPagination.innerHTML =
        html;
}

/**
 * 공개 상품 목록을 조회한다.
 */
async function fetchProducts() {
    try {
        renderEmpty(
            '데이터를 불러오는 중입니다...'
        );

        const params =
            buildSearchParams();

        const query =
            params.toString();

        const response =
            await fetch(
                `/api/v1/products/search${
                    query
                        ? `?${query}`
                        : ''
                }`
            );

        if (!response.ok) {
            throw new Error(
                '판매 조건 목록을 불러오지 못했습니다.'
            );
        }

        const data =
            await response.json();

        currentPage = 1;

        renderItems(data ?? []);
    } catch (error) {
        console.error(
            '상품 목록 조회 실패:',
            error
        );

        renderEmpty(
            error?.message
            ?? '상품을 불러오지 못했습니다.'
        );
    }
}

searchForm.addEventListener(
    'submit',
    event => {
        event.preventDefault();

        fetchProducts();
    }
);

resetBtn.addEventListener(
    'click',
    () => {
        searchForm.reset();

        fetchProducts();
    }
);

productPagination.addEventListener(
    'click',
    event => {
        const button =
            event.target.closest(
                '[data-page]'
            );

        if (
            !button
            || button.disabled
        ) {
            return;
        }

        currentPage =
            Number(
                button.dataset.page
            );

        renderItems(currentProducts);

        document.querySelector(
            '.product-list-section'
        )?.scrollIntoView({
            behavior: 'smooth',
            block: 'start'
        });
    }
);

/**
 * 상품 목록 화면의 초기 데이터를 불러옵니다.
 */
async function initialize() {
    await fetchProducts();

    if (isSeller) {
        await showRegisterButtonIfApprovedSeller();
    }
}

initialize();