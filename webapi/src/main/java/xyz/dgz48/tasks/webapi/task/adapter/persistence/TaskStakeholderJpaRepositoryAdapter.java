package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.task.domain.TaskStakeholder;
import xyz.dgz48.tasks.webapi.task.usecase.StakeholderRepository;

@Component
@RequiredArgsConstructor
class TaskStakeholderJpaRepositoryAdapter implements StakeholderRepository {

  private final TaskStakeholderJpaRepository jpaRepository;

  @Override
  public List<TaskStakeholder> findByTaskId(Long taskId, Long tenantId) {
    return jpaRepository.findWithUserInfoByTaskIdAndTenantId(taskId, tenantId).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Long> findUserIdsByTaskId(Long taskId, Long tenantId) {
    return jpaRepository.findUserIdsByTaskIdAndTenantId(taskId, tenantId);
  }

  @Override
  public TaskStakeholder add(
      Long taskId, Long tenantId, Long userId, Long addedByUserId, LocalDateTime addedAt) {
    var entity = new TaskStakeholderJpaEntity(taskId, userId, tenantId, addedByUserId, addedAt);
    jpaRepository.save(entity);
    jpaRepository.flush();
    return jpaRepository.findWithUserInfoByTaskIdAndTenantId(taskId, tenantId).stream()
        .filter(p -> p.getUserId().equals(userId))
        .map(this::toDomain)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Stakeholder not found after insert"));
  }

  @Override
  public void removeByTaskIdAndUserId(Long taskId, Long userId, Long tenantId) {
    jpaRepository.deleteByTaskIdAndUserId(taskId, userId, tenantId);
  }

  @Override
  public boolean existsByTaskIdAndUserId(Long taskId, Long userId) {
    return jpaRepository.existsById(new TaskStakeholderJpaEntityId(taskId, userId));
  }

  private TaskStakeholder toDomain(StakeholderProjection p) {
    return new TaskStakeholder(
        p.getUserId(),
        p.getFullName(),
        p.getEmail(),
        p.getAddedBy(),
        p.getAddedByFullName(),
        p.getAddedAt());
  }
}
