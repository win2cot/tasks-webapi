/**
 * Keycloak OIDC 認証モジュール
 *
 * 設定:
 *   KEYCLOAK_URL  — Keycloak の base URL (デフォルト: http://localhost:18080)
 *   REALM         — realm 名 (デフォルト: tasks)
 *   CLIENT_ID     — public client ID (デフォルト: tasks-webapi)
 *
 * keycloak-js の PKCEは属性 pkce.code.challenge.method=S256 で Keycloak 側が強制する。
 * token は sessionStorage に保存(タブ間共有を避けテナント切替の競合を防ぐ)。
 */

const Auth = (() => {
  const KEYCLOAK_URL = 'http://localhost:18080';
  const REALM = 'tasks';
  const CLIENT_ID = 'tasks-webapi';

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
    await _keycloak.updateToken(30);
    return _keycloak.token;
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
    try {
      await _keycloak.updateToken(-1);
    } catch {
      _keycloak.login();
      throw new Error('Refresh token が失効しました。再ログインが必要です。');
    }
    return _keycloak.token;
  }

  /**
   * デコード済みの ID token payload を返す。
   * @returns {Object|null}
   */
  function getUser() {
    return _keycloak ? _keycloak.idTokenParsed : null;
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
    setInterval(async () => {
      try {
        await _keycloak.updateToken(60);
      } catch {
        // 更新失敗(セッション失効等)は再ログインで対応
        _keycloak.login();
      }
    }, 30000);
  }

  return { init, getToken, refreshToken, getUser, logout };
})();
