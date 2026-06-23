package xyz.dgz48.tasks.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/**
 * 実ブラウザ(Playwright)による E2E(ADR-0006 §6)。SPI で federate した user が Keycloak へログインでき、Account Console の
 * personal info 編集が SPI の write 範囲どおりに着地することを検証する:
 *
 * <ul>
 *   <li>federate user がブラウザでログインできる(E2E ログイン確認)
 *   <li>Account Console で email を変更 → {@code users.email} に反映される
 *   <li>Account Console で氏名(First name)を変更 → {@code users.full_name} は read-only で不変
 * </ul>
 *
 * <p>Admin Console 経由の user 作成 / 削除(§6)は Admin REST API を介する操作であり {@link UserStorageSpiIT}(Admin
 * Console のバックエンドと同一 API)で検証する。
 */
class AccountConsoleE2ETest extends AbstractSpiContainerTest {

  static Playwright playwright;
  static Browser browser;

  @BeforeAll
  static void startBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  static void stopBrowser() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  private static String accountUrl() {
    return KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM + "/account/";
  }

  // --- E2E ログイン確認 ---

  @Test
  void login_federatedUser_succeedsInBrowser() throws Exception {
    String email = uniqueEmail("e2e-login");
    seedWithPassword(email, "ログイン 確認", "ログイン カクニン", "P@ssw0rd-login");

    try (BrowserContext ctx = browser.newContext()) {
      Page page = ctx.newPage();
      page.navigate(accountUrl());
      login(page, email, "P@ssw0rd-login");

      // Account Console の personal info が描画され、SPI から read した email が表示される = ログイン成功 + profile read。
      page.waitForSelector("#email");
      assertThat(page.locator("#email").inputValue()).isEqualTo(email);
      assertThat(page.locator("#username").inputValue()).isEqualTo(email);
    }
  }

  // --- email 変更 → users.email 反映 ---

  @Test
  void accountConsole_changeEmail_writesBackToUsersTable() throws Exception {
    String email = uniqueEmail("e2e-email");
    long id = seedWithPassword(email, "メール 変更", "メール ヘンコウ", "P@ssw0rd-email");
    String newEmail = uniqueEmail("e2e-email-new");

    try (BrowserContext ctx = browser.newContext()) {
      Page page = ctx.newPage();
      page.navigate(accountUrl());
      login(page, email, "P@ssw0rd-email");
      page.waitForSelector("#email");

      page.locator("#email").fill(newEmail);
      // Account REST への保存リクエスト完了を待ってから DB を検証する。
      page.waitForResponse(
          r -> r.url().contains("/account") && "POST".equals(r.request().method()),
          () -> page.locator("#save-btn").click());
    }

    UserRow row = fetchById(id);
    assertThat(row.email()).isEqualTo(newEmail); // users.email へ write 戻し
    assertThat(row.version()).isGreaterThanOrEqualTo(1L);
  }

  // --- 氏名変更 → users.full_name は read-only ---

  @Test
  void accountConsole_changeFirstName_isNotWrittenBack() throws Exception {
    String email = uniqueEmail("e2e-name");
    long id = seedWithPassword(email, "氏名 元", "シメイ モト", "P@ssw0rd-name");

    try (BrowserContext ctx = browser.newContext()) {
      Page page = ctx.newPage();
      page.navigate(accountUrl());
      login(page, email, "P@ssw0rd-name");
      page.waitForSelector("#firstName");

      page.locator("#firstName").fill("氏名 変更後");
      page.waitForResponse(
          r -> r.url().contains("/account") && "POST".equals(r.request().method()),
          () -> page.locator("#save-btn").click());
    }

    UserRow row = fetchById(id);
    assertThat(row.fullName()).isEqualTo("氏名 元"); // full_name は不変(SoT は webapi)
    assertThat(row.version()).isEqualTo(0L); // 書き戻し無し
  }

  // --- helpers ---

  private static long uniqueCounter = 0;

  private static synchronized String uniqueEmail(String label) {
    return label + "-" + ++uniqueCounter + "@example.com";
  }

  private long seedWithPassword(String email, String fullName, String fullNameKana, String password)
      throws Exception {
    long id = seedUser("sub-" + email, email, fullName, fullNameKana, null, "ACTIVE");
    try (Keycloak admin = adminClient()) {
      UserRepresentation user = admin.realm(REALM).users().search(email, 0, 2).get(0);
      CredentialRepresentation cred = new CredentialRepresentation();
      cred.setType(CredentialRepresentation.PASSWORD);
      cred.setValue(password);
      cred.setTemporary(false);
      admin.realm(REALM).users().get(user.getId()).resetPassword(cred);
    }
    return id;
  }

  private static void login(Page page, String email, String password) {
    page.waitForSelector(
        "#username", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));
    page.locator("#username").fill(email);
    page.locator("#password").fill(password);
    page.locator("#kc-login").click();
  }
}
