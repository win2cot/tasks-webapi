package xyz.dgz48.tasks.webapi.user.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.user.domain.UserStatus;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class UserJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "oidc_sub", nullable = false, unique = true, length = 255)
  private String oidcSub;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Column(name = "full_name_kana", nullable = false, length = 255)
  private String fullNameKana;

  @Nullable
  @Column(name = "department_name", length = 255)
  private String departmentName;

  @Version
  @Column(nullable = false)
  private Long version;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "ENUM('ACTIVE','INACTIVE')")
  private UserStatus status;

  @Nullable
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public boolean isAnonymized() {
    return this.deletedAt != null;
  }

  public boolean isInactive() {
    return this.status == UserStatus.INACTIVE;
  }

  public UserJpaEntity(
      String oidcSub,
      String email,
      String fullName,
      String fullNameKana,
      @Nullable String departmentName) {
    this.oidcSub = oidcSub;
    this.email = email;
    this.fullName = fullName;
    this.fullNameKana = fullNameKana;
    this.departmentName = departmentName;
    this.status = UserStatus.ACTIVE;
  }
}
