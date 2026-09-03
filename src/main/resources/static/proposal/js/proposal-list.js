import {authFetch, readApiBody, readCurrentUserId} from '/account/js/auth-client.js';

/**
 * 받은 제안과 보낸 제안 목록을 조회하고 받은 제안 비교 기능을 제공한다.
 */
const pagePath =
    window.location.pathname;

const supportedPage =
    pagePath === '/proposals'
    || pagePath === '/mypage/offers'
    || pagePath === '/mypage/buying-proposals'
    || pagePath === '/mypage/selling-proposals';

const proposalList =
    document.getElementById('proposalList');

const tabButtons =
    document.querySelectorAll('[data-proposal-tab]');

const compareToolbar =
    document.getElementById('proposalCompareToolbar');

const compareCount =
    document.getElementById('proposalCompareCount');

const compareButton =
    document.getElementById('proposalCompareButton');

const compareResetButton =
    document.getElementById('proposalCompareResetButton');

const comparisonSection =
    document.getElementById('proposalComparison');

const comparisonTable =
    document.getElementById('proposalComparisonTable');

const comparisonCloseButton =
    document.getElementById('proposalComparisonCloseButton');

const currentUserId =
    readCurrentUserId();

let currentTab = 'received';

let currentProposals = [];

const selectedProposals =
    new Map();

/**
 * Proposal 상태를 사용자에게 보여줄 한글 문자열로 변환한다.
 */
function proposalStatusLabel(status) {
    const labels = {
        SENT: '제안 전송',
        IN_TALK: '협의 중',
        ACCEPTED: '수락',
        REJECTED: '거절',
        WITHDRAWN: '철회'
    };

    return labels[status] ?? status;
}

/**
 * 금액을 천 단위 구분 형태로 표시한다.
 */
function formatMoney(value) {
    if (value == null) {
        return '-';
    }

    return Number(value).toLocaleString() + '원';
}

/**
 * 제안 생성 시간을 화면용 문자열로 변환한다.
 */
function formatDateTime(value) {
    if (!value) {
        return '-';
    }

    return new Date(value)
        .toLocaleString('ko-KR');
}

/**
 * HTML 문자열 삽입 시 사용자 입력값을 안전하게 표시한다.
 */
function escapeHtml(value) {
    const element =
        document.createElement('div');

    element.textContent =
        value ?? '';

    return element.innerHTML;
}

/**
 * 비교 선택 상태를 모두 초기화한다.
 */
function clearCompareSelection() {
    selectedProposals.clear();

    if (comparisonSection) {
        comparisonSection.hidden = true;
    }

    if (comparisonTable) {
        comparisonTable.replaceChildren();
    }

    updateCompareSelectionUi();
}

/**
 * 현재 선택된 제안 수와 체크박스 상태를 갱신한다.
 */
function updateCompareSelectionUi() {
    if (
        !compareCount
        || !compareButton
        || !compareResetButton
    ) {
        return;
    }

    const selectedCount =
        selectedProposals.size;

    compareCount.textContent =
        `${selectedCount}개 선택`;

    compareButton.disabled =
        selectedCount < 2;

    compareResetButton.disabled =
        selectedCount === 0;

    const selectedRequestId =
        selectedCount > 0
            ? [...selectedProposals.values()][0].requestId
            : null;

    document
        .querySelectorAll(
            '.proposal-compare-checkbox'
        )
        .forEach(checkbox => {
            const proposalId =
                Number(
                    checkbox.dataset.proposalId
                );

            const requestId =
                Number(
                    checkbox.dataset.requestId
                );

            const isSelected =
                selectedProposals.has(
                    proposalId
                );

            checkbox.checked =
                isSelected;

            checkbox.disabled =
                !isSelected
                && (
                    selectedCount >= 3
                    || (
                        selectedRequestId !== null
                        && requestId !== selectedRequestId
                    )
                );

            const card =
                checkbox.closest(
                    '.proposal-list-item'
                );

            if (card) {
                card.classList.toggle(
                    'is-compare-selected',
                    isSelected
                );
            }
        });
}

/**
 * 받은 제안의 비교 선택 상태를 변경한다.
 */
function toggleCompareSelection(
    proposal,
    checked
) {
    if (!checked) {
        selectedProposals.delete(
            proposal.id
        );

        if (comparisonSection) {
            comparisonSection.hidden = true;
        }

        updateCompareSelectionUi();

        return;
    }

    if (selectedProposals.size >= 3) {
        alert(
            '제안은 최대 3개까지 비교할 수 있습니다.'
        );

        updateCompareSelectionUi();

        return;
    }

    if (selectedProposals.size > 0) {
        const firstProposal =
            [...selectedProposals.values()][0];

        if (
            firstProposal.requestId
            !== proposal.requestId
        ) {
            alert(
                '같은 주문에 들어온 제안끼리만 비교할 수 있습니다.'
            );

            updateCompareSelectionUi();

            return;
        }
    }

    selectedProposals.set(
        proposal.id,
        proposal
    );

    if (comparisonSection) {
        comparisonSection.hidden = true;
    }

    updateCompareSelectionUi();
}

