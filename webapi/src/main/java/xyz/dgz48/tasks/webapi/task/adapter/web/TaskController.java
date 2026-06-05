package xyz.dgz48.tasks.webapi.task.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.task.adapter.web.dto.ChangeTaskStatusRequest;
import xyz.dgz48.tasks.webapi.task.adapter.web.dto.TaskResponse;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeTaskStatusUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

  private final GetTaskUseCase getTaskUseCase;
  private final ChangeTaskStatusUseCase changeTaskStatusUseCase;

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
      throw new IllegalArgumentException("Invalid If-Match value: " + ifMatch);
    }
  }
}
