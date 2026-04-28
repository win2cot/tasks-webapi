package xyz.dgz48.tasks.webapi.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init")
public class Task {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ENUM('INCOMPLETE','COMPLETE')")
  private TaskStatus status;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(nullable = false, length = 255)
  private String title;

  @Nullable
  @Column(columnDefinition = "LONGTEXT")
  private String body;

  public Task(TaskStatus status, Long ownerId, String title, @Nullable String body) {
    this.status = status;
    this.ownerId = ownerId;
    this.title = title;
    this.body = body;
  }
}
