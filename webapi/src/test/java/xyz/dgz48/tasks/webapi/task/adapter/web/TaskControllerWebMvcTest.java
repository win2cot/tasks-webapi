package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.adapter.web.SecurityConfig;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksJwtAuthenticationConverter;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockMember;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockSaasAdmin;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockTenantAdmin;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeTaskStatusUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, TasksJwtAuthenticationConverter.class, TaskExceptionHandler.class})
class TaskControllerWebMvcTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean GetTaskUseCase getTaskUseCase;
  @MockitoBean ChangeTaskStatusUseCase changeTaskStatusUseCase;

  @Autowired MockMvc mockMvc;

  private static final Long USER_ID = 1L; // matches @WithMockMember default id

  private Task buildTask(TaskStatus status) {
    return new Task(
        1L,
        100L,
        "Test task",
        "A description",
        status,
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

  private Task buildDoneTask() {
    return new Task(
        1L,
        100L,
        "Test task",
        "A description",
        TaskStatus.DONE,
        Priority.MEDIUM,
        Visibility.TENANT,
        USER_ID,
        null,
        LocalDate.of(2026, 6, 1),
        LocalDateTime.of(2026, 6, 1, 10, 0),
        null,
        LocalDateTime.of(2026, 5, 31, 9, 0),
        LocalDateTime.of(2026, 6, 1, 10, 0));
  }

  @Test
  @WithMockMember
  void get_returns200WithTaskJson() throws Exception {
    Task task = buildTask(TaskStatus.NOT_STARTED);
    when(getTaskUseCase.execute(1L, USER_ID)).thenReturn(task);

    mockMvc
        .perform(get("/api/tasks/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.title").value("Test task"))
        .andExpect(jsonPath("$.status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.priority").value("MEDIUM"))
        .andExpect(jsonPath("$.visibility").value("TENANT"))
        .andExpect(jsonPath("$.completedAt").doesNotExist());
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

  @Test
  @WithMockMember
  void changeStatus_returns200WithCompletedAt_whenDone() throws Exception {
    Task done = buildDoneTask();
    when(changeTaskStatusUseCase.execute(1L, USER_ID, TaskStatus.DONE)).thenReturn(done);

    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.completedAt").isNotEmpty());
  }

  @Test
  @WithMockMember
  void changeStatus_returns404_whenTaskNotViewable() throws Exception {
    when(changeTaskStatusUseCase.execute(2L, USER_ID, TaskStatus.DONE))
        .thenThrow(new TaskNotViewableException(2L));

    mockMvc
        .perform(
            patch("/api/tasks/2/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  @WithMockMember
  void changeStatus_returns403_whenOwnershipException() throws Exception {
    when(changeTaskStatusUseCase.execute(3L, USER_ID, TaskStatus.DONE))
        .thenThrow(new TaskOwnershipException(3L));

    mockMvc
        .perform(
            patch("/api/tasks/3/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  // --- 認可マトリクス: 未認証 (401) ---

  @Test
  void get_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/tasks/1")).andExpect(status().isUnauthorized());
  }

  @Test
  void changeStatus_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isUnauthorized());
  }

  // --- 認可マトリクス: SaaS Admin (テナントロールなし → 403) ---

  @Test
  @WithMockSaasAdmin
  void get_returns403_whenSaasAdminWithoutTenantRole() throws Exception {
    mockMvc.perform(get("/api/tasks/1")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockSaasAdmin
  void changeStatus_returns403_whenSaasAdminWithoutTenantRole() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isForbidden());
  }

  // --- 認可マトリクス: Tenant Admin (200) ---

  @Test
  @WithMockTenantAdmin
  void get_returns200_whenTenantAdmin() throws Exception {
    Task task = buildTask(TaskStatus.NOT_STARTED);
    when(getTaskUseCase.execute(1L, USER_ID)).thenReturn(task);

    mockMvc
        .perform(get("/api/tasks/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  @WithMockTenantAdmin
  void changeStatus_returns200_whenTenantAdmin() throws Exception {
    Task done = buildDoneTask();
    when(changeTaskStatusUseCase.execute(1L, USER_ID, TaskStatus.DONE)).thenReturn(done);

    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
  }
}
