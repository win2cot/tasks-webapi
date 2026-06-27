package xyz.dgz48.tasks.webapi.tenant.usecase;

/** 招待トークン(平文)の生成ポート。SecureRandom 256bit・URL-safe Base64(ADR-0017 §3.1)。 */
public interface InviteTokenGenerator {

  /** 新しい招待トークン(平文)を生成する。メールにのみ載せ、DB には保存しない。 */
  String generate();
}
