package xyz.dgz48.tasks.webapi.dashboard.adapter.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import xyz.dgz48.tasks.webapi.dashboard.adapter.web.dto.DashboardSummaryResponse;
import xyz.dgz48.tasks.webapi.dashboard.adapter.web.dto.DashboardTaskItemResponse;
import xyz.dgz48.tasks.webapi.dashboard.adapter.web.dto.DashboardTaskSectionsResponse;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTask;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTaskSections;
import xyz.dgz48.tasks.webapi.dashboard.usecase.GetDashboardSummaryUseCase;
import xyz.dgz48.tasks.webapi.dashboard.usecase.GetDashboardTasksUseCase;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

/**
 * S-03 個人視点ダッシュボード API。
 *
 * <p>業務 API のため {@code MEMBER} / {@code TENANT_ADMIN} のみ許可し、SaaS Admin(APP_ADMIN)が呼び出した場合は 403
 * (§6.2.1)。Tenant Admin であっても通常の参照認可フィルタが適用され、特別権限はない(ADR-0005)。テナント分離は Hibernate Filter で
 * 自動付与される(ADR-0010)。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final GetDashboardTasksUseCase getDashboardTasksUseCase;
  private final GetDashboardSummaryUseCase getDashboardSummaryUseCase;
  private final UserRepository userRepository;

  @GetMapping("/tasks")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public ResponseEntity<DashboardTaskSectionsResponse> getDashboardTasks(
      @RequestParam(defaultValue = "3") @Min(1) @Max(14) int dueWithinDays,
      TasksAuthenticationToken token) {
    requireTenant();
    Long userId = token.getPrincipal().getId();

    DashboardTaskSections sections = getDashboardTasksUseCase.execute(userId, dueWithinDays);
    Map<Long, UserJpaEntity> userMap = loadUserMap(sections);

    return ResponseEntity.ok(
        new DashboardTaskSectionsResponse(
            toItems(sections.overdue(), userId, userMap),
            toItems(sections.today(), userId, userMap),
            toItems(sections.upcoming(), userId, userMap),
            toItems(sections.completedToday(), userId, userMap)));
  }

  @GetMapping("/summary")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public ResponseEntity<DashboardSummaryResponse> getDashboardSummary(
      TasksAuthenticationToken token) {
    requireTenant();
    Long userId = token.getPrincipal().getId();
    return ResponseEntity.ok(
        DashboardSummaryResponse.from(getDashboardSummaryUseCase.execute(userId)));
  }

  private static void requireTenant() {
    if (TenantContext.get() == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "テナントが選択されていません");
    }
  }

  private List<DashboardTaskItemResponse> toItems(
      List<DashboardTask> tasks, Long userId, Map<Long, UserJpaEntity> userMap) {
    return tasks.stream()
        .map(task -> DashboardTaskItemResponse.from(task, userId, userMap))
        .toList();
  }

  /** 4 セクション全体の所有者・担当者を 1 回のクエリで解決する(N+1 回避)。 */
  private Map<Long, UserJpaEntity> loadUserMap(DashboardTaskSections sections) {
    Set<Long> userIds =
        Stream.of(
                sections.overdue(),
                sections.today(),
                sections.upcoming(),
                sections.completedToday())
            .flatMap(List::stream)
            .flatMap(
                task ->
                    task.assigneeId() != null
                        ? Stream.of(task.ownerId(), task.assigneeId())
                        : Stream.of(task.ownerId()))
            .collect(Collectors.toSet());
    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(UserJpaEntity::getId, Function.identity()));
  }
}
