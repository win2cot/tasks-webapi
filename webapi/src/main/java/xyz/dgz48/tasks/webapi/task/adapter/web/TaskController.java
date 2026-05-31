package xyz.dgz48.tasks.webapi.task.adapter.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.task.adapter.web.dto.TaskResponse;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

  private final GetTaskUseCase getTaskUseCase;

  @GetMapping("/{id}")
  public TaskResponse get(@PathVariable Long id, TasksAuthenticationToken token) {
    Task task = getTaskUseCase.execute(id, token.getPrincipal().getId());
    return TaskResponse.from(task);
  }
}
