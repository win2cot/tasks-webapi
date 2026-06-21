package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;
import xyz.dgz48.tasks.webapi.tenant.usecase.ListTenantUsersPort;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@Observed(name = "tenant.repository")
@Component
@RequiredArgsConstructor
class ListTenantUsersAdapter implements ListTenantUsersPort {

  private final UserTenantJpaRepository userTenantRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public List<TenantUserInfo> findTenantUsers(Long tenantId) {
    List<UserTenantJpaEntity> memberships =
        userTenantRepository.findByIdTenantIdOrderByJoinedAtAsc(tenantId);

    List<Long> userIds = memberships.stream().map(m -> m.getId().getUserId()).toList();
    Map<Long, UserJpaEntity> userMap =
        userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(UserJpaEntity::getId, Function.identity()));

    return memberships.stream()
        .flatMap(
            m ->
                Optional.ofNullable(userMap.get(m.getId().getUserId()))
                    .map(
                        user ->
                            new TenantUserInfo(
                                m.getId().getUserId(),
                                user.getEmail(),
                                user.getFullName(),
                                user.getDepartmentName(),
                                m.getRole(),
                                m.getStatus(),
                                m.getJoinedAt()))
                    .stream())
        .toList();
  }
}
