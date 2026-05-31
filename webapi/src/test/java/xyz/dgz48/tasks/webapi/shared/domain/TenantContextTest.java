package xyz.dgz48.tasks.webapi.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void getReturnsNullWhenNotSet() {
    assertThat(TenantContext.get()).isNull();
  }

  @Test
  void setAndGet() {
    TenantContext.set(42L);
    assertThat(TenantContext.get()).isEqualTo(42L);
  }

  @Test
  void clearRemovesValue() {
    TenantContext.set(1L);
    TenantContext.clear();
    assertThat(TenantContext.get()).isNull();
  }
}
