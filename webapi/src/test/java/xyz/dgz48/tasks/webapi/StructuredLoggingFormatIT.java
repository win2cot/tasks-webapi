package xyz.dgz48.tasks.webapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

/**
 * ADR-0019 §6: Boot logstash 形式の出力 JSON が現行 LogstashEncoder と同形であることを確認。
 *
 * <p>{@code LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash} 相当のプロパティを有効化した状態で
 * {@link OutputCaptureExtension} により JSON フィールド存在を検証する。
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = "logging.structured.format.console=logstash")
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class StructuredLoggingFormatIT {

  private static final Logger log = LoggerFactory.getLogger(StructuredLoggingFormatIT.class);

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void logstashFormat_standardFields_present(CapturedOutput output) {
    log.info("structured-log-test");

    assertThat(output.getAll())
        .contains("\"@timestamp\"")
        .contains("\"level\"")
        .contains("\"message\"")
        .contains("\"logger_name\"");
  }

  @Test
  void logstashFormat_mdcField_appearsInJson(CapturedOutput output) {
    MDC.put("tenantId", "99");
    log.info("mdc-field-test");

    assertThat(output.getAll()).contains("\"tenantId\"").contains("\"99\"");
  }

  @Test
  void logstashFormat_addKeyValue_appearsInJson(CapturedOutput output) {
    log.atInfo().addKeyValue("taskId", 42).setMessage("key-value-test").log();

    assertThat(output.getAll()).contains("\"taskId\"").contains("key-value-test");
  }

  @Test
  void logstashFormat_exception_hasStackTrace(CapturedOutput output) {
    log.error("error-with-exception", new IllegalStateException("test-exception-marker"));

    assertThat(output.getAll())
        .contains("\"stack_trace\"")
        .contains("test-exception-marker");
  }
}
