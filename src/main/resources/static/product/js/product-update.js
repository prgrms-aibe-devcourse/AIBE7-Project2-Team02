import {authFetch, readApiBody, readCurrentUserId} from '/account/js/auth-client.js';

/**
 * 판매 조건 ID로 기존 데이터를 조회해 폼에 채운 뒤, 수정 요청을 보낸다.
 */
const updateForm = document.getElementById('productUpdateForm');
const productNameInput = document.getElementById('productName');
const productIdInput = document.getElementById('productId');
const minHeadcountInput = document.getElementById('minHeadcount');
const maxHeadcountInput = document.getElementById('maxHeadcount');
const servingPriceInput = document.getElementById('servingPrice');
const deliveryRadiusKmInput = document.getElementById('deliveryRadiusKm');
const storeAddressInput =
    document.getElementById('storeAddress');

const storeAddressDetailInput =
    document.getElementById(
        'storeAddressDetail'
    );

const storeAddressSearchButton =
    document.getElementById('storeAddressSearchButton');
const categoryInput = document.getElementById('category');
const descriptionInput = document.getElementById('description');
const dayOfWeekInput = document.getElementById('dayOfWeek');
const unavailableDatesInput = document.getElementById('unavailableDates');
const imageFileInput = document.getElementById('imageFile');
const imagePreview = document.getElementById('imagePreview');
const submitBtn = document.getElementById('submitBtn');
const detailBackLink = document.getElementById('detailBackLink');

const messageBox = document.getElementById('messageBox');
const resultBox = document.getElementById('result');

const formFields = [
    productNameInput, minHeadcountInput, maxHeadcountInput, servingPriceInput,
    deliveryRadiusKmInput, storeAddressInput, storeAddressDetailInput,
    categoryInput, descriptionInput,
    dayOfWeekInput, unavailableDatesInput, imageFileInput
];

if (readCurrentUserId() === null) {
    const redirect = encodeURIComponent(window.location.pathname);
    window.location.href = `/login?redirect=${redirect}`;
}

function showMessage(text, isSuccess) {
    messageBox.textContent = text;
    messageBox.className = 'product-form-message ' + (isSuccess ? 'is-success' : 'is-error');
}

function parseUnavailableDates(value) {
    return value
        .split(/[\n,]/)
        .map(date => date.trim())
        .filter(Boolean);
}

function updatePreview(url) {
    if (!url) {
        imagePreview.style.display = 'none';
        imagePreview.src = '';
        return;
    }

    imagePreview.src = url;
    imagePreview.style.display = 'block';
}

function setFormEnabled(enabled) {
    formFields.forEach(field => {
        field.disabled = !enabled;
    });

    storeAddressSearchButton.disabled =
        !enabled;

    submitBtn.disabled =
        !enabled;
}

/**
 * Kakao 우편번호 검색창을 열고 선택한 주소를 가게 주소에 입력한다.
 */
function openStoreAddressSearch() {
    if (
        typeof kakao === 'undefined'
        || !kakao.Postcode
    ) {
        alert(
            '주소 검색 서비스를 불러오지 못했습니다.'
        );
        return;
    }

    new kakao.Postcode({
        oncomplete(data) {
            const selectedAddress =
                data.roadAddress
                || data.jibunAddress
                || data.address;

            storeAddressInput.value =
                selectedAddress;

// 주소를 재검색하면 기존 상세 주소를 초기화한다.
            storeAddressDetailInput.value = '';
            storeAddressDetailInput.focus();
        }
    }).open();
}

storeAddressSearchButton.addEventListener(
    'click',
    openStoreAddressSearch
);

imageFileInput.addEventListener('change', () => {
    const file = imageFileInput.files?.[0];
    if (!file) {
        return;
    }

    updatePreview(URL.createObjectURL(file));
});

async function loadProductData(id) {
    if (!id) {
        showMessage('ID를 입력해 주세요.', false);
        return;
    }

    resultBox.textContent = '조회 중...';

    try {
        const response = await authFetch(`/api/v1/products/${id}`);
        if (!response.ok) {
            if (response.status === 404) {
                throw new Error('존재하지 않는 판매 조건 ID입니다.');
            }
            throw new Error('데이터를 가져오는 중 오류가 발생했습니다.');
        }

        const product = await readApiBody(response);

        productNameInput.value = product.productName ?? '';
        productIdInput.value = product.id;
        minHeadcountInput.value = product.minHeadcount;
        maxHeadcountInput.value = product.maxHeadcount;
        servingPriceInput.value = product.servingPrice ?? '';
        deliveryRadiusKmInput.value = product.deliveryRadiusKm;
        storeAddressInput.value = product.storeAddress;
        storeAddressDetailInput.value =
            product.storeAddressDetail ?? '';
        categoryInput.value = product.category;
        descriptionInput.value = product.description ?? '';
        dayOfWeekInput.value = product.dayOfWeek ?? '';
        unavailableDatesInput.value = Array.isArray(product.unavailableDates)
            ? product.unavailableDates.join('\n')
            : '';
        imageFileInput.value = '';
        updatePreview(product.imageUrl ? encodeURI(product.imageUrl) : null);

        setFormEnabled(true);
        messageBox.textContent = '';
        messageBox.className = 'product-form-message';

        resultBox.textContent = JSON.stringify(product, null, 2);

        detailBackLink.href = `/product/detail?id=${id}`;
        detailBackLink.style.display = 'inline-flex';
    } catch (error) {
        resultBox.textContent = error.message;
        showMessage(error.message, false);

        updateForm.reset();
        productNameInput.value = '';
        productIdInput.value = '';
        setFormEnabled(false);
        updatePreview(null);
        detailBackLink.style.display = 'none';
    }
}

updateForm.addEventListener('submit', async (event) => {
    event.preventDefault();

    const id = productIdInput.value;
    if (!id) {
        showMessage('수정할 판매 조건 정보가 로드되지 않았습니다.', false);
        return;
    }

    const requestData = {
        productName: productNameInput.value,
        minHeadcount: Number(minHeadcountInput.value),
        maxHeadcount: Number(maxHeadcountInput.value),
        servingPrice: Number(servingPriceInput.value),
        deliveryRadiusKm: Number(deliveryRadiusKmInput.value),
        storeAddress: storeAddressInput.value,
        storeAddressDetail: storeAddressDetailInput.value,
        category: categoryInput.value,
        description: descriptionInput.value,
        dayOfWeek: dayOfWeekInput.value || null,
        unavailableDates: parseUnavailableDates(unavailableDatesInput.value)
    };

    try {
        resultBox.textContent = '수정 요청 전송 중...';

        const formData = new FormData();
        formData.append('product', new Blob([JSON.stringify(requestData)], {type: 'application/json'}));

        const imageFile = imageFileInput.files?.[0];
        if (imageFile) {
            formData.append('imageFile', imageFile);
        }

        const response = await authFetch(`/api/v1/products/${id}`, {
            method: 'PATCH',
            body: formData
        });

        const data = await readApiBody(response);

        if (!response.ok) {
            throw new Error(
                data?.message ?? '수정에 실패했습니다.'
            );
        }

        window.location.href =
            `/product/detail?id=${id}`;

        resultBox.textContent = JSON.stringify(data, null, 2);
    } catch (error) {
        resultBox.textContent = error.message;
        showMessage(`수정 실패: ${error.message}`, false);
    }
});

window.addEventListener('DOMContentLoaded', () => {
    const idParam =
        new URLSearchParams(
            window.location.search
        ).get('id');

    if (!idParam) {
        window.location.href = '/product';
        return;
    }

    loadProductData(idParam);
});