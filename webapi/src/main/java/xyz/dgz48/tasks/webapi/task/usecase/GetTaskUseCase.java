package xyz.dgz48.tasks.webapi.task.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TenantRole;

@Service
@RequiredArgsConstructor
public class GetTaskUseCase {

  private final TaskRepository taskRepository;
  private final TaskAuthorizationDomainService authorizationService =
      new TaskAuthorizationDomainService();

  @Transactional(readOnly = true)
  public Task getTask(Long tenantId, Long taskId, Long requestingUserId) {
    Task task =
        taskRepository
            .findByTenantIdAndId(tenantId, taskId)
            .orElseThrow(() -> new TaskNotFoundException(taskId));
    // TODO(Sprint 2): resolve TenantRole from user_tenants; load stakeholders from DB
    if (!authorizationService.canBeViewedBy(task, requestingUserId, TenantRole.MEMBER, List.of())) {
      throw new TaskNotViewableException(taskId);
    }
    return task;
  }
}
