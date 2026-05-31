package xyz.dgz48.tasks.webapi.shared.web;

import java.time.OffsetDateTime;

/** 全 API 共通エラー応答 record(設計規約 §2.4 / ADR-0011)。6 フィールド固定。 */
public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    ErrorCode code,
    String message,
    String path) {}
