package xyz.dgz48.tasks.webapi.dashboard.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.audit.usecase.AuthorizationDeniedAuditService;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardSummary;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTask;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTaskSections;
import xyz.dgz48.tasks.webapi.dashboard.usecase.GetDashboardSummaryUseCase;
import xyz.dgz48.tasks.webapi.dashboard.usecase.GetDashboardTasksUseCase;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.adapter.web.SecurityConfig;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAccessDeniedHandler;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationEntryPoint;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksJwtAuthenticationConverter;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockMember;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockSaasAdmin;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockTenantAdmin;
import xyz.dgz48.tasks.webapi.security.usecase.OidcSubCorrelationService;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(DashboardController.class)
@Import({
  SecurityConfig.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class
})
class DashboardControllerWebMvcTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean AuditLogPort auditLogPort;
  @MockitoBean AuthorizationDeniedAuditService authorizationDeniedAuditService;
  @MockitoBean UserRepository userRepository;
  @MockitoBean OidcSubCorrelationService oidcSubCorrelationService;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean UserTenantsResolverService userTenantsResolverService;
  @MockitoBean GetDashboardTasksUseCase getDashboardTasksUseCase;
  @MockitoBean GetDashboardSummaryUseCase getDashboardSummaryUseCase;

  @Autowired MockMvc mockMvc;

  private static final Long USER_ID = 1L; // matches @WithMockMember default id

  @BeforeEach
  void stubTenantResolver() {
    BDDMockito.given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.of(new TenantMembership(1L, TenantRole.MEMBER)));
  }

  private static DashboardTask sampleTask() {
    return new DashboardTask(
        10L,
        0L,
        "サンプル",
        null,
        "NOT_STARTED",
        "HIGH",
        "TENANT",
        USER_ID,
        null,
        LocalDate.of(2026, 1, 15),
        null,
        LocalDateTime.of(2026, 1, 10, 9, 0),
        LocalDateTime.of(2026, 1, 10, 9, 0));
  }

  // --- getDashboardTasks ---

  @Test
  @WithMockMember
  void getTasks_returns200WithFourSections() throws Exception {
    DashboardTaskSections sections =
        new DashboardTaskSections(List.of(sampleTask()), List.of(), List.of(), List.of());
    BDDMockito.given(getDashboardTasksUseCase.execute(USER_ID, 3)).willReturn(sections);
    BDDMockito.given(userRepository.findAllById(ArgumentMatchers.anyCollection()))
        .willReturn(List.of(new UserJpaEntity("sub-1", "u1@example.com", "山田太郎", "ヤマダタロウ", null)));

    mockMvc
        .perform(get("/api/dashboard/tasks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overdue").isArray())
        .andExpect(jsonPath("$.today").isArray())
        .andExpect(jsonPath("$.upcoming").isArray())
        .andExpect(jsonPath("$.completedToday").isArray())
        .andExpect(jsonPath("$.overdue[0].id").value(10))
        .andExpect(jsonPath("$.overdue[0].status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.overdue[0].priority").value("HIGH"))
        .andExpect(jsonPath("$.overdue[0].owner.id").value(USER_ID))
        .andExpect(jsonPath("$.overdue[0].editable").value(true))
        .andExpect(jsonPath("$.overdue[0].deletable").value(true));
  }

  @Test
  @WithMockMember
  void getTasks_bindsDueWithinDaysParam() throws Exception {
    BDDMockito.given(getDashboardTasksUseCase.execute(USER_ID, 7))
        .willReturn(new DashboardTaskSections(List.of(), List.of(), List.of(), List.of()));
    BDDMockito.given(userRepository.findAllById(ArgumentMatchers.anyCollection()))
        .willReturn(List.of());

    mockMvc
        .perform(get("/api/dashboard/tasks").param("dueWithinDays", "7"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockMember
  void getTasks_returns400_whenDueWithinDaysBelowMin() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/tasks").param("dueWithinDays", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockMember
  void getTasks_returns400_whenDueWithinDaysAboveMax() throws Exception {
    mockMvc
        .perform(get("/api/dashboard/tasks").param("dueWithinDays", "15"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getTasks_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/dashboard/tasks")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockSaasAdmin
  void getTasks_returns403_whenSaasAdminWithoutTenantRole() throws Exception {
    BDDMockito.given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.empty());
    mockMvc.perform(get("/api/dashboard/tasks")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockTenantAdmin
  void getTasks_returns200_whenTenantAdmin() throws Exception {
    BDDMockito.given(
            getDashboardTasksUseCase.execute(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt()))
        .willReturn(new DashboardTaskSections(List.of(), List.of(), List.of(), List.of()));
    BDDMockito.given(userRepository.findAllById(ArgumentMatchers.anyCollection()))
        .willReturn(List.of());

    mockMvc.perform(get("/api/dashboard/tasks")).andExpect(status().isOk());
  }

  // --- getDashboardSummary ---

  @Test
  @WithMockMember
  void getSummary_returns200WithCounts() throws Exception {
    DashboardSummary summary =
        new DashboardSummary(
            2L,
            1L,
            1L,
            3L,
            Map.of("NOT_STARTED", 5L, "IN_PROGRESS", 1L, "DONE", 2L, "ON_HOLD", 0L),
            Map.of("HIGH", 1L, "MEDIUM", 5L, "LOW", 2L));
    BDDMockito.given(getDashboardSummaryUseCase.execute(USER_ID)).willReturn(summary);

    mockMvc
        .perform(get("/api/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.todayDueCount").value(2))
        .andExpect(jsonPath("$.overdueCount").value(1))
        .andExpect(jsonPath("$.completedTodayCount").value(1))
        .andExpect(jsonPath("$.myOpenCount").value(3))
        .andExpect(jsonPath("$.statusBreakdown.NOT_STARTED").value(5))
        .andExpect(jsonPath("$.statusBreakdown.DONE").value(2))
        .andExpect(jsonPath("$.priorityBreakdown.MEDIUM").value(5));
  }

  @Test
  void getSummary_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockSaasAdmin
  void getSummary_returns403_whenSaasAdminWithoutTenantRole() throws Exception {
    BDDMockito.given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.empty());
    mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isForbidden());
  }
}
