import {authFetch, readApiBody, readCurrentUserId} from '/account/js/auth-client.js';

/**
 * Proposal 작성 화면에서 등록 상품 선택, 직접 입력 전환,
 * 제안 금액 계산과 API 전송을 처리한다.
 */

const form = document.getElementById('proposalForm');

if (form) {
    const requestId = form.dataset.requestId;
    let buyerId;
    let orderQuantity;

    const currentUserId = readCurrentUserId();

    const modeButtons =
        document.querySelectorAll('[data-proposal-mode]');

    const registeredProductArea =
        document.getElementById('registeredProductArea');

    const registeredProductSelect =
        document.getElementById('registeredProduct');

    const registeredProductHint =
        document.getElementById('registeredProductHint');

    const itemNameInput =
        document.getElementById('itemName');

    const quantityInput =
        document.getElementById('quantity');

    const unitPriceInput =
        document.getElementById('unitPrice');

    const totalAmountInput =
        document.getElementById('totalAmount');

    const preparationDaysInput =
        document.getElementById('preparationDays');

    const descriptionInput =
        document.getElementById('description');

    const productPreview =
        document.getElementById('productPreview');

    const productPreviewImage =
        document.getElementById('productPreviewImage');

    const productPreviewName =
        document.getElementById('productPreviewName');

    const productPreviewDescription =
        document.getElementById('productPreviewDescription');

    let mode = 'product';
    let products = [];

    /**
     * 현재 수량과 단가를 이용해 기본 총액을 계산한다.
     */
    function calculateTotalAmount() {
        const quantity = Number(quantityInput.value);
        const unitPrice = Number(unitPriceInput.value);

        if (quantity > 0 && unitPrice > 0) {
            totalAmountInput.value =
                quantity * unitPrice;
        }
    }

    /**
     * 등록 상품 미리보기를 초기화한다.
     */
    function clearProductPreview() {
        productPreview.hidden = true;

        productPreviewImage.hidden = true;
        productPreviewImage.removeAttribute('src');

        productPreviewName.textContent = '';
        productPreviewDescription.textContent = '';
    }

    /**
     * Proposal 입력 필드를 초기화한다.
     */
    function clearProposalFields() {
        itemNameInput.value = '';
        unitPriceInput.value = '';
        totalAmountInput.value = '';
        descriptionInput.value = '';

        clearProductPreview();
    }

    /**
     * 등록 상품 정보를 Proposal 작성 폼에 채운다.
     */
    function applyProduct(product) {
        itemNameInput.value =
            product.productName ?? '';

        unitPriceInput.value =
            product.servingPrice ?? '';

        descriptionInput.value =
            product.description ?? '';

        productPreviewName.textContent =
            product.productName ?? '';

        productPreviewDescription.textContent =
            product.description || '등록된 상품 설명이 없습니다.';

        if (product.imageUrl) {
            productPreviewImage.src =
                product.imageUrl;

            productPreviewImage.hidden = false;
        } else {
            productPreviewImage.hidden = true;
        }

        productPreview.hidden = false;

        calculateTotalAmount();
    }

    /**
     * 등록 상품 방식과 직접 입력 방식을 전환한다.
     */
    function changeMode(nextMode) {
        mode = nextMode;

        modeButtons.forEach(button => {
            button.classList.toggle(
                'is-active',
                button.dataset.proposalMode === mode
            );
        });

        const productMode =
            mode === 'product';

        registeredProductArea.hidden =
            !productMode;

        itemNameInput.readOnly =
            productMode;

        unitPriceInput.readOnly =
            productMode;

        registeredProductSelect.value = '';

        clearProposalFields();
    }

    /**
     * 현재 로그인 판매자가 등록한 상품을 불러온다.
     */
    async function loadProducts() {
        const response =
            await authFetch('/api/v1/products/mine');

        if (response.status === 401) {
            const redirect =
                encodeURIComponent(window.location.pathname);

            window.location.href =
                `/login?redirect=${redirect}`;

            return;
        }

        if (!response.ok) {
            const body =
                await readApiBody(response);

            alert(
                body?.message ??
                '등록 상품을 불러오지 못했습니다.'
            );

            return;
        }

        products = await readApiBody(response) ?? [];

        registeredProductSelect.innerHTML =
            '<option value="">상품을 선택해주세요</option>';

        products.forEach(product => {
            const option =
                document.createElement('option');

            option.value =
                product.id;

            option.textContent =
                `${product.productName} - ${Number(
                    product.servingPrice
                ).toLocaleString()}원`;

            registeredProductSelect.appendChild(option);
        });

        if (products.length === 0) {
            registeredProductHint.textContent =
                '등록된 상품이 없습니다. 직접 입력하여 제안해주세요.';

            changeMode('manual');
        }
    }

    /**
     * 현재 사용자가 Proposal 화면을 이용할 수 있는지 검사한다.
     */
    async function initialize() {
        if (currentUserId === null) {
            const redirect =
                encodeURIComponent(window.location.pathname);

            window.location.href =
                `/login?redirect=${redirect}`;

            return;
        }

        const orderResponse = await authFetch(`/api/v1/requests/${requestId}`);
        if (!orderResponse.ok) {
            const body = await readApiBody(orderResponse);
            alert(body?.message ?? '주문 정보를 불러올 수 없습니다.');
            window.location.href = '/requests';
            return;
        }
        const order = await orderResponse.json();
        buyerId = Number(order.buyerId);
        orderQuantity = Number(order.quantity);
        quantityInput.value = orderQuantity;
        document.querySelector('[data-order-summary="title"]').textContent = order.title || '제목 없음';
        document.querySelector('[data-order-summary="quantity"]').textContent = orderQuantity;
        document.querySelector('[data-order-summary="budgetType"]').textContent =
            order.budgetType === 'PER_PERSON' ? '1인당' : '총 예산';
        document.querySelector('[data-order-summary="budget"]').textContent =
            Number(order.budget || 0).toLocaleString('ko-KR');
        document.querySelector('[data-order-summary="category"]').textContent = order.category || '-';

        if (currentUserId === buyerId) {
            alert(
                '본인이 등록한 주문에는 제안할 수 없습니다.'
            );

            window.location.href =
                `/requests/${requestId}`;

            return;
        }

        const eligibilityResponse =
            await authFetch('/api/v1/proposals/eligibility');

        if (!eligibilityResponse.ok) {
            const body =
                await readApiBody(eligibilityResponse);

            alert(
                body?.message ??
                '승인된 판매자만 제안을 작성할 수 있습니다.'
            );

            window.location.href =
                `/requests/${requestId}`;

            return;
        }

        await loadProducts();
        form.hidden = false;
    }

    modeButtons.forEach(button => {
        button.addEventListener('click', () => {
            changeMode(
                button.dataset.proposalMode
            );
        });
    });

    registeredProductSelect.addEventListener(
        'change',
        () => {
            const productId =
                Number(registeredProductSelect.value);

            const product =
                products.find(
                    item => item.id === productId
                );

            if (!product) {
                clearProposalFields();
                return;
            }

            applyProduct(product);
        }
    );

    quantityInput.addEventListener(
        'input',
        calculateTotalAmount
    );

    unitPriceInput.addEventListener(
        'input',
        calculateTotalAmount
    );

    form.addEventListener('submit', async event => {
        event.preventDefault();

        if (!form.reportValidity()) {
            return;
        }

        if (
            mode === 'product' &&
            !registeredProductSelect.value
        ) {
            alert('제안에 사용할 상품을 선택해주세요.');
            return;
        }

        const payload = {
            productId:
                mode === 'product'
                    ? Number(registeredProductSelect.value)
                    : null,

            itemName:
                itemNameInput.value.trim(),

            quantity:
                Number(quantityInput.value),

            unitPrice:
                Number(unitPriceInput.value),

            totalAmount:
                Number(totalAmountInput.value),

            preparationDays:
                Number(preparationDaysInput.value),

            description:
                descriptionInput.value.trim()
        };

        const response =
            await authFetch(
                `/api/v1/requests/${requestId}/proposals`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type':
                            'application/json'
                    },
                    body:
                        JSON.stringify(payload)
                }
            );

        if (response.status === 401) {
            const redirect =
                encodeURIComponent(window.location.pathname);

            window.location.href =
                `/login?redirect=${redirect}`;

            return;
        }

        const body =
            await readApiBody(response);

        if (!response.ok) {
            alert(
                body?.message ??
                '제안을 전송하지 못했습니다.'
            );

            return;
        }

        alert('제안이 전송되었습니다.');

        const chatResponse =
            await authFetch(
                '/api/v1/chat-rooms',
                {
                    method: 'POST',
                    headers: {
                        'Content-Type':
                            'application/json'
                    },
                    body: JSON.stringify({
                        orderRequestId: Number(requestId),
                        proposalId: body.id,
                        originType: 'PROPOSAL'
                    })
                }
            );

        const chatRoom =
            await readApiBody(chatResponse);

        if (!chatResponse.ok) {
            alert(
                chatRoom?.message
                ?? '제안은 전송되었지만 채팅방을 열지 못했습니다.'
            );

            window.location.href =
                `/requests/${requestId}`;

            return;
        }

        window.location.href =
            `/chat?roomId=${chatRoom.chatRoomId}`;
    });

    initialize();
}