/**
 * 받은 제안 카드에 비교 선택 이벤트를 연결한다.
 */
function bindCompareCheckboxes() {
    document
        .querySelectorAll(
            '.proposal-compare-checkbox'
        )
        .forEach(checkbox => {
            checkbox.addEventListener(
                'change',
                event => {
                    const proposalId =
                        Number(
                            event.currentTarget
                                .dataset.proposalId
                        );

                    const proposal =
                        currentProposals.find(
                            item =>
                                item.id === proposalId
                        );

                    if (!proposal) {
                        return;
                    }

                    toggleCompareSelection(
                        proposal,
                        event.currentTarget.checked
                    );
                }
            );
        });
}

/**
 * 목록 데이터를 카드 형태로 렌더링한다.
 */
function renderProposals(proposals) {
    if (!proposalList) {
        return;
    }

    currentProposals =
        Array.isArray(proposals)
            ? proposals
            : [];

    clearCompareSelection();

    if (currentProposals.length === 0) {
        if (compareToolbar) {
            compareToolbar.hidden = true;
        }

        proposalList.innerHTML = `
            <div class="proposal-list-empty">
                ${
            currentTab === 'received'
                ? '받은 제안이 없습니다.'
                : '보낸 제안이 없습니다.'
        }
            </div>
        `;

        return;
    }

    if (compareToolbar) {
        compareToolbar.hidden =
            currentTab !== 'received';
    }

    proposalList.innerHTML = '';

    currentProposals.forEach(
        proposal => {
            const card =
                document.createElement(
                    'article'
                );

            card.className =
                'proposal-list-item';

            const compareSelector =
                currentTab === 'received'
                    ? `
                        <label class="proposal-compare-selector">
                            <input
                                type="checkbox"
                                class="proposal-compare-checkbox"
                                data-proposal-id="${proposal.id}"
                                data-request-id="${proposal.requestId}"
                            >
                            <span>비교</span>
                        </label>
                    `
                    : '';

            card.innerHTML = `
                <div class="proposal-list-item-main">

                    <div class="proposal-list-item-header">

                        <div class="proposal-list-title-area">

                            <div class="proposal-list-kicker">
                                ${compareSelector}

                                <span class="proposal-request-number">
                                    주문 #${proposal.requestId}
                                </span>
                            </div>

                            <h2>
                                ${escapeHtml(proposal.itemName)}
                            </h2>

                        </div>

                        <div class="proposal-list-header-actions">

                            <span class="proposal-status-badge">
                                ${proposalStatusLabel(proposal.status)}
                            </span>

                            <a
                                href="/requests/${proposal.requestId}"
                                class="proposal-list-order-link"
                            >
                                주문 보기
                            </a>

                        </div>

                    </div>

                    <div class="proposal-list-meta">

                        <div>
                            <span class="proposal-list-meta-label">
                                수량
                            </span>

                            <strong>
                                ${proposal.quantity}명
                            </strong>
                        </div>

                        <div>
                            <span class="proposal-list-meta-label">
                                1인 단가
                            </span>

                            <strong>
                                ${formatMoney(proposal.unitPrice)}
                            </strong>
                        </div>

                        <div>
                            <span class="proposal-list-meta-label">
                                최종 제안 금액
                            </span>

                            <strong>
                                ${formatMoney(proposal.totalAmount)}
                            </strong>
                        </div>

                        <div>
                            <span class="proposal-list-meta-label">
                                준비 기간
                            </span>

                            <strong>
                                ${proposal.preparationDays}일
                            </strong>
                        </div>

                    </div>

                    ${
                proposal.description
                    ? `
                                <p class="proposal-list-description">
                                    ${escapeHtml(proposal.description)}
                                </p>
                            `
                    : ''
            }

                    <span class="proposal-list-date">
                        ${formatDateTime(proposal.createdAt)}
                    </span>

                </div>
            `;

            proposalList.appendChild(
                card
            );
        }
    );

    if (currentTab === 'received') {
        bindCompareCheckboxes();
        updateCompareSelectionUi();
    }
}

/**
 * 선택한 Proposal들을 비교표로 표시한다.
 */
