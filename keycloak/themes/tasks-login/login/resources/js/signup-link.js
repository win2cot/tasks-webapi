// tasks-login テーマ(ADR-0040 / #832): ログイン画面に「新規登録」リンクを注入する。
// login.ftl を上書きせず scripts で読み込む軽量実装(Keycloak バージョン追従の脆さを避ける)。
//
// セルフサインアップ画面(signup.html)は SPA 側ドメイン(tasks[-env].dgz48.xyz)にあり、ログイン画面は
// 認証ドメイン(auth[-env].dgz48.xyz)にあるため、ホスト名から SPA の絶対 URL を導出する。既知パターンに
// 一致しないホスト(ローカル等)ではリンクを注入しない(誤誘導を避ける)。
(() => {
  const host = window.location.hostname;
  const match = host.match(/^auth(?:-(\w+))?\.dgz48\.xyz$/);
  if (!match) {
    return;
  }
  const env = match[1] ? `-${match[1]}` : "";
  const signupUrl = `https://tasks${env}.dgz48.xyz/signup.html`;

  const render = () => {
    if (document.getElementById("tasks-signup-link")) {
      return;
    }
    // ログインフォーム(#kc-form)直後に控えめに配置。無ければ body 末尾。
    const anchor =
      document.getElementById("kc-form") || document.querySelector(".login-pf-page") || document.body;
    const wrapper = document.createElement("div");
    wrapper.id = "tasks-signup-link";
    wrapper.style.marginTop = "1rem";
    wrapper.style.textAlign = "center";

    const intro = document.createElement("span");
    intro.textContent = "アカウントをお持ちでない方は ";

    const link = document.createElement("a");
    link.href = signupUrl;
    link.textContent = "新規登録";

    wrapper.appendChild(intro);
    wrapper.appendChild(link);
    anchor.appendChild(wrapper);
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", render);
  } else {
    render();
  }
})();
