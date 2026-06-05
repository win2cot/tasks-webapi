package xyz.dgz48.tasks.webapi.task.adapter.web;

/** If-Match ヘッダ値のフォーマットが不正な場合の例外(HTTP 400)。 */
class InvalidIfMatchFormatException extends RuntimeException {

  InvalidIfMatchFormatException(String message) {
    super(message);
  }
}
