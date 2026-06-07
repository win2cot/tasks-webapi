package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import xyz.dgz48.tasks.webapi.task.domain.TaskStakeholder;

public record StakeholderResponse(
    Long userId, String fullName, String email, UserSummary addedBy, OffsetDateTime addedAt) {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  public record UserSummary(Long id, String fullName) {}

  public static StakeholderResponse from(TaskStakeholder s) {
    return new StakeholderResponse(
        s.getUserId(),
        s.getFullName(),
        s.getEmail(),
        new UserSummary(s.getAddedById(), s.getAddedByFullName()),
        s.getAddedAt().atZone(JST).toOffsetDateTime());
  }
}
