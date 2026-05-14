# ADR-NNNN: <意思決定のタイトル>

- **Status**: Proposed | Accepted | Deprecated | Superseded by ADR-XXXX
- **Date**: YYYY-MM-DD
- **Deciders**: <意思決定に関わった人>
- **Tags**: <例: architecture, security, persistence>

## 0. 本テンプレートの使い方

- 新規 ADR は `docs/adr/NNNN-<kebab-case-title>.md` のファイル名で作成する(`NNNN` は4桁連番)。
- 採用すると決まった ADR は **不変**。後で覆す場合は新規 ADR(Status: Supersedes ADR-NNNN)を立てる。
- 本書のコメント(`<...>` プレースホルダ)は埋めた後に削除する。
- **本セクション(§0)は実 ADR には不要なので、テンプレからコピーした直後にこのセクション全体を削除する**。参照実装として、`docs/adr/0001-record-architecture-decisions.md` は §0 を削除した状態で `§1. コンテキスト` から始まっているため、書き出しはそれに倣う。

## 1. コンテキスト(Context)

<どのような状況・課題に直面しているか。何を決めなければならないのか。背景・制約・関連ドキュメント(設計書・PR・レビュー報告書)へのリンクを示す>

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: <名前>

- 概要:
- 利点:
- 欠点:
- リスク・未知数:

### 選択肢 B: <名前>

- 概要:
- 利点:
- 欠点:
- リスク・未知数:

<必要に応じて選択肢 C 以降を追加。「何もしない」も選択肢に含めてよい>

## 3. 決定(Decision)

**採用**: 選択肢 X

<採用した選択肢を明示する。曖昧な書き方を避け、断定形で書く>

## 4. 理由(Rationale)

<採用理由を 3〜5 個程度の bullet で示す。トレードオフを明示し、捨てた利点も認める>

## 5. 影響(Consequences)

### 良い影響(Positive)

- <例: feature 間の依存が ApplicationModules.verify() で機械的に保証される>

### 悪い影響・制約(Negative)

- <例: 新規 feature を追加する都度 package-info.java と ModularityTests への影響を確認する手間が増える>

### 既存ドキュメント・規約への波及

- <例: docs/specs/設計規約.md §1.1 を更新する必要がある>

## 6. 実装メモ(Implementation Notes)

<採用後、最初に着手すべき作業 / PR 分割の方針 / 検証方法。任意項目>

## 7. 参考リンク(References)

- <設計書セクション、過去 ADR、外部記事、Issue / PR へのリンク>
