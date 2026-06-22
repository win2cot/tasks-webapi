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
| ECS Exec | **使わない**(command 駆動ダンプ。後述の理由で exec は pidMode 共有下で不可) | 無効(既定) |
| ヘルパーサイドカー | 追加する(自律スクリプトでダンプ取得) | 追加しない |
| ダンプ出力先 | 共有ボリューム `/dump`(ephemeral) | マウントしない |

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

### Native Image モード(AWS dev) — command 駆動ダンプ(ECS Exec を使わない)

> **なぜ ECS Exec を使わないか(2026-06-22 実機検証で確定)**:
> webapi の run image は distroless 同等(shell なし)のため、ヒープダンプの SIGUSR1 は
> **別コンテナ(helper)から `pidMode=task` で PID 名前空間を共有して送る**必要がある。
> ところが **ECS Exec は共有 PID 名前空間下では 1 タスクにつき 1 コンテナにしか接続できない**
> (AWS documented な制約)。実際 dev で helper へ `execute-command` すると IAM・SG・NAT・
> Platform Version すべて正常でも `TargetNotConnectedException` になり接続できなかった。
> したがって **exec で手動 kill するのではなく、helper の `command` 自体にシグナル送出と
> ダンプ確認を仕込み、結果を CloudWatch Logs に出して証跡化する**(exec を完全に回避)。
> この方式なら ECS Exec も `ssmmessages` 一時 IAM も不要。

#### 実測で確定した要件(2026-06-22 dev 検証)

実際に dev で end-to-end 成功した構成。以下が**揃って初めて**ダンプが取れる。

| 要件 | 理由(実測) |
|---|---|
| webapi に `command: ["-XX:HeapDumpPath=/dump"]` | 既定の出力先は CWD `/workspace` だが **runtime ユーザーが書けず IOException**(`Could not create the heap dump file`)。書き込み可能な共有ボリュームへ向ける |
| webapi に `/dump` 共有ボリュームをマウント | ダンプを helper と共有して回収するため |
| helper に `SYS_PTRACE` capability | 無いと別コンテナの `/proc/<pid>/cwd` を辿れない(Fargate は helper への SYS_PTRACE 付与を許可) |
| helper が起動直後に `chmod 777 /dump` | ephemeral ボリュームは root:root 755 で、非 root の webapi が書けないため world-writable 化 |
| `pidMode = task` | helper から webapi へシグナルを送るため PID 名前空間を共有 |
| native プロセスの cmdline = `./xyz.dgz48.tasks.webapi.TasksWebapiApplication` | helper の PID 特定パターン(`*TasksWebapi*` / `*webapi*` でマッチ) |

#### 診断タスク定義(helper が自律的にダンプ取得 → S3 退避)

**手順 1**: helper 自律スクリプトを `/tmp/helper.sh` に作成する
(chmod → 待機 → PID 特定 → SIGUSR1 を 1 発 → `/dump` 確認 → S3 退避)。

```sh
# /tmp/helper.sh
set -e
echo "[helper] chmod 777 /dump"; chmod 777 /dump 2>&1 || true
echo "[helper] waiting for webapi..."; sleep 75
SELF=$$; PID=""
for p in $(ls /proc 2>/dev/null | grep -E '^[0-9]+$'); do
  [ "$p" = "$SELF" ] && continue
  cmd=$(tr '\0' ' ' < /proc/$p/cmdline 2>/dev/null || true)
  case "$cmd" in *TasksWebapi*|*webapi*) PID="$p"; echo "[helper] webapi PID=$p"; break;; esac
done
[ -z "$PID" ] && { echo "[helper] PID 特定失敗"; sleep 3600; exit 0; }
# SIGUSR1 は 1 発のみ(G1 GC の 2 回目 SIGUSR1 segfault 報告 oracle/graal#9894 回避)
echo "[helper] SIGUSR1 送出"; kill -USR1 "$PID"; sleep 25
F=$(ls /dump/*.hprof 2>/dev/null | head -1)
[ -z "$F" ] && { echo "[helper] dump 未生成"; sleep 3600; exit 0; }
echo "[helper] dump: $(ls -lh "$F" | awk '{print $5, $NF}')"
command -v aws >/dev/null 2>&1 || dnf install -y awscli >/dev/null 2>&1 || true
aws s3 cp "$F" "s3://tasks-dev-frontend/diag/$(basename "$F")" --region ap-northeast-1 \
  && echo "[helper] UPLOADED" || echo "[helper] upload 失敗"
echo "[helper] DONE"; sleep 3600
```

