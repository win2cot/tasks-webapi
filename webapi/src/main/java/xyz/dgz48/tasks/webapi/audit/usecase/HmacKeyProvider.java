package xyz.dgz48.tasks.webapi.audit.usecase;

import javax.crypto.SecretKey;

/**
 * 監査ハッシュチェーンの HMAC 鍵を解決する Port(ADR-0038 §3.3)。
 *
 * <p>鍵は DB 外(Parameter Store / KMS)で管理する(設計規約 §5.4)。各監査行は計算に用いた鍵を {@code hash_key_id} で記録し、検証時は
 * その識別子で鍵を解決する。ローテーション後の新規行は新しい鍵 ID を用い、既存行は元の鍵 ID で検証するため、連鎖は鍵に依存せず途切れない。
 */
public interface HmacKeyProvider {

  /**
   * 新規書込で用いる現行の鍵識別子を返す。
   *
   * @return 現行の {@code hash_key_id}
   */
  String currentKeyId();

  /**
   * 指定された鍵識別子に対応する HMAC-SHA256 鍵を返す。
   *
   * @param keyId 鍵識別子({@code hash_key_id})
   * @return HMAC 鍵
   * @throws IllegalArgumentException 未知の鍵識別子の場合
   */
  SecretKey keyFor(String keyId);
}
