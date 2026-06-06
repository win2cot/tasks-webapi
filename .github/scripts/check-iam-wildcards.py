#!/usr/bin/env python3
"""IAM wildcard gate — Terraform 規約 R1 の機械検知(Issue #457)。

infra/**/*.tf の IAM action のうち、wildcard を含み action 名が
Get / List / Describe で始まらないものを検知して exit 1 で fail する。
判定基準は docs/specs/Terraform規約.md §1.1(R1)と同一。

検知対象の例: "ec2:*" / "ec2:Create*" / "iam:Put*"
許容の例:     "ec2:Describe*" / "iam:Get*" / "iam:List*"

ARN("arn:aws:..."、コロン複数)や condition キー("s3:prefix"、
wildcard なし)はパターン上マッチしない。行コメント(# ...)は除外する。
"""

import re
import sys
from pathlib import Path

ALLOWED_PREFIXES = ("Get", "List", "Describe")

# "service:Action" 形式(quote 直後に service、action 部に wildcard を含む)のみ抽出
ACTION_RE = re.compile(r'"([a-z0-9-]+):([A-Za-z0-9]*\*[A-Za-z0-9]*)"')


def main() -> int:
    violations: list[str] = []
    for tf in sorted(Path("infra").rglob("*.tf")):
        for lineno, line in enumerate(
            tf.read_text(encoding="utf-8").splitlines(), start=1
        ):
            code = line.split("#", 1)[0]  # 行コメントを除外
            for svc, action in ACTION_RE.findall(code):
                if not action.startswith(ALLOWED_PREFIXES):
                    violations.append(f'{tf}:{lineno}: "{svc}:{action}"')

    if violations:
        print(
            "NG: 読取専用 prefix (Get*/List*/Describe*) 以外の wildcard action を検出"
            "(docs/specs/Terraform規約.md §1.1 R1)"
        )
        print("\n".join(violations))
        return 1

    print("OK: IAM wildcard gate passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
