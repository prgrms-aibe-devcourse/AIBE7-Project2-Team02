const status = JSON.parse(sessionStorage.getItem('matcheat.suspension') || '{}');
const email = sessionStorage.getItem('matcheat.suspensionEmail') || '';
document.querySelector('[data-suspension-reason]').textContent = status.reason || '관리자 정지';
document.querySelector('[data-suspension-period]').textContent = status.indefinite
    ? '관리자가 해제할 때까지'
    : status.expiresAt ? `${new Date(status.expiresAt).toLocaleString('ko-KR')}까지` : '관리자 확인 필요';

const form = document.querySelector('[data-appeal-form]');
form.elements.email.value = email;
form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const output = document.querySelector('[data-appeal-message]');
    const response = await fetch('/api/v1/auth/suspension/appeals', {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(Object.fromEntries(new FormData(form))),
    });
    const body = response.headers.get('content-type')?.includes('json') ? await response.json() : null;
    output.textContent = response.ok ? '해명 메시지가 관리자에게 전달되었습니다.'
        : body?.message || '메시지를 보내지 못했습니다.';
    if (response.ok) form.reset();
});
