package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStakeholder;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

@ExtendWith(MockitoExtension.class)
class ListStakeholdersUseCaseTest {

  private static final Long TASK_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final Long ASSIGNEE_ID = 20L;
  private static final Long STAKEHOLDER_ID = 40L;
  private static final Long OTHER_USER_ID = 30L;

  @Mock TaskRepository taskRepository;
  @Mock StakeholderRepository stakeholderRepository;
  @Mock TaskAuthorizationDomainService taskAuthorizationDomainService;
  @InjectMocks ListStakeholdersUseCase useCase;

  private Task buildTask(Visibility visibility) {
    return new Task(
        TASK_ID,
        TENANT_ID,
        "Test task",
        null,
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        visibility,
        OWNER_ID,
        ASSIGNEE_ID,
        LocalDate.of(2026, 6, 1),
        null,
        null,
        LocalDateTime.of(2026, 5, 31, 0, 0),
        LocalDateTime.of(2026, 5, 31, 0, 0),
        0L);
  }

  private TaskStakeholder buildStakeholder(Long userId) {
    return new TaskStakeholder(
        userId,
        "関係者 太郎",
        "stakeholder@example.com",
        OWNER_ID,
        "所有者 太郎",
        LocalDateTime.of(2026, 6, 1, 10, 0));
  }

  @Test
  void execute_returnsStakeholders_whenViewable() {
    Task task = buildTask(Visibility.STAKEHOLDERS);
    List<Long> stakeholderIds = List.of(STAKEHOLDER_ID);
    List<TaskStakeholder> stakeholders = List.of(buildStakeholder(STAKEHOLDER_ID));
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(stakeholderIds);
    when(taskAuthorizationDomainService.canBeViewedBy(task, STAKEHOLDER_ID, stakeholderIds))
        .thenReturn(true);
    when(stakeholderRepository.findByTaskId(TASK_ID, TENANT_ID)).thenReturn(stakeholders);

    List<TaskStakeholder> result = useCase.execute(TASK_ID, STAKEHOLDER_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getUserId()).isEqualTo(STAKEHOLDER_ID);
  }

  @Test
  void execute_throwsTaskNotViewableException_whenNotViewable() {
    Task task = buildTask(Visibility.PRIVATE);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_USER_ID, List.of()))
        .thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_USER_ID))
        .isInstanceOf(TaskNotViewableException.class);
  }
}
