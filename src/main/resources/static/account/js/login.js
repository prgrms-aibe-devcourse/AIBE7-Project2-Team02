import {
  authFetch,
  readAccessToken,
  readApiBody,
  safeInternalRedirect,
  saveAccessToken,
  showFieldErrors,
  validateRequiredFields,
} from './auth-client.js';

const form = document.querySelector('#account-login-form');
const message = document.querySelector('#account-form-message');
const submit = form.querySelector('button[type="submit"]');

if (readAccessToken()) {
  try {
    const response = await authFetch('/api/v1/account/me');
    if (response.ok) window.location.replace(safeInternalRedirect(window.location.search));
  } catch {
    // A temporary profile lookup failure must not prevent a fresh login attempt.
  }
}

const notice = new URLSearchParams(window.location.search);
if (notice.get('passwordChanged') === 'success') showMessage('비밀번호가 변경되었습니다. 다시 로그인해 주세요.', true);
if (notice.get('signup') === 'success') showMessage('회원가입이 완료됐습니다. 로그인해 주세요.', true);
if (notice.get('withdrawn') === 'success') showMessage('회원 탈퇴가 완료됐습니다.', true);

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!validateRequiredFields(form)) return;
  showMessage('');
  submit.disabled = true;
  submit.textContent = '확인 중...';

  const formData = new FormData(form);
  try {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Object.fromEntries(formData)),
    });
    const body = await readApiBody(response);
    if (!response.ok) {
      if (response.status === 423 && body?.code === 'ACCOUNT_SUSPENDED') {
        sessionStorage.setItem('matcheat.suspension', JSON.stringify(body));
        sessionStorage.setItem('matcheat.suspensionEmail', formData.get('email'));
        window.location.assign('/suspended');
        return;
      }
      showFieldErrors(form, body?.fieldErrors);
      throw new Error(loginErrorMessage(body));
    }

    saveAccessToken(body.accessToken);
    showMessage('로그인되었습니다. 이동합니다.', true);
    window.location.assign(safeInternalRedirect(window.location.search));
  } catch (error) {
    showMessage(error.message || '네트워크 연결을 확인해 주세요.');
  } finally {
    submit.disabled = false;
    submit.textContent = '로그인';
  }
});

function loginErrorMessage(body) {
  const messages = {
    INVALID_CREDENTIALS: '이메일 또는 비밀번호를 확인해 주세요.',
    ACCOUNT_SUSPENDED: '정지된 계정입니다. 관리자에게 문의해 주세요.',
    ACCOUNT_WITHDRAWN: '탈퇴한 계정입니다.',
  };
  return messages[body?.code] || body?.message || '로그인에 실패했습니다.';
}

function showMessage(text, success = false) {
  message.classList.toggle('is-success', success);
  message.textContent = text;
}
