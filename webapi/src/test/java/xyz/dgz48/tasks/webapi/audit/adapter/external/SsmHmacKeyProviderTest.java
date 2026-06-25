package xyz.dgz48.tasks.webapi.audit.adapter.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

/** {@link SsmHmacKeyProvider} のユニットテスト。 */
@ExtendWith(MockitoExtension.class)
class SsmHmacKeyProviderTest {

  @Mock SsmClient ssmClient;

  private void stubSecret(String value) {
    when(ssmClient.getParameter(any(GetParameterRequest.class)))
        .thenReturn(
            GetParameterResponse.builder()
                .parameter(Parameter.builder().value(value).build())
                .build());
  }

  @Test
  void keyFor_loadsSecureStringWithDecryption_byPrefixAndKeyId() {
    stubSecret("ssm-secret");
    var provider = new SsmHmacKeyProvider(ssmClient, "v1", "/tasks/dev/app/audit-hash-key");

    var key = provider.keyFor("v1");

    assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    assertThat(key.getEncoded()).isEqualTo("ssm-secret".getBytes(StandardCharsets.UTF_8));

    ArgumentCaptor<GetParameterRequest> captor = ArgumentCaptor.forClass(GetParameterRequest.class);
    verify(ssmClient).getParameter(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("/tasks/dev/app/audit-hash-key-v1");
    assertThat(captor.getValue().withDecryption()).isTrue();
  }

  @Test
  void keyFor_cachesPerKeyId() {
    stubSecret("ssm-secret");
    var provider = new SsmHmacKeyProvider(ssmClient, "v1", "/tasks/dev/app/audit-hash-key");

    provider.keyFor("v1");
    provider.keyFor("v1");

    // 鍵は不変のため SSM 取得は 1 回のみ。
    verify(ssmClient, times(1)).getParameter(any(GetParameterRequest.class));
  }

  @Test
  void currentKeyId_returnsConfiguredId() {
    var provider = new SsmHmacKeyProvider(ssmClient, "v1", "/tasks/dev/app/audit-hash-key");
    assertThat(provider.currentKeyId()).isEqualTo("v1");
  }
}
