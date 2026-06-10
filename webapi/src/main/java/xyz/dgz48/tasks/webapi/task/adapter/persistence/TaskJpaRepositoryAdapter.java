package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.shared.exception.PreconditionFailedException;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.task.usecase.TaskRepository;

@Component
@RequiredArgsConstructor
class TaskJpaRepositoryAdapter implements TaskRepository {

  private final TaskJpaRepository jpaRepository;
  private final EntityManager em;

  @Override
  public Optional<Task> findById(Long id) {
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public Task create(
      Long tenantId,
      Long ownerId,
      String title,
      @Nullable String description,
      Priority priority,
      Visibility visibility,
      @Nullable Long assigneeId,
      LocalDate dueDate) {
    TaskJpaEntity entity =
        new TaskJpaEntity(
            tenantId,
            ownerId,
            title,
            description,
            TaskStatus.NOT_STARTED,
            priority,
            visibility,
            assigneeId,
            dueDate);
    return toDomain(jpaRepository.saveAndFlush(entity));
  }

  @Override
  public Task save(Task task) {
    TaskJpaEntity entity =
        jpaRepository
            .findById(task.getId())
            .orElseThrow(
                () -> new IllegalStateException("Task not found for save: " + task.getId()));
    entity.updateFields(
        task.getTitle(),
        task.getDescription(),
        task.getPriority(),
        task.getAssigneeId(),
        task.getDueDate(),
        task.getStatus(),
        task.getCompletedAt(),
        task.getVisibility());
    try {
      return toDomain(jpaRepository.saveAndFlush(entity));
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new PreconditionFailedException("バージョンが競合しています: task=" + task.getId());
    }
  }

  @Override
  public Page<Task> findVisibleTasks(
      Long userId,
      @Nullable List<TaskStatus> statuses,
      @Nullable Long ownerId,
      @Nullable Long assigneeId,
      @Nullable Visibility visibility,
      Pageable pageable) {

    CriteriaBuilder cb = em.getCriteriaBuilder();

    long total = executeCountQuery(cb, userId, statuses, ownerId, assigneeId, visibility);
    if (total == 0) {
      return Page.empty(pageable);
    }

    CriteriaQuery<TaskJpaEntity> dataQuery = cb.createQuery(TaskJpaEntity.class);
    Root<TaskJpaEntity> task = dataQuery.from(TaskJpaEntity.class);
    dataQuery.where(
        buildPredicates(cb, dataQuery, task, userId, statuses, ownerId, assigneeId, visibility));
    dataQuery.orderBy(buildOrders(cb, task, pageable.getSort()));

    List<TaskJpaEntity> result =
        em.createQuery(dataQuery)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();

    return new PageImpl<>(result.stream().map(this::toDomain).toList(), pageable, total);
  }

  @Override
  public long countOverdueTasks(Long userId, LocalDate today) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<TaskJpaEntity> task = query.from(TaskJpaEntity.class);

    Predicate authPredicate = buildAuthPredicate(cb, query, task, userId);

    query
        .select(cb.count(task))
        .where(
            cb.isNull(task.get("deletedAt")),
            cb.lessThan(task.get("dueDate"), today),
            cb.notEqual(task.get("status"), TaskStatus.DONE),
            authPredicate);

    Long count = em.createQuery(query).getSingleResult();
    return count != null ? count : 0L;
  }

  private long executeCountQuery(
      CriteriaBuilder cb,
      Long userId,
      @Nullable List<TaskStatus> statuses,
      @Nullable Long ownerId,
      @Nullable Long assigneeId,
      @Nullable Visibility visibility) {

    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<TaskJpaEntity> countRoot = countQuery.from(TaskJpaEntity.class);
    countQuery
        .select(cb.count(countRoot))
        .where(
            buildPredicates(
                cb, countQuery, countRoot, userId, statuses, ownerId, assigneeId, visibility));
    Long result = em.createQuery(countQuery).getSingleResult();
    return result != null ? result : 0L;
  }

  private <T> Predicate[] buildPredicates(
      CriteriaBuilder cb,
      CriteriaQuery<T> query,
      Root<TaskJpaEntity> task,
      Long userId,
      @Nullable List<TaskStatus> statuses,
      @Nullable Long ownerId,
      @Nullable Long assigneeId,
      @Nullable Visibility visibility) {

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.isNull(task.get("deletedAt")));
    predicates.add(buildAuthPredicate(cb, query, task, userId));

