package xyz.dgz48.tasks.webapi.user.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;
import xyz.dgz48.tasks.webapi.user.domain.UserAlreadyRegisteredException;

class UserExceptionHandlerTest {

  private final UserExceptionHandler handler = new UserExceptionHandler();

  @Test
  void handleAlreadyRegisteredReturns409WithEConflictAndNoPii() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/invitations/tok/accept");

    ErrorResponse response =
        handler.handleAlreadyRegistered(new UserAlreadyRegisteredException(), request);

    assertThat(response.status()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(response.code()).isEqualTo(ErrorCode.E_CONFLICT);
    assertThat(response.path()).isEqualTo("/api/invitations/tok/accept");
    // PII(email)を含まないこと
    assertThat(response.message()).doesNotContain("@");
  }
}
