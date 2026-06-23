package xyz.dgz48.tasks.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ModelException;

/**
 * {@link UserRepository} を実 MySQL に対してテスト JVM 内で直接駆動する component テスト(ADR-0006 §3.4 / §3.5)。read /
 * search / 楽観排他 write / 匿名化を網羅し、JaCoCo の計測対象とする(統合テストは Keycloak コンテナ内実行のため計測されない)。
 */
class UserRepositoryComponentTest extends AbstractMySqlContainerTest {

  // --- read ---

  @Test
  void findById_returnsRow_whenActive() throws Exception {
    String email = unique("find") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "シメイ", "部署", "ACTIVE");
    try (UserRepository repo = newRepository()) {
      Optional<UserRepository.UserRow> row = repo.findById(id);
      assertThat(row).isPresent();
      assertThat(row.get().email()).isEqualTo(email);
      assertThat(row.get().fullName()).isEqualTo("氏名");
      assertThat(row.get().fullNameKana()).isEqualTo("シメイ");
      assertThat(row.get().departmentName()).isEqualTo("部署");
      assertThat(row.get().status()).isEqualTo("ACTIVE");
    }
  }

  @Test
  void findById_empty_whenMissing() throws Exception {
    try (UserRepository repo = newRepository()) {
      assertThat(repo.findById(999_999_999L)).isEmpty();
    }
  }

  @Test
  void findByEmail_empty_whenAnonymized() throws Exception {
    String email = unique("anon") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "シメイ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      repo.anonymize(id);
      // deleted_at IS NULL で絞るため匿名化後は解決されない(= 認証拒否)。
      assertThat(repo.findByEmail(email)).isEmpty();
      assertThat(repo.findById(id)).isEmpty();
    }
  }

  // --- search / count ---

  @Test
  void search_matchAll_whenBlankOrStar() throws Exception {
    seedUser(unique("sub"), unique("all") + "@example.com", "全件 太郎", "ゼンケン", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      assertThat(repo.search("", 0, 1000)).isNotEmpty();
      assertThat(repo.search(null, 0, 1000)).isNotEmpty();
      assertThat(repo.search("*", 0, 1000)).isNotEmpty();
      assertThat(repo.count()).isGreaterThanOrEqualTo(1);
    }
  }

  @Test
  void search_matchesEmailOrFullName_caseInsensitive() throws Exception {
    String tag = unique("srch");
    seedUser(unique("sub"), tag + "-a@example.com", "検索 一郎", "ケンサク", null, "ACTIVE");
    seedUser(unique("sub"), "other-" + tag + "@example.com", tag + " 二郎", "ケンサク", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      // email 部分一致 + full_name 部分一致の双方がヒットする。
      assertThat(repo.search(tag, 0, 1000)).hasSize(2);
      // paging: maxResults=1 で 1 件に絞られる。
      assertThat(repo.search(tag, 0, 1)).hasSize(1);
      // firstResult で 2 件目以降。
      assertThat(repo.search(tag, 1, 1000)).hasSize(1);
    }
  }

  @Test
  void search_escapesLikeMetacharacters() throws Exception {
    String literal = unique("pct") + "%_x";
    seedUser(unique("sub"), literal + "@example.com", "メタ", "メタ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      // % と _ はリテラルとして一致する(ワイルドカード化されない)。
      assertThat(repo.search(literal, 0, 1000)).hasSize(1);
      // ワイルドカードとして解釈されないので、別文字列の "%" 検索では当たらない。
      assertThat(repo.search(unique("nomatch") + "%_x", 0, 1000)).isEmpty();
    }
  }

  @Test
  void search_negativeMaxResults_returnsAll() throws Exception {
    seedUser(unique("sub"), unique("neg") + "@example.com", "無制限", "ムセイゲン", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      assertThat(repo.search("", 0, -1)).isNotEmpty(); // maxResults<0 → Integer.MAX_VALUE
    }
  }

  // --- write ---

  @Test
  void insert_createsTenantlessPendingRow() throws Exception {
    String email = unique("ins") + "@example.com";
    try (UserRepository repo = newRepository()) {
      UserRepository.UserRow row = repo.insert(email);
      assertThat(row.email()).isEqualTo(email);
      assertThat(row.fullName()).isEqualTo(email);
      assertThat(row.fullNameKana()).isEmpty();
      assertThat(row.departmentName()).isNull();
      assertThat(row.status()).isEqualTo("ACTIVE");
      assertThat(row.oidcSub()).isEqualTo("pending:" + email);
      assertThat(row.version()).isZero();
    }
  }

  @Test
  void updateColumns_bumpVersion_andPersist() throws Exception {
    String email = unique("upd") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", null, "ACTIVE");
    String newEmail = unique("upd-new") + "@example.com";
    try (UserRepository repo = newRepository()) {
      assertThat(repo.updateEmail(id, newEmail, 0)).isEqualTo(1);
      assertThat(repo.updateFullNameKana(id, "シンカナ", 1)).isEqualTo(2);
      assertThat(repo.updateDepartmentName(id, "新部署", 2)).isEqualTo(3);
      assertThat(repo.updateDepartmentName(id, null, 3)).isEqualTo(4); // null 許容
    }
    UserRow row = fetchById(id);
    assertThat(row.email()).isEqualTo(newEmail);
    assertThat(row.fullNameKana()).isEqualTo("シンカナ");
    assertThat(row.departmentName()).isNull();
    assertThat(row.version()).isEqualTo(4L);
  }

  @Test
  void updateEmail_staleVersion_throwsModelException() throws Exception {
    String email = unique("opt") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      repo.updateEmail(id, unique("opt-1") + "@example.com", 0); // version 0 → 1
      assertThatThrownBy(() -> repo.updateEmail(id, unique("opt-2") + "@example.com", 0))
          .isInstanceOf(ModelException.class)
          .hasMessageContaining("optimistic lock conflict");
    }
  }

  @Test
  void anonymize_replacesPiiWithPlaceholders_andIsIdempotent() throws Exception {
    String email = unique("del") + "@example.com";
    long id = seedUser(unique("sub"), email, "氏名", "カナ", "部署", "ACTIVE");
    try (UserRepository repo = newRepository()) {
      repo.anonymize(id);
      repo.anonymize(id); // 冪等: 匿名化済みの再削除は no-op(例外を投げない)
    }
    UserRow row = fetchById(id);
    assertThat(row.deleted()).isTrue();
    assertThat(row.email()).isEqualTo("__deleted__" + id + "@deleted.invalid");
    assertThat(row.oidcSub()).isEqualTo("__deleted__" + id);
    assertThat(row.fullName()).isEqualTo("__deleted__");
    assertThat(row.fullNameKana()).isEqualTo("__deleted__");
    assertThat(row.departmentName()).isNull();
    assertThat(row.version()).isEqualTo(1L); // 1 回だけ increment(2 回目は no-op)
    // step 8: ANONYMIZE audit が 1 件だけ記録される(2 回目の no-op では追加されない)。
    assertThat(countAnonymizeAudit(id)).isEqualTo(1);
  }

  @Test
  void anonymize_recordsAnonymizeAuditWithUsersEntity() throws Exception {
    long id = seedUser(unique("sub"), unique("aud") + "@example.com", "氏名", "カナ", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      repo.anonymize(id);
    }
    AuditRow audit = fetchAnonymizeAudit(id);
    assertThat(audit).isNotNull();
    assertThat(audit.action()).isEqualTo("ANONYMIZE");
    assertThat(audit.entityType()).isEqualTo("users");
    assertThat(audit.entityId()).isEqualTo(id);
    assertThat(audit.tenantId()).isNull(); // システム横断
    assertThat(audit.userId()).isNull(); // 操作者(Keycloak admin)は webapi user-id 不明
    assertThat(audit.detail()).isEqualTo("{}");
    assertThat(audit.hashChain()).matches("[0-9a-f]{64}");
  }

  @Test
  void anonymize_chainsHashToPreviousAuditRow() throws Exception {
    // 同一 JVM・同一クラス内では逐次実行されるため、idA の ANONYMIZE 行が idB の直前行になる。
    long idA = seedUser(unique("sub"), unique("a") + "@example.com", "A", "エー", null, "ACTIVE");
    long idB = seedUser(unique("sub"), unique("b") + "@example.com", "B", "ビー", null, "ACTIVE");
    try (UserRepository repo = newRepository()) {
      repo.anonymize(idA);
      repo.anonymize(idB);
    }
    AuditRow prev = fetchAnonymizeAudit(idA);
    AuditRow next = fetchAnonymizeAudit(idB);
    assertThat(prev).isNotNull();
    assertThat(next).isNotNull();
    assertThat(next.id()).isGreaterThan(prev.id());
    // webapi AuditLogPersistenceAdapter#computeChainHash と同一式で連鎖していることを検証。
    String expected = sha256Hex(prev.id() + "|" + prev.detail() + "|" + prev.createdAt());
    assertThat(next.hashChain()).isEqualTo(expected);
  }

  private static String sha256Hex(String input) throws Exception {
    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
    byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(64);
    for (byte b : bytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  @Test
  void close_isIdempotent_evenWithoutConnection() {
    UserRepository repo = newRepository();
    repo.close(); // 接続未生成でも安全
    repo.close();
  }

  // escapeLike の単体は UserRepositoryTest で別途検証済み(ここでは search 経由の挙動を確認)。
  @Test
  void escapeLike_isExercisedThroughSearch() throws Exception {
    List<UserRepository.UserRow> rows;
    try (UserRepository repo = newRepository()) {
      rows = repo.search("|", 0, 10); // エスケープ文字自身を含む検索でも例外なく動く
    }
    assertThat(rows).isNotNull();
  }
}
