package xyz.dgz48.tasks.webapi.audit.domain;

/**
 * 検証対象の監査行(正準化入力 + 格納済みハッシュ)。
 *
 * @param canonical 正準化に用いる不変列
 * @param storedHash {@code audit_logs.hash_chain} に格納されている値(再計算結果と突合する)
 */
public record AuditChainRow(CanonicalAuditRow canonical, String storedHash) {}
