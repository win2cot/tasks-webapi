# Runbook: ソークテスト実施手順(メモリリーク検知)

最終更新: 2026-06-21 | Issue #587 / ADR-0029 §3.3/§6.3/§6.4

## 概要

本 runbook は tasks-webapi のメモリリーク検知を目的とした**ソークテスト**の実施手順を記録する。
テストはやり直しを前提に設計しており、コマンドをそのまま再実行できる。

| モード | 実施環境 | 主な観測指標 |
|---|---|---|
| JVM モード | ローカル(`bootRun`) | `jvm.memory.usage.after.gc`(GC 後ヒープ使用量) |
| Native Image モード | AWS dev ECS Fargate | コンテナ RSS(Container Insights) |

**ヒープダンプはリーク確定診断用**であり、ソークテスト本体とは別フェーズ。
取得手順は「[ヒープダンプ取得](#ヒープダンプ取得)」を参照。

---

## 前提条件

### JVM モード

- Docker Compose が起動済み(MySQL + Keycloak): [fullstack-startup.md](fullstack-startup.md) 参照
- `.env.local` が存在する

### Native Image モード

- dev で稼働中の webapi イメージが `--enable-monitoring=heapdump,jfr` を含むこと(下記参照)
- AWS CLI が設定済み: `aws sts get-caller-identity` で疎通確認

---

## 診断バリアントは不要(単一イメージ方針)

ADR-0029 §6.3(2026-06-22 #587 改訂)により、`--enable-monitoring=heapdump,jfr` は
**全環境共通の単一イメージに常時付与**される([build.gradle](../../webapi/build.gradle) の
`BP_NATIVE_IMAGE_BUILD_ARGUMENTS`)。したがって**ソークテスト用に別ビルドを作る必要はない**。
通常のリリースイメージ(`vX.Y.Z`)をそのまま使う。

> **重要**: この変更は #587 のコミット以降のイメージに含まれる。dev で稼働中のイメージが
> 古い場合(フラグ未収録)は、最新版を [deploy.yml](../../.github/workflows/deploy.yml) で
> デプロイしてから実施すること。収録有無は heapdump 取得が成功するかで判定できる。

本番安全性はインフラ制御で担保する(フラグはバイナリに入っているが起動・取得経路を塞ぐ):

| 制御 | dev(ソークテスト時) | prd |
|---|---|---|
| ECS Exec | **有効化**(`--enable-execute-command`) | 無効(既定) |
| ヘルパーサイドカー | 追加する | 追加しない |
| ダンプ出力先の永続ボリューム | マウントしない(ephemeral で可) | マウントしない |

---

## JVM モードのソークテスト手順(ローカル)

### 1. アプリ起動

```bash
# ターミナル A
source .env.local && ./gradlew :webapi:bootRun
```

起動確認:

```bash
curl -s http://localhost:8080/actuator/health
# {"status":"UP"}
```

JVM プロセスの PID を取得:

```bash
# ターミナル B で実行
WEBAPI_PID=$(jcmd | grep "xyz.dgz48.tasks.webapi" | awk '{print $1}')
echo "PID: ${WEBAPI_PID}"
```

### 2. 負荷をかける

```bash
# JWT トークンを取得(Keycloak がローカルで起動済みの場合)
# client_id / user は keycloak/realm-export/tasks-realm.json + dev seed (V1.0.0_02) に準拠。
# トークンは既定 5 分で失効するため、長時間ループでは適宜再取得する(下記ループは再取得込み)。
get_token() {
  curl -s -X POST \
    http://localhost:18080/realms/tasks/protocol/openid-connect/token \
    -d "client_id=tasks-webapi" \
    -d "grant_type=password" \
    -d "username=tenant1-admin@example.com" \
    -d "password=password" \
    | jq -r '.access_token'
}

# 継続的に負荷をかけるループ(Ctrl+C で停止)
# 約 5 req/s、ターミナル C で実行。4 分ごとにトークンを再取得して失効を回避。
TOKEN=$(get_token); LAST=$(date +%s)
while true; do
  NOW=$(date +%s)
  if [ $((NOW - LAST)) -ge 240 ]; then TOKEN=$(get_token); LAST=$NOW; fi
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "X-Tenant-Id: 1" \
    http://localhost:8080/api/tasks
  sleep 0.2
done
```

### 3. ヒープ使用量を定期記録

```bash
# ターミナル D: 5 分おきに GC 後ヒープ使用量を CSV 記録(4〜8 時間続ける)
# NMT(VM.native_memory)は起動時 -XX:NativeMemoryTracking が必要なため使わず、
# GC.run → GC.heap_info の used/committed をパースする(追加フラグ不要・G1GC 前提)。
WEBAPI_PID=$(jcmd | grep "xyz.dgz48.tasks.webapi" | awk '{print $1}')
LOG_FILE="soak-jvm-$(date +%Y%m%d-%H%M).csv"

echo "time,heap_used_after_gc_kb,heap_committed_kb" > ${LOG_FILE}

while true; do
  TS=$(date +%Y-%m-%dT%H:%M:%S)
  # GC を明示実行し「GC 後」ヒープ使用量を観測する(リーク判定の主指標)
  jcmd ${WEBAPI_PID} GC.run >/dev/null 2>&1
  LINE=$(jcmd ${WEBAPI_PID} GC.heap_info 2>/dev/null | grep -iE 'reserved')
  USED=$(echo "$LINE" | grep -oE 'used [0-9]+K' | grep -oE '[0-9]+')
  COMMITTED=$(echo "$LINE" | grep -oE 'committed [0-9]+K' | grep -oE '[0-9]+')
  echo "${TS},${USED},${COMMITTED}" | tee -a ${LOG_FILE}
  sleep 300  # 5 分
done
```

> **補足**: `jvm.memory.usage.after.gc` を HTTP で取得したい場合は、
> `application.yml` の `management.endpoints.web.exposure.include` に `metrics` を追記してから再起動する。
> ソークテスト専用の一時設定として `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics`
> 環境変数で上書きしても可。
>
> ```bash
> # 取得例(metrics 公開後)
> curl -s http://localhost:8080/actuator/metrics/jvm.memory.usage.after.gc | jq .
> ```

### 4. JFR 記録(オプション: より詳細なアロケーション解析)

```bash
# JFR 記録開始(4〜8 時間分)
jcmd ${WEBAPI_PID} JFR.start \
  name=soak \
  filename=soak-$(date +%Y%m%d-%H%M).jfr \
  duration=8h \
  settings=profile

# 途中でダンプしたい場合
jcmd ${WEBAPI_PID} JFR.dump name=soak filename=soak-interim-$(date +%H%M).jfr

# 停止
jcmd ${WEBAPI_PID} JFR.stop name=soak
```

### 5. 結果の判定

| 観測値 | 正常 | 要注意 |
|---|---|---|
| GC 後ヒープ使用量 | 増加せず横ばい | 右肩上がりで増加し続ける |
| `jvm.gc.pause` 頻度 | 一定 | 増加傾向 |
| スレッド数(`jvm.threads.live`) | 一定 | 増加し続ける |

---

## Native Image モードのソークテスト手順(AWS dev)

### 1. 稼働イメージの確認

通常のリリースイメージに診断フラグが含まれる(「[診断バリアントは不要(単一イメージ方針)](#診断バリアントは不要単一イメージ方針)」参照)。dev で最新版が稼働していることを確認する。

```bash
# サービスの稼働確認
aws ecs describe-services \
  --cluster tasks-dev-cluster \
  --services tasks-dev-webapi \
  --query 'services[0].{status:status,running:runningCount,desired:desiredCount}'
```

### 2. 負荷をかける(ローカルから AWS dev へ)

```bash
# dev の JWT トークンを取得(client_id / user は dev seed に準拠。
# dev のテストユーザー・パスワードは環境で異なる場合があるため Parameter Store / realm 設定で確認)
get_dev_token() {
  curl -s -X POST \
    https://auth-dev.dgz48.xyz/realms/tasks/protocol/openid-connect/token \
    -d "client_id=tasks-webapi" \
    -d "grant_type=password" \
    -d "username=tenant1-admin@example.com" \
    -d "password=password" \
    | jq -r '.access_token'
}

# 継続的に負荷をかけるループ(Ctrl+C で停止)。4 分ごとにトークン再取得。
DEV_TOKEN=$(get_dev_token); LAST=$(date +%s)
while true; do
  NOW=$(date +%s)
  if [ $((NOW - LAST)) -ge 240 ]; then DEV_TOKEN=$(get_dev_token); LAST=$NOW; fi
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer ${DEV_TOKEN}" \
    -H "X-Tenant-Id: 1" \
    https://api-dev.tasks.dgz48.xyz/api/tasks
  sleep 0.2
done
```

### 3. RSS トレンドを CloudWatch で確認

```bash
# CLI で 5 分おきの RSS 平均を取得(最大 8 時間分)
CLUSTER=tasks-dev-cluster
SERVICE=tasks-dev-webapi

aws cloudwatch get-metric-statistics \
  --namespace ECS/ContainerInsights \
  --metric-name MemoryUtilized \
  --dimensions \
    Name=ClusterName,Value=${CLUSTER} \
    Name=ServiceName,Value=${SERVICE} \
  --start-time $(date -u -d '8 hours ago' +%Y-%m-%dT%H:%M:%SZ) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ) \
  --period 300 \
  --statistics Average \
  --query 'sort_by(Datapoints, &Timestamp)[*].{time:Timestamp,rss_mb:Average}' \
  --output table
```

CloudWatch コンソールで確認する場合:

- メトリクス → ECS/ContainerInsights → ClusterName / ServiceName
- `MemoryUtilized` を選択、期間 5 分、統計 Average でグラフ化

### 4. 結果の判定

| 観測値 | 正常 | 要注意 |
|---|---|---|
| RSS (`MemoryUtilized`) | 増加せず横ばい | 右肩上がりで増加し続ける |

---

## ヒープダンプ取得

ソークテストで右肩上がりを検知した場合の確定診断手順。

### JVM モード(ローカル)

```bash
# jcmd で取得(アプリ実行中)
WEBAPI_PID=$(jcmd | grep "xyz.dgz48.tasks.webapi" | awk '{print $1}')
# GC.heap_dump の filename は positional 引数(filename= プレフィックスは付けない)。
# パスは「対象 JVM の」作業ディレクトリ基準で解決されるため絶対パス推奨。
jcmd ${WEBAPI_PID} GC.heap_dump $(pwd)/heap-$(date +%Y%m%d-%H%M).hprof

# 取得後、Eclipse MAT で開く
# File > Open Heap Dump... → 上記 .hprof を選択
# Reports > Leak Suspects で確定診断
```

### Native Image モード(AWS dev) — distroless 制約と対処

> **制約**: run image(`ubuntu-noble-run-tiny`)は scratch + 8 deb のみの distroless 構成で、
> シェル・`kill` コマンドが存在しない。ECS Exec でシェルに入れないため、
> SIGUSR1 を直接送る通常手順は使用できない。

#### 対処 A: ヘルパーサイドカーを使った診断タスク定義(推奨)

PID 名前空間をコンテナ間で共有し、`busybox` サイドカーから SIGUSR1 を送る。

```bash
# 現在の Task Definition をベースに診断用に変更する
TASK_DEF=$(aws ecs describe-task-definition \
  --task-definition tasks-dev-webapi \
  --query taskDefinition --output json)

# サイドカーを追加し pidMode=task を設定した診断用 Task Definition を登録
DIAG_TASK_DEF=$(echo "${TASK_DEF}" | jq '
  .pidMode = "task" |
  .containerDefinitions += [{
    "name": "helper",
    "image": "busybox:stable",
    "essential": false,
    "command": ["sh", "-c", "tail -f /dev/null"],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/tasks-dev/webapi",
        "awslogs-region": "ap-northeast-1",
        "awslogs-stream-prefix": "helper"
      }
    }
  }] |
  del(.taskDefinitionArn, .revision, .status, .requiresAttributes,
      .compatibilities, .registeredAt, .registeredBy)')

DIAG_REVISION=$(aws ecs register-task-definition \
  --cli-input-json "${DIAG_TASK_DEF}" \
  --query 'taskDefinition.taskDefinitionArn' --output text)

# enable_execute_command を有効にしてサービスを更新
aws ecs update-service \
  --cluster tasks-dev-cluster \
  --service tasks-dev-webapi \
  --task-definition ${DIAG_REVISION} \
  --enable-execute-command \
  --force-new-deployment

echo "diagnostic task definition deployed: ${DIAG_REVISION}"
```

> **IAM 権限(一時付与・Terraform には残さない)**: ECS Exec は dev でも常時開かない方針のため、
> 検証中だけ Task Role にインラインポリシーを**ローカルから一時付与**し、終了後に撤去する
> (Terraform 管理外のため state drift も発生しない)。本番では付与しない。
>
> ```bash
> TASK_ROLE=tasks-dev-webapi-task-role
>
> # 一時付与
> aws iam put-role-policy \
>   --role-name ${TASK_ROLE} \
>   --policy-name soak-ecs-exec-temp \
>   --policy-document '{
>     "Version": "2012-10-17",
>     "Statement": [{
>       "Sid": "EcsExecSsmMessages",
>       "Effect": "Allow",
>       "Action": [
>         "ssmmessages:CreateControlChannel",
>         "ssmmessages:CreateDataChannel",
>         "ssmmessages:OpenControlChannel",
>         "ssmmessages:OpenDataChannel"
>       ],
>       "Resource": "*"
>     }]
>   }'
> ```
>
> 撤去は「[ソークテスト後の後片付け](#ソークテスト後の後片付け)」を参照。

タスクが起動したら `helper` サイドカーから SIGUSR1 を送る:

```bash
TASK_ARN=$(aws ecs list-tasks \
  --cluster tasks-dev-cluster \
  --service-name tasks-dev-webapi \
  --query 'taskArns[0]' --output text)

# helper サイドカーから webapi プロセス(PID=1)に SIGUSR1 を送信
# → --enable-monitoring=heapdump により /tmp/heapdump-<pid>.hprof が生成される
aws ecs execute-command \
  --cluster tasks-dev-cluster \
  --task ${TASK_ARN} \
  --container helper \
  --interactive \
  --command "kill -USR1 1"
```

#### 対処 B: OOM を意図的に発生させてダンプを取得

`--enable-monitoring=heapdump` は OOM 時にも自動でヒープダンプを書き出す。
リーク確認済みの場合は負荷を増やして OOM を誘発し、ダンプを取得する方法もある。
ただしサービス断が発生するため、本番同等トラフィックがある場合は使用しない。

#### ヒープダンプの取り出し(共有ボリューム必須)

> **重要**: `pidMode=task` はプロセス名前空間を共有するだけで、**コンテナ間のファイルシステムは共有されない**。
> webapi が書いたダンプを helper から読むには、両コンテナに**共有ボリュームをマウント**する必要がある。
> 上記「対処 A」の診断 task definition に以下を追加する(Fargate の ephemeral ボリューム)。
>
> ```jsonc
> // DIAG_TASK_DEF の jq に追記:
> //   .volumes += [{"name": "dump"}] |
> //   (.containerDefinitions[] | select(.name=="webapi") | .mountPoints) += [{"sourceVolume":"dump","containerPath":"/dump"}] |
> //   (.containerDefinitions[] | select(.name=="helper") | .mountPoints) += [{"sourceVolume":"dump","containerPath":"/dump"}]
> ```
>
> ダンプの出力先(SIGUSR1 トリガ時)は GraalVM のデフォルトで対象プロセスの作業ディレクトリになる。
> **共有ボリューム `/dump` に確実に書かせる方法(作業ディレクトリ指定 or `-XX:HeapDumpPath` 相当)は
> 初回の AWS dev 検証で実測して確定する**(#587 の検証タスク)。確定後、本節を実コマンドで更新する。

```bash
# 共有ボリュームマウント後、helper から確認(パスは実測で確定)
aws ecs execute-command \
  --cluster tasks-dev-cluster --task ${TASK_ARN} --container helper \
  --interactive --command "ls -lh /dump/"

# 取り出しは helper(aws-cli 同梱イメージ)から S3 へ:
#   helper image を amazonlinux:2023 にし `aws s3 cp /dump/<file>.hprof s3://...` を実行
#   busybox には aws-cli が無いため、取り出しが必要なら amazonlinux:2023 を使う
```

---

## ソークテスト後の後片付け

### JVM モード(ローカル)

```bash
# 1. 負荷ループ・記録ループを停止(各ターミナルで Ctrl+C)

# 2. webapi(bootRun)を停止
#    フォアグラウンドなら Ctrl+C。バックグラウンド起動していれば PID を kill:
WEBAPI_PID=$(jcmd | grep "xyz.dgz48.tasks.webapi" | awk '{print $1}')
[ -n "${WEBAPI_PID}" ] && kill ${WEBAPI_PID}

# 3. 依存サービスを停止
docker compose -f docker-compose.local.yml down

# 4. 取得物(ヒープダンプ・JFR は数十〜百 MB)を退避 or 削除
#    解析が済んだら不要な hprof / jfr を削除する
ls -lh *.hprof *.jfr soak-jvm-*.csv 2>/dev/null
# rm -f *.hprof *.jfr   # 解析完了後に削除
```

### Native Image モード

検証で開いた経路をすべて閉じる(helper サイドカー除去・ECS Exec 無効化・一時 IAM 撤去)。

```bash
# 1. 通常構成へ戻す + ECS Exec を無効化
#    helper サイドカー無し / pidMode 無しの通常イメージへ。最も確実なのは通常デプロイの再実行:
#      gh workflow run deploy.yml -f version=vX.Y.Z -f environment=dev
#    deploy.yml は診断 revision より高い新 revision を登録するため、ADR-0028 の
#    max(revision) ロジックでも通常構成が選択される。
#    ECS Exec も明示的に無効化する:
aws ecs update-service \
  --cluster tasks-dev-cluster \
  --service tasks-dev-webapi \
  --no-enable-execute-command \
  --force-new-deployment

# 2. 一時付与した ECS Exec 用 IAM ポリシーを撤去
aws iam delete-role-policy \
  --role-name tasks-dev-webapi-task-role \
  --policy-name soak-ecs-exec-temp

# 3. 撤去確認(soak-ecs-exec-temp が出ないこと)
aws iam list-role-policies --role-name tasks-dev-webapi-task-role
```

> **注**: helper サイドカー入りの診断 task definition revision は登録されたまま残るが、
> サービスが参照していなければ無害。気になる場合は `aws ecs deregister-task-definition` で
> 当該 revision を無効化する。

---

## 結果記録テンプレート

ソークテストを実施したら以下を記録して PR / Issue にコメントする。

```markdown
## ソークテスト実施記録

- 実施日時: YYYY-MM-DD HH:MM 〜 HH:MM (X 時間)
- 実施モード: JVM / Native Image
- コミット / イメージタグ:
- 負荷: X req/s

### 観測結果

| 時刻 | JVM: GC後ヒープ(MB) / Native: RSS(MB) |
|---|---|
| 開始直後 |  |
| 2 時間後 |  |
| 4 時間後 |  |
| 終了時   |  |

### 判定

- [ ] 正常(増加傾向なし)
- [ ] 要調査(右肩上がりを確認 → ヒープダンプ取得へ)

### 添付

- ログファイル:
- CloudWatch グラフ URL(Native の場合):
```

---

## 参考

- [ADR-0029](../adr/0029-performance-measurement-and-diagnostics.md) §3.3/§6.3/§6.4
- [ADR-0018](../adr/0018-container-image-build-with-boot-build-image.md)(bootBuildImage / distroless 構成)
- [GraalVM: Create a Heap Dump from a Native Executable](https://www.graalvm.org/latest/reference-manual/native-image/guides/create-heap-dump/)
- [GraalVM: JFR with Native Image](https://www.graalvm.org/latest/reference-manual/native-image/debugging-and-diagnostics/JFR/)
- [Amazon ECS Container Insights metrics](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html)
