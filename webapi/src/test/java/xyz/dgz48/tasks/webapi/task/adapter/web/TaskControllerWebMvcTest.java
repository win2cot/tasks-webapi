package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.security.SecurityConfig;
import xyz.dgz48.tasks.webapi.security.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.TasksJwtAuthenticationConverter;
import xyz.dgz48.tasks.webapi.security.TasksPrincipal;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;
import xyz.dgz48.tasks.webapi.user.UserRepository;

@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, TasksJwtAuthenticationConverter.class, TaskExceptionHandler.class})
class TaskControllerWebMvcTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean GetTaskUseCase getTaskUseCase;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;

  private TasksAuthenticationToken authToken() {
    TasksPrincipal principal =
        new TasksPrincipal(1L, "sub-001", "user@example.com", "テスト ユーザー", "テスト ユーザー", null);
    return new TasksAuthenticationToken(principal, List.of());
  }

  @Test
  void getTask_returns200WithTask_whenAuthenticated() throws Exception {
    var now = OffsetDateTime.now();
    Task task =
        new Task(
            1L,
            1L,
            "テストタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            Visibility.TENANT,
            1L,
            null,
            LocalDate.of(2026, 12, 31),
            null,
            null,
            now,
            now);
    when(getTaskUseCase.getTask(eq(1L), eq(1L), eq(1L))).thenReturn(task);

    mockMvc
        .perform(get("/api/tasks/1").header("X-Tenant-Id", "1").with(authentication(authToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.title").value("テストタスク"))
        .andExpect(jsonPath("$.status").value("NOT_STARTED"));
  }

  @Test
  void getTask_returns404_whenNotFound() throws Exception {
    when(getTaskUseCase.getTask(eq(1L), eq(99L), eq(1L))).thenThrow(new TaskNotFoundException(99L));

    mockMvc
        .perform(get("/api/tasks/99").header("X-Tenant-Id", "1").with(authentication(authToken())))
        .andExpect(status().isNotFound());
  }

  @Test
  void getTask_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/tasks/1").header("X-Tenant-Id", "1"))
        .andExpect(status().isUnauthorized());
  }
}
