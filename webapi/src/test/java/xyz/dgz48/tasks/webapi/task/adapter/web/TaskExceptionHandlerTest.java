package xyz.dgz48.tasks.webapi.task.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingRequestHeaderException;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;

class TaskExceptionHandlerTest {

  @SuppressWarnings("unused")
  private static void placeholder(String s) {}

  private final TaskExceptionHandler handler = new TaskExceptionHandler();

  @Test
  void handleMissingHeader_returns400WithEValidation() throws NoSuchMethodException {
    Method method = TaskExceptionHandlerTest.class.getDeclaredMethod("placeholder", String.class);
    MethodParameter param = new MethodParameter(method, 0);
    MissingRequestHeaderException ex = new MissingRequestHeaderException("If-Match", param);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/tasks/1");

    ErrorResponse response = handler.handleMissingHeader(ex, request);

    assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(response.code()).isEqualTo(ErrorCode.E_VALIDATION);
    assertThat(response.message()).contains("If-Match");
    assertThat(response.path()).isEqualTo("/api/tasks/1");
  }
}
