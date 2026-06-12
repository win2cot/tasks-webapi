document.getElementById('btn-logout').addEventListener('click', () => Auth.logout());

async function main() {
  const authenticated = await Auth.init();
  document.getElementById('loading').classList.add('d-none');

  if (!authenticated) {
    return;
  }

  const user = Auth.getUser();
  document.getElementById('nav-username').textContent = user.name || user.preferred_username || '';
  document.getElementById('nav-username').classList.remove('d-none');
  document.getElementById('btn-logout').classList.remove('d-none');
  document.getElementById('content-logged-in').classList.remove('d-none');

  try {
    const me = await Api.getMe();

    if (me.activeTenantId !== null) {
      Api.setTenantId(me.activeTenantId);
      window.location.replace('tasks.html');
      return;
    }

    // テナント未選択: テナント選択 UI を表示
    document.getElementById('user-info').textContent =
      'テナントを選択してタスク一覧を開いてください。';

    document.getElementById('tenant-switcher').setData(me.tenants, me.activeTenantId);
  } catch (err) {
    document.getElementById('user-info').textContent = `API 呼び出しエラー: ${err.message}`;
  }
}

main();
