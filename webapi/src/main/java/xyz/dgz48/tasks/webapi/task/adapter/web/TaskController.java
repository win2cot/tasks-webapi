package xyz.dgz48.tasks.webapi.task.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
  public TaskResponse get(@PathVariable Long id, TasksAuthenticationToken token) {
    Task task = getTaskUseCase.execute(id, token.getPrincipal().getId());
    return TaskResponse.from(task);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MEMBER')")
  public TaskResponse changeStatus(
      @PathVariable Long id,
      @RequestBody @Valid ChangeTaskStatusRequest request,
      TasksAuthenticationToken token) {
    Task task = changeTaskStatusUseCase.execute(id, token.getPrincipal().getId(), request.status());
    return TaskResponse.from(task);
  }
}
