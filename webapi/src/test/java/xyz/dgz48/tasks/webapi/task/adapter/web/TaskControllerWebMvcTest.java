package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.adapter.web.SecurityConfig;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAccessDeniedHandler;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationEntryPoint;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksJwtAuthenticationConverter;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockMember;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockSaasAdmin;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockTenantAdmin;
import xyz.dgz48.tasks.webapi.shared.exception.PreconditionFailedException;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.task.usecase.AddStakeholderUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeTaskStatusUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeVisibilityUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.CreateTaskUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.DeleteTaskUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ListStakeholdersUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ListTasksUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.RemoveStakeholderUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.UpdateTaskUseCase;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(TaskController.class)
@Import({
  SecurityConfig.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class,
  TaskExceptionHandler.class
})
class TaskControllerWebMvcTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean UserTenantsResolverService userTenantsResolverService;
  @MockitoBean CreateTaskUseCase createTaskUseCase;
  @MockitoBean GetTaskUseCase getTaskUseCase;
  @MockitoBean ChangeTaskStatusUseCase changeTaskStatusUseCase;
  @MockitoBean UpdateTaskUseCase updateTaskUseCase;
  @MockitoBean DeleteTaskUseCase deleteTaskUseCase;
  @MockitoBean ChangeVisibilityUseCase changeVisibilityUseCase;
  @MockitoBean ListTasksUseCase listTasksUseCase;
  @MockitoBean ListStakeholdersUseCase listStakeholdersUseCase;
  @MockitoBean AddStakeholderUseCase addStakeholderUseCase;
  @MockitoBean RemoveStakeholderUseCase removeStakeholderUseCase;

  @Autowired MockMvc mockMvc;

  private static final Long USER_ID = 1L; // matches @WithMockMember default id

  @BeforeEach
  void stubTenantResolver() {
    BDDMockito.given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.of(new TenantMembership(1L, TenantRole.MEMBER)));
  }

  private static final long VERSION = 0L;
  private static final String IF_MATCH = "W/\"" + VERSION + "\"";

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
        LocalDateTime.of(2026, 5, 31, 9, 0),
        VERSION);
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
        LocalDateTime.of(2026, 6, 1, 10, 0),
        1L);
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
    when(changeTaskStatusUseCase.execute(1L, USER_ID, TaskStatus.DONE, VERSION)).thenReturn(done);

    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .header("If-Match", IF_MATCH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.completedAt").isNotEmpty());
  }

  @Test
  @WithMockMember
  void changeStatus_returns400_whenIfMatchHeaderMissing() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  @WithMockMember
  void changeStatus_returns400_whenIfMatchInvalidFormat() throws Exception {
    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .header("If-Match", "not-a-version")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  @WithMockMember
  void changeStatus_returns404_whenTaskNotViewable() throws Exception {
    when(changeTaskStatusUseCase.execute(2L, USER_ID, TaskStatus.DONE, VERSION))
        .thenThrow(new TaskNotViewableException(2L));

    mockMvc
        .perform(
            patch("/api/tasks/2/status")
                .header("If-Match", IF_MATCH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  @WithMockMember
  void changeStatus_returns403_whenOwnershipException() throws Exception {
    when(changeTaskStatusUseCase.execute(3L, USER_ID, TaskStatus.DONE, VERSION))
        .thenThrow(new TaskOwnershipException(3L));

    mockMvc
        .perform(
            patch("/api/tasks/3/status")
                .header("If-Match", IF_MATCH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  @WithMockMember
  void changeStatus_returns412_whenOptimisticLockingFailure() throws Exception {
    when(changeTaskStatusUseCase.execute(5L, USER_ID, TaskStatus.DONE, VERSION))
        .thenThrow(new PreconditionFailedException("バージョンが競合しています: task=5"));

    mockMvc
        .perform(
            patch("/api/tasks/5/status")
                .header("If-Match", IF_MATCH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("E_PRECONDITION_FAILED"))
        .andExpect(jsonPath("$.status").value(412));
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
                .header("If-Match", IF_MATCH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isUnauthorized());
  }

  // --- 認可マトリクス: SaaS Admin (テナントロールなし → 403) ---

  @Test
  @WithMockSaasAdmin
  void get_returns403_whenSaasAdminWithoutTenantRole() throws Exception {
    BDDMockito.given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.empty());
    mockMvc.perform(get("/api/tasks/1")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockSaasAdmin
  void changeStatus_returns403_whenSaasAdminWithoutTenantRole() throws Exception {
    BDDMockito.given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.empty());
    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .header("If-Match", IF_MATCH)
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
    when(changeTaskStatusUseCase.execute(1L, USER_ID, TaskStatus.DONE, VERSION)).thenReturn(done);

    mockMvc
        .perform(
            patch("/api/tasks/1/status")
                .header("If-Match", IF_MATCH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
  }

  // --- listTasks ---

  @Test
  @WithMockMember
  void listTasks_returns200WithPage() throws Exception {
    Task task = buildTask(TaskStatus.NOT_STARTED);
    Page<Task> taskPage = new PageImpl<>(List.of(task), PageRequest.of(0, 50), 1);
    ListTasksUseCase.Result result = new ListTasksUseCase.Result(taskPage, 0);

    BDDMockito.given(
            listTasksUseCase.execute(
                ArgumentMatchers.eq(USER_ID),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any()))
        .willReturn(result);

    UserJpaEntity ownerEntity =
        new UserJpaEntity("sub-t1", "owner@example.com", "Owner Name", "オーナーネーム", null);
    BDDMockito.given(userRepository.findAllById(ArgumentMatchers.anyCollection()))
        .willReturn(List.of(ownerEntity));

    mockMvc
        .perform(get("/api/tasks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.size").value(50))
        .andExpect(jsonPath("$.overdueCount").value(0))
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void listTasks_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockSaasAdmin
  void listTasks_returns403_whenSaasAdminWithoutTenantRole() throws Exception {
    BDDMockito.given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.empty());
    mockMvc.perform(get("/api/tasks")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockTenantAdmin
  void listTasks_returns200_whenTenantAdmin() throws Exception {
    Task task = buildTask(TaskStatus.NOT_STARTED);
    Page<Task> taskPage = new PageImpl<>(List.of(task), PageRequest.of(0, 50), 1);
    ListTasksUseCase.Result result = new ListTasksUseCase.Result(taskPage, 0);

    BDDMockito.given(
            listTasksUseCase.execute(
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
        .willReturn(result);
    BDDMockito.given(userRepository.findAllById(ArgumentMatchers.anyCollection()))
        .willReturn(List.of());

    mockMvc.perform(get("/api/tasks")).andExpect(status().isOk());
  }

  // --- createTask ---

  @Test
  @WithMockMember
  void createTask_returns201_whenValidRequest() throws Exception {
    Task created = buildTask(TaskStatus.NOT_STARTED);
    BDDMockito.given(
            createTaskUseCase.execute(
                ArgumentMatchers.any(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
        .willReturn(created);

    mockMvc
        .perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Test task",
                      "priority": "MEDIUM",
                      "visibility": "TENANT",
                      "dueDate": "2026-06-01"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("NOT_STARTED"));
  }

  @Test
  @WithMockMember
  void createTask_returns400_whenTitleIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "",
                      "priority": "MEDIUM",
                      "visibility": "TENANT",
                      "dueDate": "2026-06-01"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  void createTask_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Test task",
                      "priority": "MEDIUM",
                      "visibility": "TENANT",
                      "dueDate": "2026-06-01"
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockSaasAdmin
  void createTask_returns403_whenSaasAdminWithoutTenantRole() throws Exception {
    BDDMockito.given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.empty());
    mockMvc
        .perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Test task",
                      "priority": "MEDIUM",
                      "visibility": "TENANT",
                      "dueDate": "2026-06-01"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockTenantAdmin
  void createTask_returns201_whenTenantAdmin() throws Exception {
    Task created = buildTask(TaskStatus.NOT_STARTED);
    BDDMockito.given(
            createTaskUseCase.execute(
                ArgumentMatchers.any(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
        .willReturn(created);

    mockMvc
        .perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Tenant Admin task",
                      "priority": "HIGH",
                      "visibility": "TENANT",
                      "dueDate": "2026-06-01"
                    }
                    """))
        .andExpect(status().isCreated());
  }
}
