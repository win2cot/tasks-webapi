package xyz.dgz48.tasks.webapi.audit.domain;

/**
 * 連鎖末尾の状態({@code chain_heads} の不変表現)。
 *
 * @param chainKey 連鎖キー(tenant_id、横断は 0)
 * @param lastHash 末尾行のハッシュ
 * @param lastSeq 末尾の chain_seq
 */
public record ChainHead(long chainKey, String lastHash, long lastSeq) {}
