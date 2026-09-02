import {authFetch, readApiBody, readCurrentUserId} from '/account/js/auth-client.js';

/**
 * 쿼리스트링의 id로 판매 조건 상세 정보를 조회해 화면에 채운다.
 * "수정하기" 버튼은 이 판매 조건을 등록한 본인에게만 보인다.
 * "바로 채팅하기" 버튼은 본인 상품이 아닐 때만 보인다.
 */
const statusBox = document.getElementById('statusBox');
const detailContent = document.getElementById('detailContent');
const imageHolder = document.getElementById('imageHolder');
const estimateButton = document.getElementById('estimateButton');
const editButton = document.getElementById('editButton');
const chatButton = document.getElementById('chatButton'); // [추가]
const reviewsButton = document.getElementById('reviewsButton');
const reportButton = document.getElementById('reportButton');

const dayOfWeekMap = {
    MONDAY: '월요일',
    TUESDAY: '화요일',
    WEDNESDAY: '수요일',
    THURSDAY: '목요일',
    FRIDAY: '금요일',
    SATURDAY: '토요일',
    SUNDAY: '일요일'
};

function getIdFromQuery() {
    return new URLSearchParams(window.location.search).get('id');
}

function formatUnavailableDates(unavailableDates) {
    if (!Array.isArray(unavailableDates) || unavailableDates.length === 0) {
        return '-';
    }

    return unavailableDates.join(', ');
}

function formatMoney(value) {
    return value != null ? `${Number(value).toLocaleString()}원` : '-';
}

function formatNumber(value, suffix = '') {
    return value != null ? `${value}${suffix}` : '-';
}

function renderImage(imageUrl, productName) {
    if (!imageUrl) {
        imageHolder.innerHTML = `<div class="product-detail-image-placeholder">이미지 없음</div>`;
        return;
    }

    imageHolder.innerHTML = `<img src="${encodeURI(imageUrl)}" alt="${productName || '상품 이미지'}">`;
}

async function isOwnedProduct(productId) {
    if (readCurrentUserId() === null) {
        return false;
    }

    try {
        const response = await authFetch('/api/v1/products/mine');
        if (!response.ok) {
            return false;
        }

        const products = await readApiBody(response);
        return Array.isArray(products)
            && products.some(product => Number(product.id) === Number(productId));
    } catch {
        return false;
    }
}

// [추가] "바로 채팅하기" 클릭 핸들러
async function startChatWithSeller(productId) {
    if (readCurrentUserId() === null) {
        location.href = '/login';
        return;
    }

    chatButton.disabled = true;
    chatButton.textContent = '채팅방 여는 중...';

    try {
        const response = await authFetch('/api/v1/chat-rooms', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ productId, originType: 'INQUIRY' })
        });

        if (!response.ok) {
            throw new Error('채팅방을 여는 데 실패했습니다.');
        }

        const room = await readApiBody(response);
        location.href = `/chat?roomId=${room.chatRoomId}`;
    } catch (error) {
        alert(error.message);
        chatButton.disabled = false;
        chatButton.textContent = '💬 바로 채팅하기';
    }
}

async function loadDetail() {
    const id = getIdFromQuery();
    if (!id) {
        statusBox.textContent = '상세를 확인할 상품 ID가 없습니다.';
        statusBox.classList.add('is-error');
        return;
    }

    try {
        const response = await authFetch(`/api/v1/products/${id}`);
        if (!response.ok) {
            if (response.status === 404) {
                throw new Error('존재하지 않는 판매 조건입니다.');
            }
            throw new Error('상세 정보를 불러오지 못했습니다.');
        }

        const product = await readApiBody(response);

        document.getElementById('productName').textContent = product.productName ?? '-';
        document.getElementById('minHeadcount').textContent =
            formatNumber(product.minHeadcount, '인분');

        document.getElementById('maxHeadcount').textContent =
            formatNumber(product.maxHeadcount, '인분');
        document.getElementById('servingPrice').textContent = formatMoney(product.servingPrice);
        document.getElementById('deliveryRadiusKm').textContent =
            product.deliveryRadiusKm != null
                ? `최대 ${product.deliveryRadiusKm}km`
                : '-';
        document.getElementById('storeAddress').textContent = product.storeAddress ?? '-';
        document.getElementById('category').textContent = product.category ?? '-';
        document.getElementById('description').textContent = product.description ?? '-';
        document.getElementById('dayOfWeek').textContent = dayOfWeekMap[product.dayOfWeek] ?? '없음';
        document.getElementById('ratingAvg').textContent =
            product.ratingAvg != null
                ? `★ ${product.ratingAvg.toFixed(1)}`
                : '평점 없음';
        document.getElementById('unavailableDates').textContent = formatUnavailableDates(product.unavailableDates);
        document.getElementById('updatedAt').textContent = product.updatedAt ? new Date(product.updatedAt).toLocaleString() : '-';

        renderImage(product.imageUrl, product.productName);

        const params = new URLSearchParams({
            itemName: product.productName ?? '',
            productId: product.id
        });
        estimateButton.href = `/estimates/new?${params.toString()}`;
        reviewsButton.href = `/reviews/by-product/${product.id}`;
        reportButton.href = `/mypage/reports?${new URLSearchParams({
            targetType: 'PRODUCT',
            targetId: product.id,
        })}`;

        // [수정] isOwnedProduct를 한 번만 호출해서 editButton/chatButton 둘 다에 사용
        const owned = await isOwnedProduct(product.id);

        if (owned) {
            editButton.href = `/product/update?id=${product.id}`;
            editButton.style.display = '';
            chatButton.style.display = 'none'; // [추가] 본인 상품과는 채팅할 수 없음
        } else {
            chatButton.addEventListener('click', () => startChatWithSeller(product.id)); // [추가]
        }

        statusBox.style.display = 'none';
        detailContent.style.display = 'grid';
    } catch (error) {
        statusBox.textContent = error.message;
        statusBox.classList.add('is-error');
    }
}

window.addEventListener('DOMContentLoaded', loadDetail);
