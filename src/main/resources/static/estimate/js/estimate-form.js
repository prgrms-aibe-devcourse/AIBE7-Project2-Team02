import {authFetch, readApiBody, readCurrentUserId} from '/account/js/auth-client.js';

/**
 * 구매자의 견적 요청 작성 화면에서 상품 조회,
 * 예산 안내, 주소 검색과 견적 요청 전송을 처리합니다.
 */

const form =
    document.getElementById('estimateForm');

const productSummary =
    document.getElementById('productSummary');

const missingProductNotice =
    document.getElementById('missingProductNotice');

const itemNameInput =
    document.getElementById('itemName');

const quantityInput =
    document.getElementById('quantity');

const quantityHint =
    document.getElementById('quantityHint');

const budgetTypeInput =
    document.getElementById('budgetType');

const budgetInput =
    document.getElementById('budget');

const budgetGuide =
    document.getElementById('budgetGuide');

const eventDateTimeInput =
    document.getElementById('eventDateTime');

const deliveryAddressInput =
    document.getElementById('deliveryAddress');

const deliveryAddressDetailInput =
    document.getElementById(
        'deliveryAddressDetail'
    );

const addressSearchButton =
    document.getElementById('addressSearchButton');

const descriptionInput =
    document.getElementById('description');

const submitButton =
    document.getElementById('estimateSubmitButton');

const cancelButton =
    document.getElementById('estimateCancelButton');

const errorBox =
    document.getElementById('estimateError');

const currentUserId =
    readCurrentUserId();

const params =
    new URLSearchParams(window.location.search);

const productId =
    params.get('productId')
        ? Number(params.get('productId'))
        : null;


/**
 * 숫자를 원화 표시용 문자열로 변환합니다.
 */
function formatMoney(value) {
    if (value == null) {
        return '-';
    }

    return Number(value)
        .toLocaleString('ko-KR');
}


/**
 * 선택 상품 정보를 화면에 표시합니다.
 */
function renderProduct(product) {
    const productName =
        product.productName
        ?? params.get('itemName')
        ?? '상품명 없음';

    itemNameInput.value =
        productName;

    document
        .querySelector(
            '[data-product-summary="name"]'
        )
        .textContent =
        productName;

    document
        .querySelector(
            '[data-product-summary="category"]'
        )
        .textContent =
        product.category ?? '-';

    document
        .querySelector(
            '[data-product-summary="price"]'
        )
        .textContent =
        formatMoney(product.servingPrice);

    const minHeadcount =
        product.minHeadcount;

    const maxHeadcount =
        product.maxHeadcount;

    const headcountText =
        minHeadcount != null
        && maxHeadcount != null
            ? `${minHeadcount}~${maxHeadcount}인분`
            : '-';

    document
        .querySelector(
            '[data-product-summary="headcount"]'
        )
        .textContent =
        headcountText;

    if (
        minHeadcount != null
        && maxHeadcount != null
    ) {
        quantityHint.textContent =
            `이 상품은 ${minHeadcount}~${maxHeadcount}인분 주문이 가능합니다.`;
    }

    cancelButton.href =
        `/product/detail?id=${productId}`;

    document
        .getElementById('productDetailLink')
        .href =
        `/product/detail?id=${productId}`;

    productSummary.hidden = false;
    form.hidden = false;
}


/**
 * 선택 상품 정보를 서버에서 조회합니다.
 */
async function loadProduct() {
    if (!productId) {
        missingProductNotice.hidden = false;
        return;
    }

    const response =
        await authFetch(
            `/api/v1/products/${productId}`
        );

    if (!response.ok) {
        const body =
            await readApiBody(response)
                .catch(() => null);

        showError(
            body?.message
            ?? '상품 정보를 불러오지 못했습니다.'
        );

        return;
    }

    const product =
        await readApiBody(response);

    renderProduct(product);
}


/**
 * 예산 유형과 수량을 기준으로 예상 금액을 안내합니다.
 */
