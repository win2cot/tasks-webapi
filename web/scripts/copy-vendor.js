/**
 * deploy 時に node_modules の dist ファイルを vendor/ へコピーするスクリプト。
 * バンドラ不要。S3 アップロード前に実行する。
 * 使用: node scripts/copy-vendor.js
 */

const fs = require('fs');
const path = require('path');

const nm  = path.resolve(__dirname, '../node_modules');
const dst = path.resolve(__dirname, '../vendor');

function cp(from, to) {
  const src = path.join(nm, from);
  const out = path.join(dst, to);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.cpSync(src, out, { recursive: true, force: true });
  console.log(`copied  node_modules/${from}  →  vendor/${to}`);
}

fs.mkdirSync(dst, { recursive: true });

// Bootstrap CSS + JS bundle
cp('bootstrap/dist/css/bootstrap.min.css',      'bootstrap/css/bootstrap.min.css');
cp('bootstrap/dist/js/bootstrap.bundle.min.js', 'bootstrap/js/bootstrap.bundle.min.js');

// Bootstrap Icons (CSS references ./fonts/ with relative path — preserve structure)
cp('bootstrap-icons/font/bootstrap-icons.min.css', 'bootstrap-icons/bootstrap-icons.min.css');
cp('bootstrap-icons/font/fonts',                   'bootstrap-icons/fonts');

// keycloak-js
cp('keycloak-js/dist/keycloak.min.js', 'keycloak-js/keycloak.min.js');

// Noto Sans JP via @fontsource (CSS references ./files/ with relative path — preserve structure)
['400', '500', '700'].forEach(weight => {
  cp(`@fontsource/noto-sans-jp/${weight}.css`, `fontsource/noto-sans-jp/${weight}.css`);
});
cp('@fontsource/noto-sans-jp/files', 'fontsource/noto-sans-jp/files');

console.log('\nvendor/ ready.');