**手順 2**: クリーン TD をベースに診断 TD を組み立てて登録・デプロイする。

```bash
# クリーンな直近リリース revision をベースにする(例: tasks-dev-webapi:23 = v0.1.21)
aws ecs describe-task-definition --task-definition tasks-dev-webapi:23 \
  --region ap-northeast-1 --query taskDefinition --output json > /tmp/td_base.json

# webapi に -XX:HeapDumpPath=/dump + /dump マウント、helper(SYS_PTRACE 付き)を追加
jq --rawfile script /tmp/helper.sh '
  .pidMode = "task" |
  .volumes = ((.volumes // []) + [{"name":"dump"}]) |
  (.containerDefinitions[] | select(.name=="webapi") | .mountPoints) = [{"sourceVolume":"dump","containerPath":"/dump"}] |
  (.containerDefinitions[] | select(.name=="webapi") | .command) = ["-XX:HeapDumpPath=/dump"] |
  .containerDefinitions += [{
    "name":"helper","image":"public.ecr.aws/amazonlinux/amazonlinux:2023","essential":false,
    "command":["sh","-c",$script],
    "linuxParameters":{"capabilities":{"add":["SYS_PTRACE"]}},
    "mountPoints":[{"sourceVolume":"dump","containerPath":"/dump"}],
    "logConfiguration":{"logDriver":"awslogs","options":{
      "awslogs-group":"/ecs/tasks-dev/webapi","awslogs-region":"ap-northeast-1","awslogs-stream-prefix":"helper"}}
  }] |
  del(.taskDefinitionArn,.revision,.status,.requiresAttributes,.compatibilities,.registeredAt,.registeredBy)
' /tmp/td_base.json > /tmp/td_diag.json

DIAG_ARN=$(aws ecs register-task-definition --region ap-northeast-1 \
  --cli-input-json file:///tmp/td_diag.json --query 'taskDefinition.taskDefinitionArn' --output text)

# S3 退避用の一時 IAM(Task Role に s3:PutObject 限定で付与。検証後に撤去)
aws iam put-role-policy --role-name tasks-dev-webapi-task-role --policy-name soak-dump-s3-temp \
  --policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":"s3:PutObject","Resource":"arn:aws:s3:::tasks-dev-frontend/diag/*"}]}'

# 診断 TD へ切替(ECS Exec は使わないので有効化しない)
aws ecs update-service --cluster tasks-dev-cluster --service tasks-dev-webapi \
  --task-definition "${DIAG_ARN}" --desired-count 1 --force-new-deployment --region ap-northeast-1
```

> **前提**: 稼働イメージが `--enable-monitoring=heapdump` を含むこと(本リポジトリは [build.gradle](../../webapi/build.gradle) で常時付与済み)。
> 含まないイメージでは SIGUSR1 を送っても `dumpHeap` が起動しない。

#### ダンプ生成・取り出しの確認(CloudWatch Logs + S3)

```bash
# helper の進捗を確認(SIGUSR1 → dump サイズ → UPLOADED)
aws logs tail /ecs/tasks-dev/webapi --log-stream-name-prefix helper \
  --since 10m --follow --region ap-northeast-1

# S3 からローカルへ取得し HPROF フォーマットを検証(Eclipse MAT で開ける)
aws s3 cp s3://tasks-dev-frontend/diag/<dump>.hprof ./native-dump.hprof --region ap-northeast-1
head -c 13 ./native-dump.hprof   # => "JAVA PROFILE 1.0.2" なら正当(MAT 解析可)
```

