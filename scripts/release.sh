#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lib.sh"
[[ $# == 2 ]] || fail '用法：bash scripts/release.sh 1.0.1 "fix(payment): 修复说明"'
VERSION_NEXT="$1"
COMMIT_MESSAGE="$2"
[[ "$VERSION_NEXT" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]] || fail '版本号格式应为 1.0.1。'
cd "$PROJECT_ROOT"
command -v git >/dev/null && command -v mvn >/dev/null && command -v npm >/dev/null && command -v python3 >/dev/null || fail '需要 Git、Java 21 / Maven、Node.js / npm 和 Python 3。'
[[ -n "$(git branch --show-current)" ]] || fail '请先切换到一个分支，不能在 detached HEAD 发布。'
git remote get-url origin | grep -Eq 'github\.com[:/]xiaowork-dev/Automated-Delivery(\.git)?$' || fail 'origin 必须指向用户指定的 Automated-Delivery 仓库。'
if git rev-parse --verify --quiet "refs/tags/v$VERSION_NEXT" >/dev/null; then fail '该版本标签已存在。'; fi
[[ "$(tr -d '\r\n' < VERSION)" != "$VERSION_NEXT" ]] || fail '发布版本必须不同于当前 VERSION。'
python3 scripts/set-version.py "$VERSION_NEXT"
(cd backend && mvn -B verify)
(cd frontend && npm ci && npm run build)
if [[ -n "${TEST_BASE_URL:-}" ]]; then python3 scripts/api-acceptance.py; fi
git diff --check
git add --all
python3 scripts/check-release.py
printf '%s\n' '本版本提交文件：'
git diff --cached --name-status
git commit -m "$COMMIT_MESSAGE"
git tag -a "v$VERSION_NEXT" -m "Version $VERSION_NEXT"
BRANCH_CURRENT="$(git branch --show-current)"
git push --atomic --set-upstream origin "HEAD:refs/heads/$BRANCH_CURRENT" "refs/tags/v$VERSION_NEXT"
printf '版本 %s 已提交并推送。\n' "$VERSION_NEXT"