function updateBudgetGuide() {
    const budget =
        Number(budgetInput.value);

    const quantity =
        Number(quantityInput.value);

    if (
        !budgetTypeInput.value
        || budget <= 0
        || quantity <= 0
    ) {
        budgetGuide.textContent =
            '예산 유형과 수량을 입력하면 예상 금액을 확인할 수 있습니다.';

        return;
    }

    if (
        budgetTypeInput.value
        === 'PER_PERSON'
    ) {
        const total =
            budget * quantity;

        budgetGuide.textContent =
            `예상 총 예산은 ${formatMoney(total)}원입니다.`;

        return;
    }

    const perPerson =
        Math.floor(
            budget / quantity
        );

    budgetGuide.textContent =
        `1인당 약 ${formatMoney(perPerson)}원입니다.`;
}


/**
 * 카카오 우편번호 검색을 열어 배송 주소를 입력합니다.
 */
function openAddressSearch() {
    if (
        typeof daum === 'undefined'
        || !daum.Postcode
    ) {
        alert(
            '주소 검색 기능을 불러오지 못했습니다.'
        );

        return;
    }

    new daum.Postcode({
        oncomplete(data) {
            deliveryAddressInput.value =
                data.roadAddress
                || data.jibunAddress
                || '';

// 주소를 다시 선택한 경우 기존 상세 주소가
// 다른 장소의 정보로 남지 않도록 초기화한다.
            deliveryAddressDetailInput.value = '';
            deliveryAddressDetailInput.focus();
        }
    }).open();
}


/**
 * 견적 작성 화면 오류를 표시합니다.
 */
function showError(message) {
    errorBox.textContent =
        message;

    errorBox.hidden = false;
}


/**
 * 현재 오류 표시를 초기화합니다.
 */
function clearError() {
    errorBox.textContent = '';
    errorBox.hidden = true;
}


/**
 * 견적 요청을 서버에 전송합니다.
 */
async function submitEstimate(event) {
    event.preventDefault();

    clearError();

    if (!form.reportValidity()) {
        return;
    }

    if (!productId) {
        showError(
            '견적을 요청할 상품 정보가 없습니다.'
        );

        return;
    }

    const requestData = {
        description:
            descriptionInput.value.trim()
            || null,

        productId,

        budget:
            Number(budgetInput.value),

        budgetType:
        budgetTypeInput.value,

        itemName:
        itemNameInput.value,

        quantity:
            Number(quantityInput.value),

        eventDateTime:
        eventDateTimeInput.value,

        deliveryAddress:
            deliveryAddressInput.value.trim(),

        deliveryAddressDetail:
            deliveryAddressDetailInput.value.trim(),

// 현재 Estimate API는 이미지 URL 문자열만 지원하므로
        // 실제 파일 첨부 기능을 구현하기 전까지는 전송하지 않습니다.
        estimateImage: null
    };

    submitButton.disabled = true;
    submitButton.textContent =
        '요청 보내는 중...';

    try {
        const response =
            await authFetch(
                '/api/v1/estimates',
                {
                    method: 'POST',
                    headers: {
                        'Content-Type':
                            'application/json'
                    },
                    body:
                        JSON.stringify(
                            requestData
                        )
                }
            );

        if (response.status === 401) {
            redirectToLogin();
            return;
        }

        const body =
            await readApiBody(response);

        if (!response.ok) {
            throw new Error(
                body?.message
                ?? '견적 요청을 보내지 못했습니다.'
            );
        }

        alert(
            '견적 요청이 전송되었습니다.'
        );

        window.location.href =
            '/mypage/buying-estimates';

    } catch (error) {
        showError(
            error.message
            ?? '견적 요청 중 문제가 발생했습니다.'
        );

        submitButton.disabled = false;
        submitButton.textContent =
            '견적 요청 보내기';
    }
}


/**
 * 로그인 화면으로 이동합니다.
 */
function redirectToLogin() {
    const redirect =
        encodeURIComponent(
            window.location.pathname
            + window.location.search
        );

    window.location.href =
        `/login?redirect=${redirect}`;
}


/**
 * 견적 요청 작성 화면을 초기화합니다.
 */
async function initialize() {
    if (currentUserId === null) {
        redirectToLogin();
        return;
    }

    await loadProduct();
}


budgetTypeInput.addEventListener(
    'change',
    updateBudgetGuide
);

budgetInput.addEventListener(
    'input',
    updateBudgetGuide
);

quantityInput.addEventListener(
    'input',
    updateBudgetGuide
);

addressSearchButton.addEventListener(
    'click',
    openAddressSearch
);

form.addEventListener(
    'submit',
    submitEstimate
);

initialize();