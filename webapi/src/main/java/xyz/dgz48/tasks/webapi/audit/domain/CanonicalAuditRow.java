package xyz.dgz48.tasks.webapi.audit.domain;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * ハッシュチェーン計算の入力となる監査行の不変表現(ADR-0038 §3.2)。
 *
 * <p>書込側(adapter)と検証側(B-05 バッチ)が同一の {@link AuditCanonicalizer} を通すことで、再計算結果が byte
 * 単位で一致することを保証する。{@code chainKey} は解決済みの値(テナント行は {@code tenant_id}、横断行は予約値 {@code 0})であり、{@code
 * audit_logs.tenant_id} 列の値(横断行は {@code NULL})とは異なる点に注意する。
 *
 * @param chainKey 連鎖キー(tenant_id、横断は 0)
 * @param chainSeq 連鎖内の順序(1 始まり)
 * @param userId 操作ユーザー ID(不明な場合は {@code null})
 * @param action イベント種別({@code AuditEventType.name()})
 * @param entityType 対象エンティティ種別({@code null} 可)
 * @param entityId 対象 ID({@code null} 可)
 * @param detailJson detail 列に格納する JSON 文字列(空は {@code "{}"})。canonical ではネスト object として埋め込む
 * @param ipAddress 送信元 IP({@code null} 可)
 * @param createdAt 発生日時(JST、秒精度に正規化済み)
 * @param hashKeyId この行の HMAC 計算に用いる鍵識別子
 */
public record CanonicalAuditRow(
    long chainKey,
    long chainSeq,
    @Nullable Long userId,
    String action,
    @Nullable String entityType,
    @Nullable Long entityId,
    String detailJson,
    @Nullable String ipAddress,
    LocalDateTime createdAt,
    String hashKeyId) {}
