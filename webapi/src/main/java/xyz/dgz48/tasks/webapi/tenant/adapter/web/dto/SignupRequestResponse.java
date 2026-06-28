package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

/** POST /api/signup/request レスポンス(ADR-0040 §3.3)。email の存在有無に関わらず常に同一(列挙耐性)。到達確認はメール受信側でのみ判明する。 */
public record SignupRequestResponse(String message) {

  public static SignupRequestResponse accepted() {
    return new SignupRequestResponse("確認メールを送信しました。メールのリンクから登録を完了してください。");
  }
}
