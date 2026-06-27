/**
 * Keycloak OIDC 認証モジュール
 *
 * 設定:
 *   KEYCLOAK_URL  — ホスト名から自動導出。tasks[-<env>].dgz48.xyz → auth[-<env>].dgz48.xyz
 *                   それ以外(localhost 等)は http://localhost:18080 にフォールバック
 *   REALM         — realm 名 (デフォルト: tasks)
 *   CLIENT_ID     — public client ID (デフォルト: tasks-webapi)
 *
 * keycloak-js の PKCEは属性 pkce.code.challenge.method=S256 で Keycloak 側が強制する。
 * token は sessionStorage に保存(タブ間共有を避けテナント切替の競合を防ぐ)。
 */

const Auth = (() => {
  const _envMatch = window.location.hostname.match(/^tasks(?:-(\w+))?\.dgz48\.xyz$/);
  const KEYCLOAK_URL = _envMatch
    ? `https://auth${_envMatch[1] ? `-${_envMatch[1]}` : ''}.dgz48.xyz`
    : 'http://localhost:18080';
  const REALM = 'tasks';
  const CLIENT_ID = 'tasks-webapi';

  /** @type {InstanceType<typeof Keycloak>|null} */
  let _keycloak = null;

  function _createKeycloak() {
    return new Keycloak({
      url: KEYCLOAK_URL,
      realm: REALM,
      clientId: CLIENT_ID,
    });
  }

  /**
   * 認証初期化。未ログインなら Keycloak ログインページへリダイレクト。
   * @returns {Promise<boolean>} 認証済みなら true
   */
  async function init() {
    _keycloak = _createKeycloak();

    const authenticated = await _keycloak.init({
      onLoad: 'login-required',
      checkLoginIframe: false,
      pkceMethod: 'S256',
    });

    if (authenticated) {
      _scheduleTokenRefresh();
    }

    return authenticated;
  }

  /**
   * 現在の Bearer token を返す。期限切れ間近なら自動更新する。
   * @returns {Promise<string>} JWT access token
   */
  async function getToken() {
    if (!_keycloak) {
      throw new Error('Auth が初期化されていません。init() を先に呼んでください。');
    }
    // キャプチャして await 後も型が絞られた状態を保つ
    const kc = _keycloak;
    await kc.updateToken(30);
    const token = kc.token;
    if (!token) throw new Error('トークンを取得できませんでした。');
    return token;
  }

  /**
   * access token を強制リフレッシュする。401 受信後のリトライ前に呼び出す。
   * セッション失効時は再ログインページへリダイレクトする。
   * @returns {Promise<string>} 新しい JWT access token
   */
  async function refreshToken() {
    if (!_keycloak) {
      throw new Error('Auth が初期化されていません。init() を先に呼んでください。');
    }
    const kc = _keycloak;
    try {
      await kc.updateToken(-1);
    } catch {
      kc.login();
      throw new Error('Refresh token が失効しました。再ログインが必要です。');
    }
    const token = kc.token;
    if (!token) throw new Error('トークンを取得できませんでした。');
    return token;
  }

  /**
   * デコード済みの ID token payload を返す。
   * @returns {KeycloakUser|null}
   */
  function getUser() {
    return /** @type {KeycloakUser|null} */ (_keycloak ? (_keycloak.idTokenParsed ?? null) : null);
  }

  /**
   * ログイン中ユーザーが SaaS Admin(Keycloak realm role APP_ADMIN)か判定する。
   * UI 導線の出し分け用。API の認可はサーバ側(app_admin_users)が正本で、これは表示制御のみ。
   * @returns {boolean}
   */
  function isAppAdmin() {
    return _keycloak ? _keycloak.hasRealmRole('APP_ADMIN') : false;
  }

  /**
   * Keycloak ログアウトエンドポイントへリダイレクト。
   */
  function logout() {
    if (_keycloak) {
      _keycloak.logout({ redirectUri: window.location.origin + window.location.pathname });
    }
  }

  // token の有効期限 60 秒前に自動更新するポーリング(30 秒間隔)
  function _scheduleTokenRefresh() {
    if (!_keycloak) return;
    const kc = _keycloak;
    setInterval(async () => {
      try {
        await kc.updateToken(60);
      } catch {
        // 更新失敗(セッション失効等)は再ログインで対応
        kc.login();
      }
    }, 30000);
  }

  return { init, getToken, refreshToken, getUser, isAppAdmin, logout };
})();
