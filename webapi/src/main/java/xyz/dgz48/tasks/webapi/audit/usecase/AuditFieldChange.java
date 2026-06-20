package xyz.dgz48.tasks.webapi.audit.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * 監査ログ detail の差分フィールド 1 件。JSON キー名は後方互換のため {@code "old"} / {@code "new"} で固定する。
 *
 * <p>domain 層の {@code FieldChange} は POJO のまま保持し、usecase 層でこの型に詰め替えてシリアライズ責務を分離する。
 */
public record AuditFieldChange(
    String field,
    @JsonProperty("old") @Nullable Object oldValue,
    @JsonProperty("new") @Nullable Object newValue) {}
