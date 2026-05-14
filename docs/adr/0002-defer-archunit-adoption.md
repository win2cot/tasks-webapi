# ADR-0002: ArchUnit 採用を当面保留し Spring Modulith の検証に集約する

- **Status**: Accepted
- **Date**: 2026-05-14
- **Deciders**: 開発チーム
- **Tags**: architecture, ci, testing

## 1. コンテキスト

`.claude/CLAUDE.md`(2026-05-14 以前)の Architecture 節には **「クリーンアーキ 4 層(依存方向は外→内のみ、ArchUnit で検証)」** と記載されていた。一方で、本 PR(#135)時点の実態は以下のとおりである。

- `build.gradle` の依存に **ArchUnit は含まれていない**(`testImplementation` に `archunit-*` の記述なし)。
- `src/test/java/.../ModularityTests.java` で **Spring Modulith の `ApplicationModules.verify()`** が CI 必須テストとして稼働している(`testImplementation 'org.springframework.modulith:spring-modulith-starter-test'`)。
- 設計規約 §1.1 が採用するアーキテクチャは **「Spring Modulith による feature-by-package + 各 feature 内部でクリーンアーキ 4 層」のハイブリッド方式** であり、feature 間の依存違反は `ModularityTests` が機械的に検知する。
- feature 内部の 4 層(domain / usecase / adapter / infra)の依存方向は、現時点では Code Review でカバーする方針(設計規約 §1.1 の表)。

CLAUDE.md の従来記述は**実態を伴わない aspirational なメモ**であったと考えられるが、本 PR で「ArchUnit による静的検証の導入は ADR-NNNN で検討中(未採用)」へ書き換えるにあたり、**この方針判断を ADR として明示的に記録する**必要がある(PR #135 レビュー指摘 #2)。

## 2. 検討した選択肢

### 選択肢 A: ArchUnit を採用し feature 内部の 4 層依存方向を CI で強制する

- 概要: `archunit-junit5` を `testImplementation` に追加し、`<feature>.usecase` から `<feature>.adapter` への参照禁止、`<feature>.domain` への Spring 依存禁止、などのルールをテストで検証する。
- 利点:
  - feature 内部の 4 層依存違反を機械的に検出できる。
  - 設計規約 §1.1 が表で「内側は Code Review」としている箇所を自動化に格上げできる。
- 欠点:
  - ArchUnit のルール DSL を学ぶ学習コストと、初期セットアップ(対象パッケージの正規表現整備)が必要。
  - Spring Modulith の `@NamedInterface` / `internal` パッケージ規約と二重で類似ルールを書きうるため、ルールの責務分担を別途設計する必要がある。
  - 規約を厳しくすると、PoC 期や急ぎの fix で例外を作りにくく、開発の摩擦になる懸念。
- リスク・未知数:
  - Spring Modulith の検証範囲(feature 間)と ArchUnit の検証範囲(feature 内部)を綺麗に分離できるかは要実証。

### 選択肢 B: ArchUnit 採用を当面保留し、Spring Modulith の `ApplicationModules.verify()` に集約する

- 概要: `ModularityTests` を**唯一の自動検証**として据え、feature 内部の 4 層は当面 Code Review でカバー。CLAUDE.md と設計規約 §1.1 を実態に揃える。
- 利点:
  - 追加依存・追加 CI 設定なしで、現行 scaffold の安定性を保てる。
  - feature 単位の境界違反という最も致命的な事故は既に機械的に防げている。
  - 将来 ArchUnit が必要になった時点で(例:feature が 10 個を超える、内部 4 層違反が複数件 Code Review をすり抜ける、など)選択肢 A へ昇格する余地を残せる。
- 欠点:
  - feature 内部の依存方向違反は Code Review に依存(検知が遅れるリスク)。
  - 将来 ArchUnit を入れる際に追加コストが発生する。
- リスク・未知数:
  - Sprint 0〜1 で feature 数が増えた段階で、Code Review の負担が想定より大きくなる可能性。

### 選択肢 C: 自前の `ApplicationModuleListener` テスト + `package-info.java` の `@ApplicationModule(allowedDependencies)` で頑張る

- 概要: ArchUnit を入れずに、`@ApplicationModule(allowedDependencies = …)` をきめ細かく設定し、Spring Modulith の検証範囲を拡張する。
- 利点: 追加依存ゼロ。
- 欠点: feature 内部の 4 層検証は Spring Modulith のスコープ外なので限界があり、結局カバレッジが選択肢 B と変わらない。
- リスク・未知数: 設定の複雑化が利得を上回るリスク。

## 3. 決定

**採用**: 選択肢 B(ArchUnit 採用を当面保留)

- 自動検証は **Spring Modulith の `ApplicationModules.verify()` のみ**を CI 必須とする。
- feature 内部の 4 層依存違反は **Code Review でカバーする**(コーディング規約 §18 のチェックリストに含める)。
- ArchUnit 導入は将来課題とし、再評価のトリガーは「feature 数 ≥ 10 になる」または「Code Review で feature 内部の 4 層違反が 2 回以上検知不能になった事案を観測する」のいずれか早い方とする。
- 本 ADR は本 PR(#135)のマージをもって **Accepted** とし、後日選択肢 A への切替が必要になった場合は新規 ADR(`Supersedes ADR-0002`)で更新する。

## 4. 理由

- 現行 scaffold は ArchUnit を実際には組み込んでおらず、従来 CLAUDE.md の「ArchUnit で検証」記述は**実態を反映していなかった**。これを実態と合わせる必要がある。
- Spring Modulith の `verify()` は既に動作中で、feature 間境界という最も重要な不変条件は既に保証されている。
- 内部 4 層違反は致命的ではあるが、観測頻度が未知数であり、Code Review でも当面は十分に検知できると見込まれる。
- Sprint 0 着手前にライブラリを増やすより、まず本 PR(規約整備)を確定し、運用実績を見てから判断するのが堅実。

## 5. 影響

### 良い影響

- CLAUDE.md / 設計規約 §1.1 と CI 実態の齟齬が解消される(レビュー指摘 #2)。
- `build.gradle` の依存追加・CI 設定変更が不要で、本 PR のスコープを大きくしない。

### 悪い影響・制約

- feature 内部の 4 層依存違反は Code Review でしか検知できない。新規参画メンバーがいきなり `domain` から Spring 依存を持ち込むようなコードを書く可能性がゼロではない。
- 「将来導入する」と書きながら導入されないリスク(運用課題)。

### 既存ドキュメント・規約への波及

- 設計規約 §1.1 表内の「内側 = Code Review / ArchUnit(導入は別 ADR)」を「**Code Review(ArchUnit 導入は ADR-0002 で保留)**」に更新(本 ADR と同 PR で対応)。
- `.claude/CLAUDE.md` の Architecture 節「ArchUnit による静的検証の導入は **ADR-NNNN で検討中**(未採用)」を「**ADR-0002 により採用を保留**」に更新(本 ADR と同 PR で対応)。

## 6. 実装メモ

- 本 ADR がマージされた時点で `ModularityTests` が単一の自動検証となる(Accepted 反映)。
- 再評価のトリガーが発火したら、`Supersedes ADR-0002` を Status に持つ新規 ADR を起票する。
- `ApplicationModules.verify()` で不足を感じた場合、まずは `@ApplicationModule(allowedDependencies = …)` の細分化(選択肢 C 相当の限定的な強化)で対応し、それでも不足なら ArchUnit を検討する。

## 7. 参考リンク

- Spring Modulith リファレンス: https://docs.spring.io/spring-modulith/reference/
- ArchUnit: https://www.archunit.org/
- 設計規約 §1.1 / §1.3
- `.claude/CLAUDE.md` Architecture 節
- PR #135 レビュー指摘 #2(2026-05-14)
