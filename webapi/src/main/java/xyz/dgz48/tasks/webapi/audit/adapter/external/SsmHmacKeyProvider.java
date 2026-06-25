package xyz.dgz48.tasks.webapi.audit.adapter.external;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import xyz.dgz48.tasks.webapi.audit.usecase.HmacKeyProvider;

/**
 * AWS Systems Manager Parameter Store(SecureString)から HMAC 鍵をロードする {@link
 * HmacKeyProvider}(本番、ADR-0038 §3.3)。
 *
 * <p>パラメータ名は {@code <prefix>-<keyId>}(例 {@code /tasks/dev/app/audit-hash-key-v1})。鍵は不変のため鍵 ID
 * 単位でキャッシュし、KMS 復号付きで一度だけ取得する。認証情報・リージョンは AWS SDK 標準プロバイダチェーン(ECS タスクロール)で解決する。
 */
public class SsmHmacKeyProvider implements HmacKeyProvider {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final SsmClient ssmClient;
  private final String currentKeyId;
  private final String parameterPrefix;
  private final ConcurrentMap<String, SecretKey> cache = new ConcurrentHashMap<>();

  public SsmHmacKeyProvider(SsmClient ssmClient, String currentKeyId, String parameterPrefix) {
    this.ssmClient = ssmClient;
    this.currentKeyId = currentKeyId;
    this.parameterPrefix = parameterPrefix;
  }

  @Override
  public String currentKeyId() {
    return currentKeyId;
  }

  @Override
  public SecretKey keyFor(String keyId) {
    return cache.computeIfAbsent(keyId, this::loadKey);
  }

  private SecretKey loadKey(String keyId) {
    String parameterName = parameterPrefix + "-" + keyId;
    String secret =
        ssmClient
            .getParameter(
                GetParameterRequest.builder().name(parameterName).withDecryption(true).build())
            .parameter()
            .value();
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
  }
}