function renderComparison() {
    if (
        !comparisonSection
        || !comparisonTable
    ) {
        return;
    }

    const proposals =
        [...selectedProposals.values()];

    if (proposals.length < 2) {
        return;
    }

    const requestId =
        proposals[0].requestId;

    const headerCells =
        proposals
            .map(
                (proposal, index) => `
                    <th>
                        <span class="proposal-comparison-column-number">
                            제안 ${index + 1}
                        </span>

                        <strong>
                            ${escapeHtml(proposal.itemName)}
                        </strong>
                    </th>
                `
            )
            .join('');

    const rows = [
        {
            label: '판매자',
            values: proposals.map(
                proposal =>
                    `판매자 #${proposal.sellerId}`
            )
        },
        {
            label: '상품',
            values: proposals.map(
                proposal =>
                    proposal.productId != null
                        ? `상품 #${proposal.productId}`
                        : '직접 입력 제안'
            )
        },
        {
            label: '수량',
            values: proposals.map(
                proposal =>
                    `${proposal.quantity}명`
            )
        },
        {
            label: '1인 단가',
            values: proposals.map(
                proposal =>
                    formatMoney(
                        proposal.unitPrice
                    )
            )
        },
        {
            label: '총 제안 금액',
            values: proposals.map(
                proposal =>
                    formatMoney(
                        proposal.totalAmount
                    )
            )
        },
        {
            label: '준비 기간',
            values: proposals.map(
                proposal =>
                    `${proposal.preparationDays}일`
            )
        },
        {
            label: '상태',
            values: proposals.map(
                proposal =>
                    proposalStatusLabel(
                        proposal.status
                    )
            )
        },
        {
            label: '제안 설명',
            className:
                'proposal-comparison-description-row',
            values: proposals.map(
                proposal =>
                    proposal.description
                        ? escapeHtml(
                            proposal.description
                        )
                        : '-'
            )
        },
        {
            label: '제안 시각',
            values: proposals.map(
                proposal =>
                    formatDateTime(
                        proposal.createdAt
                    )
            )
        }
    ];

    const bodyRows =
        rows
            .map(row => `
                <tr class="${row.className ?? ''}">
                    <th scope="row">
                        ${row.label}
                    </th>

                    ${
                row.values
                    .map(value => `
                                <td>
                                    ${value}
                                </td>
                            `)
                    .join('')
            }
                </tr>
            `)
            .join('');

    comparisonTable.innerHTML = `
        <div class="proposal-comparison-order">
            주문 #${requestId}에 들어온 제안을 비교하고 있습니다.
        </div>

        <table class="proposal-comparison-table">
            <thead>
                <tr>
                    <th>비교 항목</th>
                    ${headerCells}
                </tr>
            </thead>

            <tbody>
                ${bodyRows}
            </tbody>
        </table>
    `;

    comparisonSection.hidden = false;

    comparisonSection.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
    });
}

/**
 * 선택한 탭의 Proposal 목록을 서버에서 조회한다.
 */
async function loadProposals(tab) {
    if (!proposalList) {
        return;
    }

    currentTab = tab;

    clearCompareSelection();

    if (compareToolbar) {
        compareToolbar.hidden = true;
    }

    proposalList.innerHTML = `
        <div class="proposal-list-empty">
            제안 목록을 불러오는 중입니다.
        </div>
    `;

    const url =
        tab === 'received'
            ? '/api/v1/proposals/received'
            : '/api/v1/proposals/sent';

    try {
        const response =
            await authFetch(url);

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
            await readApiBody(response);

        if (!response.ok) {
            proposalList.innerHTML = `
                <div class="proposal-list-empty">
                    ${
                escapeHtml(
                    body?.message
                    ?? '제안 목록을 불러오지 못했습니다.'
                )
            }
                </div>
            `;

            return;
        }

        renderProposals(body);
    } catch (error) {
        console.error(
            '제안 목록 조회 실패:',
            error
        );

        proposalList.innerHTML = `
            <div class="proposal-list-empty">
                제안 목록을 불러오지 못했습니다.
            </div>
        `;
    }
}

/**
 * 탭 활성 상태를 변경한다.
 */
function selectTab(tab) {
    tabButtons.forEach(button => {
        button.classList.toggle(
            'is-active',
            button.dataset.proposalTab === tab
        );
    });

    loadProposals(tab);
}

/**
 * 제안 목록 화면 이벤트를 연결한다.
 */
function bindEvents() {
    tabButtons.forEach(button => {
        button.addEventListener(
            'click',
            () => {
                selectTab(
                    button.dataset.proposalTab
                );
            }
        );
    });

    compareButton?.addEventListener(
        'click',
        renderComparison
    );

    compareResetButton?.addEventListener(
        'click',
        clearCompareSelection
    );

    comparisonCloseButton?.addEventListener(
        'click',
        () => {
            if (comparisonSection) {
                comparisonSection.hidden = true;
            }
        }
    );
}

/**
 * 제안 목록을 사용하는 페이지에서만 초기 조회를 실행한다.
 */
function initialize() {
    if (!supportedPage) {
        return;
    }

    if (!proposalList) {
        return;
    }

    if (currentUserId === null) {
        const redirect =
            encodeURIComponent(
                window.location.pathname
            );

        window.location.href =
            `/login?redirect=${redirect}`;

        return;
    }
    bindEvents();

    const initialTab =
        pagePath === '/mypage/selling-proposals'
            ? 'sent'
            : 'received';

    selectTab(initialTab);
}

initialize();