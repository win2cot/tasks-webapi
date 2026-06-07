package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

@ExtendWith(MockitoExtension.class)
class ListTasksUseCaseTest {

  private static final Long USER_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 7);

  @Mock TaskRepository taskRepository;

  @Mock Clock clock;

  @InjectMocks ListTasksUseCase useCase;

  private Task buildTask(Long id) {
    return new Task(
        id,
        TENANT_ID,
        "Task " + id,
        null,
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        Visibility.TENANT,
        USER_ID,
        null,
        LocalDate.of(2026, 7, 1),
        null,
        null,
        LocalDateTime.of(2026, 5, 1, 0, 0),
        LocalDateTime.of(2026, 5, 1, 0, 0),
        0L);
  }

  private void setupClock() {
    when(clock.getZone()).thenReturn(ZoneId.of("Asia/Tokyo"));
    when(clock.instant()).thenReturn(TODAY.atStartOfDay().toInstant(ZoneOffset.of("+09:00")));
  }

  @Test
  void execute_returnsPageAndOverdueCount() {
    setupClock();
    Pageable pageable = PageRequest.of(0, 10, Sort.by("dueDate").ascending());
    Page<Task> taskPage = new PageImpl<>(List.of(buildTask(1L), buildTask(2L)), pageable, 2);

    when(taskRepository.findVisibleTasks(
            eq(USER_ID), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
        .thenReturn(taskPage);
    when(taskRepository.countOverdueTasks(eq(USER_ID), eq(TODAY))).thenReturn(3L);

    ListTasksUseCase.Result result = useCase.execute(USER_ID, null, null, null, null, pageable);

    assertThat(result.taskPage().getContent()).hasSize(2);
    assertThat(result.overdueCount()).isEqualTo(3);
  }

  @Test
  void execute_passesFiltersToRepository() {
    setupClock();
    Pageable pageable = PageRequest.of(0, 10);
    List<TaskStatus> statuses = List.of(TaskStatus.IN_PROGRESS);
    Long ownerId = 5L;
    Long assigneeId = 6L;
    Visibility visibility = Visibility.TENANT;

    when(taskRepository.findVisibleTasks(
            eq(USER_ID), eq(statuses), eq(ownerId), eq(assigneeId), eq(visibility), eq(pageable)))
        .thenReturn(Page.empty(pageable));
    when(taskRepository.countOverdueTasks(any(), any())).thenReturn(0L);

    ListTasksUseCase.Result result =
        useCase.execute(USER_ID, statuses, ownerId, assigneeId, visibility, pageable);

    assertThat(result.taskPage().getContent()).isEmpty();
    assertThat(result.overdueCount()).isZero();
  }

  @Test
  void execute_returnsEmptyPage_whenRepositoryReturnsEmpty() {
    setupClock();
    Pageable pageable = PageRequest.of(0, 50);
    when(taskRepository.findVisibleTasks(any(), any(), any(), any(), any(), any()))
        .thenReturn(Page.empty(pageable));
    when(taskRepository.countOverdueTasks(any(), any())).thenReturn(0L);

    ListTasksUseCase.Result result = useCase.execute(USER_ID, null, null, null, null, pageable);

    assertThat(result.taskPage().isEmpty()).isTrue();
    assertThat(result.overdueCount()).isZero();
  }
}
