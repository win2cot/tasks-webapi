package xyz.dgz48.tasks.webapi.tenant.domain;

import org.jspecify.annotations.Nullable;

/** テナント属性更新時の単一フィールド差分(ADR-0013)。field 名は DTO camelCase プロパティ名で統一。 */
public record FieldChange(String field, @Nullable Object oldValue, @Nullable Object newValue) {}
