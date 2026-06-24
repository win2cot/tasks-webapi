package xyz.dgz48.tasks.webapi.dashboard.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * ダッシュボードのセクションリスト 1 行を表す読み取り専用ドメインモデル(CQRS リードモデル)。
 *
 * <p>OpenAPI {@code Task} スキーマに必要な項目のみを保持する。{@code status} / {@code priority} / {@code visibility}
 * は task feature への型依存を避けるため文字列で保持する(DB の ENUM 制約により値域は保証され、JSON 表現は enum と同一)。 task feature の
 * {@code Task} ドメインとは別物で、dashboard feature が所有する。
 */
public record DashboardTask(
    Long id,
    Long version,
    String title,
    @Nullable String description,
    String status,
    String priority,
    String visibility,
    Long ownerId,
    @Nullable Long assigneeId,
    LocalDate dueDate,
    @Nullable LocalDateTime completedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