`[helper] UPLOADED` が出て、ダウンロードした hprof が `JAVA PROFILE 1.0.x` で始まれば
**Native でヒープダンプ取得 = 条件 1 達成**。Eclipse MAT の Leak Suspects / dominator tree で確定診断する。

#### ダンプの取り出し(任意・S3 経由)

確定診断(Eclipse MAT)のためローカルへ持ち出す場合は、helper(amazonlinux:2023 は dnf で aws-cli 導入可)から S3 へ。
S3 書き込みには Task Role への一時 IAM(`s3:PutObject` 対象バケット限定)が別途必要。
helper スクリプト末尾に `dnf install -y awscli && aws s3 cp /dump/<file>.hprof s3://<bucket>/...` を足す。

#### 代替: OOM 起因の自動ダンプ

`--enable-monitoring=heapdump` は OOM 時にも自動でダンプを書く。リーク確証後に負荷で OOM を
誘発する手もあるが、サービス断が発生するため通常は上記 command 駆動を使う。

#### 補足: どうしても ECS Exec をデバッグしたい場合

`pidMode=task` を**外した**通常タスクなら ECS Exec は 1 コンテナに接続できる(ただし distroless の
webapi には shell が無いため helper 等のサイドカーが必要で、その場合 pidMode 共有が無く webapi へ
シグナルを送れない=ダンプ用途には使えない)。exec の接続失敗自体を切り分けるには:

- クラスタ/サービスの `executeCommandConfiguration.logConfiguration` で SSM agent ログを
  CloudWatch/S3 に出すと、コンテナに入らず接続失敗理由を読める。
- NACL のエフェメラルポート(1024-65535)の inbound 戻りが塞がれていないか(SG は stateful だが NACL は stateless)。

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

検証で投入した診断構成を撤去する(helper サイドカー除去・クリーン TD 復帰・手動起動分の停止)。

```bash
# 1. 通常構成(helper / pidMode なしのクリーン TD)へ戻す。
#    クリーンな直近リリース revision に re-point する(例: tasks-dev-webapi:23 = v0.1.21)。
#    revision 番号は `aws ecs list-task-definitions --family-prefix tasks-dev-webapi` で確認。
CLEAN_REV=tasks-dev-webapi:23   # ← 実際のクリーン revision に置換
aws ecs update-service \
  --cluster tasks-dev-cluster --service tasks-dev-webapi \
  --task-definition "${CLEAN_REV}" \
  --disable-execute-command \
  --force-new-deployment \
  --region ap-northeast-1

# 2. off-hours に手動起動していた場合はコスト最適化状態へ戻す(スケジューラ運用に復帰)
aws ecs update-service --cluster tasks-dev-cluster --service tasks-dev-webapi \
  --desired-count 0 --region ap-northeast-1            # 検証で手動起動した場合のみ
aws rds stop-db-instance --db-instance-identifier tasks-dev-mysql --region ap-northeast-1

# 3. S3 退避用の一時 IAM(s3:PutObject)を撤去
aws iam delete-role-policy --role-name tasks-dev-webapi-task-role --policy-name soak-dump-s3-temp
aws iam list-role-policies --role-name tasks-dev-webapi-task-role   # soak-* が出ないこと

# 4. S3 に退避したダンプを削除(ローカルへ取得済みなら)
aws s3 rm s3://tasks-dev-frontend/diag/<dump>.hprof --region ap-northeast-1
```

> **注1**: command 駆動方式では ECS Exec も `ssmmessages` 一時 IAM も使わない。撤去対象は
> S3 退避用の `s3:PutObject` 一時ポリシー(`soak-dump-s3-temp`)のみ。
> **注2**: helper サイドカー入りの診断 task definition revision は登録されたまま残るが、
> サービスが参照していなければ無害。気になる場合は `aws ecs deregister-task-definition` で無効化する。

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
