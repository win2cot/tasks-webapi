# tasks-webapi
タスク管理システム（業務WebAPI）

## OpenAPI Lint

`api/openapi.yaml` の構文・規約準拠は [Spectral](https://stoplight.io/open-source/spectral) で検証します。
CI(`.github/workflows/openapi-lint.yml`)が PR ごとに自動実行します。

ローカルで同じチェックを実行する場合:

```bash
npx --yes @stoplight/spectral-cli@6 lint api/openapi.yaml
```
