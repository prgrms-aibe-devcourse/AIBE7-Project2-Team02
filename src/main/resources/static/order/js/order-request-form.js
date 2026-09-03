import {authFetch, readApiBody, readCurrentUserId} from '/account/js/auth-client.js';

/**
 * 주문 등록/수정 폼을 JWT 인증 API와 연결한다.
 */
const form =
    document.getElementById('orderRequestForm');

if (form) {
    const currentUserId =
        readCurrentUserId();

    const mode =
        form.dataset.mode;

    const requestId =
        form.dataset.requestId;

    const addressInput =
        document.getElementById(
            'deliveryAddress'
        );

    const addressDetailInput =
        document.getElementById(
            'deliveryAddressDetail'
        );

    const addressSearchButton =
        document.getElementById(
            'addressSearchButton'
        );

    const imageFileInput =
        document.getElementById('imageFile');

    const imagePreviewArea =
        document.getElementById('imagePreviewArea');

    const imagePreview =
        document.getElementById('imagePreview');

    let existingImageUrl = null;
    let previewObjectUrl = null;

    // 주문 등록과 수정은 로그인 사용자만 이용한다.
    if (currentUserId === null) {
        const redirect =
            encodeURIComponent(
                window.location.pathname
            );

        window.location.href =
            `/login?redirect=${redirect}`;
    } else if (mode === 'edit') {
        const response =
            await authFetch(
                `/api/v1/requests/${requestId}`
            );

        if (response.status === 401) {
            const redirect =
                encodeURIComponent(
                    window.location.pathname
                );

            window.location.href =
                `/login?redirect=${redirect}`;

            throw new Error(
                'Redirecting to login'
            );
        }

        if (!response.ok) {
            alert(
                '주문 정보를 불러올 수 없습니다.'
            );

            window.location.href =
                '/requests';

            throw new Error(
                'Unable to load order'
            );
        }

        const order =
            await response.json();

        if (
            currentUserId
            !== Number(order.buyerId)
        ) {
            alert(
                '본인이 등록한 주문만 수정할 수 있습니다.'
            );

            window.location.href =
                `/requests/${requestId}`;

            throw new Error(
                'Forbidden order edit'
            );
        }

        populateEditForm(order);

        form.hidden = false;
    } else {
        form.hidden = false;
    }

    const categorySelect =
        document.getElementById('category');

    const customCategoryArea =
        document.getElementById(
            'customCategoryArea'
        );

    const customCategoryInput =
        document.getElementById(
            'customCategory'
        );

    const standardCategories = [
        '한식',
        '중식',
        '일식',
        '양식',
        '도시락/간편식',
        '디저트/다과',
        '카페/음료',
        '비건'
    ];

    // 수정 화면에서 기존 값이 직접 입력 카테고리라면 "기타" 입력란을 연다.
    const originalCategory =
        categorySelect.dataset.originalCategory;

    if (
        originalCategory
        && !standardCategories.includes(
            originalCategory
        )
    ) {
        categorySelect.value = '기타';

        customCategoryInput.value =
            originalCategory;

        customCategoryInput.required =
            true;

        customCategoryArea.hidden =
            false;
    }

    categorySelect.addEventListener(
        'change',
        () => {
            const isOther =
                categorySelect.value === '기타';

            customCategoryArea.hidden =
                !isOther;

            customCategoryInput.required =
                isOther;

            if (!isOther) {
                customCategoryInput.value =
                    '';
            }
        }
    );

    addressSearchButton.addEventListener(
        'click',
        openAddressSearch
    );

    imageFileInput.addEventListener(
        'change',
        () => {
            const file =
                imageFileInput.files?.[0];

            updateImagePreview(file);
        }
    );

    form.addEventListener(
        'submit',
        async event => {
            event.preventDefault();

            if (!form.reportValidity()) {
                return;
            }

            const category =
                categorySelect.value === '기타'
                    ? customCategoryInput
                        .value
                        .trim()
                    : categorySelect.value;

            if (!category) {
                alert(
                    '음식 카테고리를 입력해주세요.'
                );

                return;
            }

            const payload = {
                title:
                    form.elements
                        .namedItem('title')
                        .value
                        .trim(),

                description:
                    form.elements
                        .namedItem('description')
                        .value
                        .trim(),

                eventDateTime:
                form.elements
                    .namedItem('eventDateTime')
                    .value,

                quantity:
                    Number(
                        form.elements
                            .namedItem('quantity')
                            .value
                    ),

                budgetType:
                form.elements
                    .namedItem('budgetType')
                    .value,

                budget:
                    Number(
                        form.elements
                            .namedItem('budget')
                            .value
                    ),

                category,

                deliveryAddress:
                    form.elements
                        .namedItem(
                            'deliveryAddress'
                        )
                        .value
                        .trim(),

                deliveryAddressDetail:
                    form.elements
                        .namedItem(
                            'deliveryAddressDetail'
                        )
                        .value
                        .trim()
            };

            const formData =
                new FormData();

            formData.append(
                'request',
                new Blob(
                    [
                        JSON.stringify(
                            payload
                        )
                    ],
                    {
                        type:
                            'application/json'
                    }
                )
            );

            const imageFile =
                imageFileInput.files?.[0];

            if (imageFile) {
                formData.append(
                    'imageFile',
                    imageFile
                );
            }

            const url =
                mode === 'edit'
                    ? `/api/v1/requests/${requestId}`
                    : '/api/v1/requests';

            const method =
                mode === 'edit'
                    ? 'PATCH'
                    : 'POST';

            const response =
                await authFetch(
                    url,
                    {
                        method,
                        body: formData
                    }
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

            if (response.status === 403) {
                alert(
                    '본인이 등록한 주문만 수정할 수 있습니다.'
                );

                return;
            }

            if (!response.ok) {
                const body =
                    await readApiBody(
                        response
                    );

                alert(
                    body?.message
                    ?? '주문 처리 중 문제가 발생했습니다.'
                );

                return;
            }

            const body =
                await readApiBody(
                    response
                );

            if (mode === 'edit') {
                window.location.href =
                    `/requests/${requestId}`;
            } else {
                window.location.href =
                    `/requests/${body.id}/matches`;
            }
        }
    );

    /**
     * Kakao 우편번호 검색창을 열고 선택한 주소를 배송지에 입력한다.
     */
    function openAddressSearch() {
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

                addressInput.value =
                    selectedAddress;

// 새 배송지를 선택하면 이전 상세 주소가
// 다른 장소의 정보로 남지 않도록 초기화한다.
                addressDetailInput.value = '';
                addressDetailInput.focus();
            }
        }).open();
    }

    /**
     * 기존 주문 정보를 수정 폼에 표시한다.
     */
    function populateEditForm(order) {
        form.elements
            .namedItem('title')
            .value =
            order.title || '';

        form.elements
            .namedItem('eventDateTime')
            .value =
            order.eventDateTime
                ?.slice(0, 16)
            || '';

        form.elements
            .namedItem('quantity')
            .value =
            order.quantity ?? '';

        form.elements
            .namedItem('budgetType')
            .value =
            order.budgetType
            || 'PER_PERSON';

        form.elements
            .namedItem('budget')
            .value =
            order.budget ?? '';

        form.elements
            .namedItem('category')
            .value =
            order.category || '';

        form.elements
            .namedItem('category')
            .dataset
            .originalCategory =
            order.category || '';

        form.elements
            .namedItem(
                'deliveryAddress'
            )
            .value =
            order.deliveryAddress
            || '';

        form.elements
            .namedItem(
                'deliveryAddressDetail'
            )
            .value =
            order.deliveryAddressDetail
            || '';

        form.elements
            .namedItem('description')
            .value =
            order.description || '';

        existingImageUrl =
            order.referenceImageUrl
            || null;

        updateImagePreview(null);
    }

    /**
     * 선택한 참고 이미지 또는 기존 이미지를 미리보기로 표시한다.
     */
    function updateImagePreview(file) {
        if (previewObjectUrl) {
            URL.revokeObjectURL(
                previewObjectUrl
            );

            previewObjectUrl = null;
        }

        if (file) {
            previewObjectUrl =
                URL.createObjectURL(
                    file
                );

            imagePreview.src =
                previewObjectUrl;

            imagePreviewArea.hidden =
                false;

            return;
        }

        if (existingImageUrl) {
            imagePreview.src =
                existingImageUrl;

            imagePreviewArea.hidden =
                false;

            return;
        }

        imagePreview.removeAttribute(
            'src'
        );

        imagePreviewArea.hidden =
            true;
    }
}