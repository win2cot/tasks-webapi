package xyz.dgz48.tasks.webapi.task.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStakeholder;

@Service
@RequiredArgsConstructor
public class ListStakeholdersUseCase {

  private final TaskRepository taskRepository;
  private final StakeholderRepository stakeholderRepository;
  private final TaskAuthorizationDomainService taskAuthorizationDomainService;

  @Transactional(readOnly = true)
  public List<TaskStakeholder> execute(Long taskId, Long userId) {
    Task task =
        taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    List<Long> stakeholderUserIds =
        stakeholderRepository.findUserIdsByTaskId(taskId, task.getTenantId());
    if (!taskAuthorizationDomainService.canBeViewedBy(task, userId, stakeholderUserIds)) {
      throw new TaskNotViewableException(taskId);
    }
    return stakeholderRepository.findByTaskId(taskId, task.getTenantId());
  }
}
