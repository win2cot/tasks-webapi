package xyz.dgz48.tasks.webapi.task.adapter.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.task.adapter.web.dto.ChangeTaskStatusRequest;
import xyz.dgz48.tasks.webapi.task.adapter.web.dto.TaskListItemResponse;
import xyz.dgz48.tasks.webapi.task.adapter.web.dto.TaskPageResponse;
import xyz.dgz48.tasks.webapi.task.adapter.web.dto.TaskResponse;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeTaskStatusUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ListTasksUseCase;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

  private final GetTaskUseCase getTaskUseCase;
  private final ChangeTaskStatusUseCase changeTaskStatusUseCase;
  private final ListTasksUseCase listTasksUseCase;
  private final UserRepository userRepository;

  /**
   * タスク一覧取得(operationId: listTasks)。
   *
   * <p>targetDate / includeOverdue / keyword / priority 絞込は Sprint 3 で実装予定。現フェーズでは受理するが
   * 実装なし(フィルタ動作なし)。
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public ResponseEntity<TaskPageResponse> listTasks(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "dueDate,asc") String sort,
      @RequestParam(name = "status", required = false) @Nullable List<TaskStatus> statuses,
      @RequestParam(required = false) @Nullable Long ownerId,
      @RequestParam(required = false) @Nullable Long assigneeId,
      @RequestParam(required = false) @Nullable Visibility visibility,
      @RequestParam(required = false) @Nullable String targetDate,
      @RequestParam(required = false) @Nullable Boolean includeOverdue,
      @RequestParam(required = false) @Nullable String priority,
      @RequestParam(required = false) @Nullable String keyword,
      TasksAuthenticationToken token) {

    Long userId = token.getPrincipal().getId();
    Pageable pageable = buildPageable(page, size, sort);

    ListTasksUseCase.Result result =
        listTasksUseCase.execute(userId, statuses, ownerId, assigneeId, visibility, pageable);

    Page<Task> taskPage = result.taskPage();
    Map<Long, UserJpaEntity> userMap = loadUserMap(taskPage.getContent());

    List<TaskListItemResponse> content =
        taskPage.getContent().stream()
            .map(task -> TaskListItemResponse.from(task, userId, userMap))
            .toList();

    TaskPageResponse response =
        new TaskPageResponse(
            content,
            taskPage.getTotalElements(),
            taskPage.getTotalPages(),
            taskPage.getNumber(),
            taskPage.getSize(),
            result.overdueCount());

    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public ResponseEntity<TaskResponse> get(@PathVariable Long id, TasksAuthenticationToken token) {
    Task task = getTaskUseCase.execute(id, token.getPrincipal().getId());
    TaskResponse body = TaskResponse.from(task);
    return ResponseEntity.ok().header(HttpHeaders.ETAG, etagValue(task.getVersion())).body(body);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public ResponseEntity<TaskResponse> changeStatus(
      @PathVariable Long id,
      @RequestHeader(name = HttpHeaders.IF_MATCH) String ifMatch,
      @RequestBody @Valid ChangeTaskStatusRequest request,
      TasksAuthenticationToken token) {
    Long ifMatchVersion = parseIfMatchVersion(ifMatch);
    Task task =
        changeTaskStatusUseCase.execute(
            id, token.getPrincipal().getId(), request.status(), ifMatchVersion);
    TaskResponse body = TaskResponse.from(task);
    return ResponseEntity.ok().header(HttpHeaders.ETAG, etagValue(task.getVersion())).body(body);
  }

  private Map<Long, UserJpaEntity> loadUserMap(List<Task> tasks) {
    Set<Long> userIds =
        tasks.stream()
            .flatMap(
                task ->
                    task.getAssigneeId() != null
                        ? Stream.of(task.getOwnerId(), task.getAssigneeId())
                        : Stream.of(task.getOwnerId()))
            .collect(Collectors.toSet());
    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(UserJpaEntity::getId, Function.identity()));
  }

  private Pageable buildPageable(int page, int size, String sort) {
    Sort sortSpec = parseSortSpec(sort);
    return PageRequest.of(page, size, sortSpec);
  }

  private static final Set<String> VALID_SORT_FIELDS =
      Set.of("dueDate", "createdAt", "updatedAt", "title", "priority");

  private Sort parseSortSpec(String sort) {
    String[] parts = sort.split(",", 2);
    String field = parts[0].strip();
    if (!VALID_SORT_FIELDS.contains(field)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort field: " + field);
    }
    String direction = parts.length > 1 ? parts[1].strip().toLowerCase(Locale.ROOT) : "asc";
    Sort.Direction dir = "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
    return Sort.by(new Sort.Order(dir, field));
  }

  private static String etagValue(Long version) {
    return "W/\"" + version + "\"";
  }

  private static Long parseIfMatchVersion(String ifMatch) {
    String s = ifMatch.strip();
    if (s.startsWith("W/")) {
      s = s.substring(2);
    }
    if (s.startsWith("\"") && s.endsWith("\"")) {
      s = s.substring(1, s.length() - 1);
    }
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      throw new InvalidIfMatchFormatException("Invalid If-Match value: " + ifMatch);
    }
  }
}
