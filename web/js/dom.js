// @ts-check

/**
 * 外部 HTML で定義された要素をセレクタで取得する。要素が存在しない場合は Error をスローする(fail-closed)。
 *
 * 使い分け規約:
 *   - 外部 HTML(index.html / tasks.html など)で宣言した要素を参照する場合 => mustQuery() を使用
 *   - 自部品(Web Component)が生成した要素を参照する場合 => インライン JSDoc キャストを使用
 *
 * @param {ParentNode} root - 検索起点(通常は document)
 * @param {string} selector - CSS セレクタ
 * @returns {Element} 一致した要素(null にならない)
 * @throws {Error} セレクタに一致する要素が見つからない場合
 */
function mustQuery(root, selector) {
  const el = root.querySelector(selector);
  if (!el) throw new Error(`Required element not found: "${selector}"`);
  return el;
}