    if (statuses != null && !statuses.isEmpty()) {
      predicates.add(task.get("status").in(statuses));
    }
    if (ownerId != null) {
      predicates.add(cb.equal(task.get("ownerId"), ownerId));
    }
    if (assigneeId != null) {
      predicates.add(cb.equal(task.get("assigneeId"), assigneeId));
    }
    if (visibility != null) {
      predicates.add(cb.equal(task.get("visibility"), visibility));
    }

    return predicates.toArray(Predicate[]::new);
  }

  /** visibility 3 役割評価認可述語(ADR-0005)。Hibernate Filter による tenant_id 絞込は別途自動適用。 */
  private <T> Predicate buildAuthPredicate(
      CriteriaBuilder cb, CriteriaQuery<T> query, Root<TaskJpaEntity> task, Long userId) {

    Subquery<Long> stakeholderSubq = query.subquery(Long.class);
    Root<TaskStakeholderJpaEntity> ts = stakeholderSubq.from(TaskStakeholderJpaEntity.class);
    stakeholderSubq
        .select(ts.get("taskId"))
        .where(cb.equal(ts.get("taskId"), task.get("id")), cb.equal(ts.get("userId"), userId));

    return cb.or(
        cb.equal(task.get("visibility"), Visibility.TENANT),
        cb.equal(task.get("ownerId"), userId),
        cb.equal(task.get("assigneeId"), userId),
        cb.and(
            cb.equal(task.get("visibility"), Visibility.STAKEHOLDERS), cb.exists(stakeholderSubq)));
  }

  private List<Order> buildOrders(CriteriaBuilder cb, Root<TaskJpaEntity> task, Sort sort) {
    if (sort.isUnsorted()) {
      return List.of(cb.asc(task.get("dueDate")));
    }
    List<Order> orders = new ArrayList<>();
    for (Sort.Order sortOrder : sort) {
      String prop = sortOrder.getProperty();
      if ("priority".equals(prop)) {
        orders.add(buildPriorityOrder(cb, task, sortOrder.isAscending()));
      } else {
        Expression<?> expr = task.get(toJpaField(prop));
        orders.add(sortOrder.isAscending() ? cb.asc(expr) : cb.desc(expr));
      }
    }
    return orders;
  }

  /**
   * priority ソート: HIGH=3 / MEDIUM=2 / LOW=1 に変換する。
   *
   * <p>desc → HIGH(3)→MEDIUM(2)→LOW(1) = 重要度高い順(API 利用者の直感に合致)。
   */
  private Order buildPriorityOrder(CriteriaBuilder cb, Root<TaskJpaEntity> task, boolean asc) {
    Expression<Integer> priorityOrdinal =
        cb.<Integer>selectCase()
            .when(cb.equal(task.get("priority"), Priority.HIGH), 3)
            .when(cb.equal(task.get("priority"), Priority.MEDIUM), 2)
            .otherwise(1);
    return asc ? cb.asc(priorityOrdinal) : cb.desc(priorityOrdinal);
  }

  /** API の sort フィールド名を JPA エンティティのフィールド名に変換する。 */
  private String toJpaField(String apiField) {
    return switch (apiField) {
      case "dueDate" -> "dueDate";
      case "createdAt" -> "createdAt";
      case "updatedAt" -> "updatedAt";
      case "title" -> "title";
      default -> throw new IllegalArgumentException("Unknown sort field: " + apiField);
    };
  }

  @Override
  public void softDelete(Task task, LocalDateTime deletedAt) {
    TaskJpaEntity entity =
        jpaRepository
            .findById(task.getId())
            .orElseThrow(
                () -> new IllegalStateException("Task not found for softDelete: " + task.getId()));
    entity.markDeleted(deletedAt);
    try {
      jpaRepository.saveAndFlush(entity);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new PreconditionFailedException("バージョンが競合しています: task=" + task.getId());
    }
  }

  private Task toDomain(TaskJpaEntity entity) {
    return new Task(
        entity.getId(),
        entity.getTenantId(),
        entity.getTitle(),
        entity.getDescription(),
        entity.getStatus(),
        entity.getPriority(),
        entity.getVisibility(),
        entity.getOwnerId(),
        entity.getAssigneeId(),
        entity.getDueDate(),
        entity.getCompletedAt(),
        entity.getDeletedAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getVersion());
  }
}
