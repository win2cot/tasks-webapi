package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.security.adapter.web.SecurityConfig;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksJwtAuthenticationConverter;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockMember;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, TasksJwtAuthenticationConverter.class, TaskExceptionHandler.class})
class TaskControllerWebMvcTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean GetTaskUseCase getTaskUseCase;

  @Autowired MockMvc mockMvc;

  private static final Long USER_ID = 1L; // matches @WithMockMember default id

  private Task buildTask() {
    return new Task(
        1L,
        100L,
        "Test task",
        "A description",
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        Visibility.TENANT,
        USER_ID,
        null,
        LocalDate.of(2026, 6, 1),
        null,
        null,
        LocalDateTime.of(2026, 5, 31, 9, 0),
        LocalDateTime.of(2026, 5, 31, 9, 0));
  }

  @Test
  @WithMockMember
  void get_returns200WithTaskJson() throws Exception {
    Task task = buildTask();
    when(getTaskUseCase.execute(1L, USER_ID)).thenReturn(task);

    mockMvc
        .perform(get("/api/tasks/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.title").value("Test task"))
        .andExpect(jsonPath("$.status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.priority").value("MEDIUM"))
        .andExpect(jsonPath("$.visibility").value("TENANT"));
  }

  @Test
  @WithMockMember
  void get_returns404_whenTaskNotFoundException() throws Exception {
    when(getTaskUseCase.execute(99L, USER_ID)).thenThrow(new TaskNotFoundException(99L));

    mockMvc
        .perform(get("/api/tasks/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  @WithMockMember
  void get_returns404_whenTaskNotViewableException() throws Exception {
    when(getTaskUseCase.execute(2L, USER_ID)).thenThrow(new TaskNotViewableException(2L));

    mockMvc
        .perform(get("/api/tasks/2"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }
}
