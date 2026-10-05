#!/usr/bin/env bash
# Isolated installer behavior tests: real local Git, fake Docker/deploy, no network or package manager.
set -Eeuo pipefail

SCRIPT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
INSTALLER="$SCRIPT_ROOT/install.sh"
[[ -f "$INSTALLER" ]] || { printf '%s\n' 'Missing install.sh; create the installer before running its tests.' >&2; exit 1; }
for tool in git mktemp sha256sum bash; do
  command -v "$tool" >/dev/null || { printf 'Missing test utility: %s\n' "$tool" >&2; exit 1; }
done

TASK_TMP_PARENT="$(cd -- "${TMPDIR:-/tmp}" && pwd -P)"
TASK_TMP_DIR="$(mktemp -d "$TASK_TMP_PARENT/automated-delivery-install-test.XXXXXX")"
TASK_TMP_DIR="$(cd -- "$TASK_TMP_DIR" && pwd -P)"
cleanup() {
  # Resolve and verify the exact temporary workspace before any recursive removal.
  if [[ -d "$TASK_TMP_DIR" ]]; then
    local resolved
    resolved="$(cd -- "$TASK_TMP_DIR" && pwd -P)"
    if [[ "$resolved" == "$TASK_TMP_DIR" && "$resolved" == "$TASK_TMP_PARENT"/automated-delivery-install-test.* ]]; then
      rm -rf -- "$resolved"
    else
      printf '%s\n' 'Refusing to clean a path outside the isolated test workspace.' >&2
    fi
  fi
}
trap cleanup EXIT

export REAL_GIT_BIN="$(command -v git)"
export GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL="$TASK_TMP_DIR/gitconfig"
export GIT_TERMINAL_PROMPT=0 GIT_ALLOW_PROTOCOL=file
export FAKE_DEPLOY_LOG="$TASK_TMP_DIR/deploy.log" FAKE_GIT_TRACE="$TASK_TMP_DIR/git-trace.log"
FIXED_REPO='https://github.com/xiaowork-dev/Automated-Delivery.git'
REMOTE="$TASK_TMP_DIR/remote.git"
SEED="$TASK_TMP_DIR/seed"
TARGET="$TASK_TMP_DIR/checkout"
STATE="$TARGET/.deployment-state"
OUT="$TASK_TMP_DIR/installer-output.log"
LOCK_FILE="$TASK_TMP_DIR/.checkout.install.lock"
LOCK_DIR="$LOCK_FILE.d"
mkdir -p "$TASK_TMP_DIR/bin"
: > "$FAKE_DEPLOY_LOG"
: > "$FAKE_GIT_TRACE"

cat > "$TASK_TMP_DIR/bin/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_GIT_TRACE"
exec "$REAL_GIT_BIN" "$@"
EOF
cat > "$TASK_TMP_DIR/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == info || "${1:-}" == version ]]; then printf '%s\n' '28.0.0'; exit 0; fi
if [[ "${1:-}" == inspect ]]; then
  status="${FAKE_DOCKER_HEALTH:-healthy}"
  case "$*" in
    *State.Health*) printf '%s\n' "$status" ;;
    *State.Running*) printf '%s\n' true ;;
    *State.Status*) printf '%s\n' running ;;
    *) printf '%s\n' "$status" ;;
  esac
  exit 0
fi
if [[ "${1:-}" == compose ]]; then
  case "$*" in
    *version*) printf '%s\n' 'Docker Compose version v2.39.0' ;;
    *' ps '*)
      service=''
      for arg in "$@"; do case "$arg" in mysql|redis|backend|frontend) service="$arg" ;; esac; done
      if [[ -n "$service" ]]; then printf 'fixture-%s\n' "$service"; else printf '%s\n' fixture-mysql fixture-redis fixture-backend fixture-frontend; fi
      ;;
    *) printf 'Unexpected fake Docker command: %s\n' "$*" >&2; exit 90 ;;
  esac
  exit 0
fi
printf 'Unexpected fake Docker command: %s\n' "$*" >&2
exit 90
EOF
# Fail closed if --no-deps accidentally invokes installation or network utilities.
for tool in apt apt-get dnf yum zypper pacman brew sudo curl wget; do
  cat > "$TASK_TMP_DIR/bin/$tool" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' 'Forbidden package-manager/network operation in isolated installer tests.' >&2
exit 91
EOF
done
chmod +x "$TASK_TMP_DIR/bin/"*
export PATH="$TASK_TMP_DIR/bin:$PATH"

git init --bare --initial-branch=main "$REMOTE" >/dev/null
git init --initial-branch=main "$SEED" >/dev/null
git -C "$SEED" config user.name 'Installer Fixture'
git -C "$SEED" config user.email 'installer-fixture@example.invalid'
mkdir -p "$SEED/scripts" "$SEED/deploy"
cp "$INSTALLER" "$SEED/install.sh"
printf '%s\n' '1.0.0' > "$SEED/VERSION"
printf '%s\n' 'Isolated install fixture; no production configuration.' > "$SEED/README.md"
printf '%s\n' '.env' '.deployment-state' 'deploy/certs/' 'backups/' '*.log' > "$SEED/.gitignore"
printf '%s\n' 'services: {}' > "$SEED/docker-compose.yml"
printf '%s\n' 'services: {}' > "$SEED/deploy/docker-compose.https.yml"
cat > "$SEED/scripts/deploy.sh" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
printf '%s|%s\n' "$(git -C "$project" rev-parse HEAD)" "$*" >> "$FAKE_DEPLOY_LOG"
if [[ ! -f "$project/.env" ]]; then printf '%s\n' 'INSTALL_FIXTURE=harmless-test-only-value' > "$project/.env"; fi
if [[ "${FAKE_DEPLOY_FAIL:-0}" == 1 ]]; then printf '%s\n' 'Injected fixture deploy failure.' >&2; exit 42; fi
printf '%s\n' 'Fixture deploy completed; fake containers are healthy.'
EOF
chmod +x "$SEED/install.sh" "$SEED/scripts/deploy.sh"
git -C "$SEED" add .
git -C "$SEED" commit -m 'test: initial installer fixture' >/dev/null
git -C "$SEED" remote add origin "$REMOTE"
git -C "$SEED" push -u origin main >/dev/null 2>&1
if command -v cygpath >/dev/null && [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
  LOCAL_REPO_URL="file:///$(cygpath -m "$REMOTE")"
else
  LOCAL_REPO_URL="file://$REMOTE"
fi
git config --global "url.$LOCAL_REPO_URL.insteadOf" "$FIXED_REPO"
git config --global credential.helper ''

PASSED=0
fail() { printf 'FAIL %s\n' "$*" >&2; tail -n 50 "$OUT" >&2 2>/dev/null || true; exit 1; }
pass() { PASSED=$((PASSED+1)); printf 'PASS %s\n' "$*"; }
assert_equal() { [[ "$1" == "$2" ]] || fail "$3"; }
count_deploys() { wc -l < "$FAKE_DEPLOY_LOG" | tr -d '[:space:]'; }
state_value() { sed -n "s/^$1=//p" "$STATE" | tail -n 1; }
file_hash() { sha256sum "$1" | awk '{print $1}'; }
remote_head() { git -C "$SEED" rev-parse HEAD; }
run_installer_at() {
  local directory="$1"
  shift
  : > "$FAKE_GIT_TRACE"
  bash "$INSTALLER" --dir "$directory" --branch main --no-deps "$@" > "$OUT" 2>&1
}
run_installer() { run_installer_at "$TARGET" "$@"; }
expect_rejection() {
  if run_installer "$@"; then fail 'Installer accepted an operation that must be rejected'; fi
}
expect_rejection_at() {
  if run_installer_at "$@"; then fail 'Installer accepted an operation that must be rejected'; fi
}
publish_version() {
  printf '%s\n' "$1" > "$SEED/VERSION"
  git -C "$SEED" add VERSION
  git -C "$SEED" commit -m "test: publish fixture version $1" >/dev/null
  git -C "$SEED" push origin main >/dev/null 2>&1
}

run_installer || fail 'First installation failed'
assert_equal "$(git -C "$TARGET" rev-parse HEAD)" "$(remote_head)" 'Initial checkout differs from local remote'
assert_equal "$(state_value COMMIT)" "$(remote_head)" 'First successful state lacks actual commit'
assert_equal "$(state_value VERSION)" '1.0.0' 'First successful state lacks version'
assert_equal "$(state_value BRANCH)" main 'Successful state lacks branch'
assert_equal "$(state_value HTTPS)" false 'First installation unexpectedly enables HTTPS'
[[ -n "$(state_value DEPLOYED_AT)" ]] || fail 'Successful state lacks deployment timestamp'
assert_equal "$(count_deploys)" 1 'First installation did not deploy exactly once'
pass 'first install clones fixed repository and records healthy deployment'

FIRST_COMMIT="$(git -C "$TARGET" rev-parse HEAD)"
run_installer || fail 'Same-version deployment failed'
assert_equal "$(git -C "$TARGET" rev-parse HEAD)" "$FIRST_COMMIT" 'Same-version run changed commit'
assert_equal "$(count_deploys)" 2 'Same-version run did not redeploy'
pass 'same version can be safely redeployed'

printf '%s\n' 'INSTALL_FIXTURE=preserve-this-harmless-value' > "$TARGET/.env"
mkdir -p "$TARGET/deploy/certs" "$TARGET/backups"
printf '%s\n' 'isolated dummy certificate sentinel' > "$TARGET/deploy/certs/fullchain.pem"
printf '%s\n' 'isolated dummy key sentinel, no real cryptographic key' > "$TARGET/deploy/certs/privkey.pem"
printf '%s\n' 'isolated backup sentinel, no real database data' > "$TARGET/backups/fixture.sql.gz"
ENV_HASH="$(file_hash "$TARGET/.env")"
CERT_HASH="$(file_hash "$TARGET/deploy/certs/fullchain.pem")"
KEY_HASH="$(file_hash "$TARGET/deploy/certs/privkey.pem")"
BACKUP_HASH="$(file_hash "$TARGET/backups/fixture.sql.gz")"
run_installer --https || fail 'Explicit HTTPS fixture deployment failed'
assert_equal "$(state_value HTTPS)" true 'HTTPS state was not recorded'
[[ "$(tail -n 1 "$FAKE_DEPLOY_LOG")" == *'|--https' ]] || fail 'HTTPS flag did not reach deploy script'
pass 'explicit HTTPS reaches deploy and successful state'

publish_version 1.0.1
run_installer || fail 'Fast-forward upgrade failed'
assert_equal "$(git -C "$TARGET" rev-parse HEAD)" "$(remote_head)" 'Upgrade did not reach remote commit'
git -C "$TARGET" merge-base --is-ancestor "$FIRST_COMMIT" HEAD || fail 'Upgrade rewrote history'
assert_equal "$(state_value VERSION)" '1.0.1' 'Upgrade did not record new version'
assert_equal "$(state_value HTTPS)" true 'Auto upgrade lost HTTPS mode'
[[ "$(tail -n 1 "$FAKE_DEPLOY_LOG")" == *'|--https' ]] || fail 'Auto upgrade did not retain HTTPS deploy flag'
assert_equal "$(file_hash "$TARGET/.env")" "$ENV_HASH" 'Upgrade replaced environment'
assert_equal "$(file_hash "$TARGET/deploy/certs/fullchain.pem")" "$CERT_HASH" 'Upgrade replaced certificate'
assert_equal "$(file_hash "$TARGET/deploy/certs/privkey.pem")" "$KEY_HASH" 'Upgrade replaced private-key sentinel'
assert_equal "$(file_hash "$TARGET/backups/fixture.sql.gz")" "$BACKUP_HASH" 'Upgrade replaced backup sentinel'
pass 'fast-forward upgrade preserves environment, certificates, backups and HTTPS'

GOOD_COMMIT="$(git -C "$TARGET" rev-parse HEAD)"
GOOD_STATE="$(file_hash "$STATE")"
BEFORE="$(count_deploys)"
printf '\n%s\n' 'local tracked edit' >> "$TARGET/README.md"
expect_rejection
assert_equal "$(count_deploys)" "$BEFORE" 'Dirty checkout still ran deploy'
assert_equal "$(file_hash "$STATE")" "$GOOD_STATE" 'Dirty rejection changed successful state'
git -C "$TARGET" restore -- README.md
pass 'tracked local changes are refused without deployment'

git -C "$TARGET" config user.name 'Installer Fixture'
git -C "$TARGET" config user.email 'installer-fixture@example.invalid'
printf '%s\n' 'local branch divergence fixture' > "$TARGET/local-branch.txt"
git -C "$TARGET" add local-branch.txt
git -C "$TARGET" commit -m 'test: local diverging commit' >/dev/null
LOCAL_COMMIT="$(git -C "$TARGET" rev-parse HEAD)"
publish_version 1.0.2
expect_rejection
assert_equal "$(git -C "$TARGET" rev-parse HEAD)" "$LOCAL_COMMIT" 'Divergence rejection reset local commit'
assert_equal "$(count_deploys)" "$BEFORE" 'Diverging branch still ran deploy'
assert_equal "$(file_hash "$STATE")" "$GOOD_STATE" 'Divergence rejection changed successful state'
# Reset only a fixture checkout created inside our verified temporary workspace.
[[ "$TARGET" == "$TASK_TMP_DIR/checkout" ]] || fail 'Unexpected fixture checkout path'
git -C "$TARGET" reset --hard "$GOOD_COMMIT" >/dev/null
pass 'diverging local and remote histories are refused without reset'

git -C "$TARGET" remote set-url origin 'https://example.invalid/unapproved.git'
expect_rejection
if grep -Eq '(^|[[:space:]])(fetch|pull)([[:space:]]|$)' "$FAKE_GIT_TRACE"; then fail 'Wrong origin was contacted before validation'; fi
assert_equal "$(count_deploys)" "$BEFORE" 'Wrong origin still ran deploy'
assert_equal "$(file_hash "$STATE")" "$GOOD_STATE" 'Origin rejection changed successful state'
git -C "$TARGET" remote set-url origin "$FIXED_REPO"
pass 'wrong origin is refused before any remote fetch'

export FAKE_DEPLOY_FAIL=1
expect_rejection
unset FAKE_DEPLOY_FAIL
assert_equal "$(file_hash "$STATE")" "$GOOD_STATE" 'Failed deploy overwrote last successful state'
assert_equal "$(state_value COMMIT)" "$GOOD_COMMIT" 'Failed upgrade was marked successful'
[[ ! -d "$LOCK_DIR" ]] || fail 'Failed deploy leaked fallback lock directory'
pass 'deployment failure preserves last successful marker and releases lock'

export FAKE_DOCKER_HEALTH=unhealthy
expect_rejection
unset FAKE_DOCKER_HEALTH
assert_equal "$(file_hash "$STATE")" "$GOOD_STATE" 'Unhealthy services were marked successfully deployed'
pass 'unhealthy containers cannot produce a success marker'

run_installer || fail 'Recovery after injected failure failed'
assert_equal "$(state_value COMMIT)" "$(remote_head)" 'Recovery did not record actual commit'
assert_equal "$(state_value VERSION)" '1.0.2' 'Recovery did not record actual version'
pass 'retry after failure records the recovered deployment'

BEFORE="$(count_deploys)"
LOCK_STATE="$(file_hash "$STATE")"
if command -v flock >/dev/null; then
  exec 9> "$LOCK_FILE"
  flock -x 9
  expect_rejection
  flock -u 9
  exec 9>&-
  LOCK_MODE=flock
else
  mkdir "$LOCK_DIR"
  expect_rejection
  rmdir "$LOCK_DIR"
  LOCK_MODE=mkdir
fi
assert_equal "$(count_deploys)" "$BEFORE" 'Lock contention still ran deploy'
assert_equal "$(file_hash "$STATE")" "$LOCK_STATE" 'Lock rejection changed successful state'
pass "concurrent installer lock is refused ($LOCK_MODE)"

BEFORE="$(count_deploys)"
COLLISION_COMMIT="$(git -C "$TARGET" rev-parse HEAD)"
COLLISION_STATE="$(file_hash "$STATE")"
printf '%s\n' 'REMOTE_FIXTURE=unwanted-tracked-placeholder' > "$SEED/.env"
printf '%s\n' '1.0.3' > "$SEED/VERSION"
git -C "$SEED" add -f .env
git -C "$SEED" add VERSION
git -C "$SEED" commit -m 'test: remote mistakenly tracks an environment file' >/dev/null
git -C "$SEED" push origin main >/dev/null 2>&1
expect_rejection
assert_equal "$(file_hash "$TARGET/.env")" "$ENV_HASH" 'Remote tracked environment overwrote local ignored configuration'
assert_equal "$(git -C "$TARGET" rev-parse HEAD)" "$COLLISION_COMMIT" 'Environment collision advanced local commit'
assert_equal "$(file_hash "$STATE")" "$COLLISION_STATE" 'Environment collision overwrote successful state'
assert_equal "$(count_deploys)" "$BEFORE" 'Environment collision still ran deployment'
pass 'remote tracked environment collision is refused without overwriting ignored configuration'

git -C "$SEED" rm -- .env >/dev/null
mkdir -p "$SEED/deploy/certs"
printf '%s\n' 'unwanted remote tracked certificate fixture' > "$SEED/deploy/certs/fullchain.pem"
printf '%s\n' '1.0.4' > "$SEED/VERSION"
git -C "$SEED" add -f deploy/certs/fullchain.pem
git -C "$SEED" add VERSION
git -C "$SEED" commit -m 'test: remote mistakenly tracks a certificate' >/dev/null
git -C "$SEED" push origin main >/dev/null 2>&1
expect_rejection
assert_equal "$(file_hash "$TARGET/deploy/certs/fullchain.pem")" "$CERT_HASH" 'Remote tracked certificate overwrote local ignored certificate'
assert_equal "$(file_hash "$TARGET/deploy/certs/privkey.pem")" "$KEY_HASH" 'Certificate collision replaced private-key sentinel'
assert_equal "$(git -C "$TARGET" rev-parse HEAD)" "$COLLISION_COMMIT" 'Certificate collision advanced local commit'
assert_equal "$(file_hash "$STATE")" "$COLLISION_STATE" 'Certificate collision overwrote successful state'
assert_equal "$(count_deploys)" "$BEFORE" 'Certificate collision still ran deployment'
pass 'remote tracked certificate collision is refused without overwriting ignored certificate'

git -C "$SEED" rm -- deploy/certs/fullchain.pem >/dev/null
printf '%s\n' '1.0.5' > "$SEED/VERSION"
git -C "$SEED" add VERSION
git -C "$SEED" commit -m 'test: remove unsafe tracked fixture files' >/dev/null
git -C "$SEED" push origin main >/dev/null 2>&1
run_installer || fail 'Recovery after removing remote ignored-file collisions failed'
assert_equal "$(state_value COMMIT)" "$(remote_head)" 'Collision recovery did not record current commit'
assert_equal "$(file_hash "$TARGET/.env")" "$ENV_HASH" 'Collision recovery replaced environment'
assert_equal "$(file_hash "$TARGET/deploy/certs/fullchain.pem")" "$CERT_HASH" 'Collision recovery replaced certificate'
assert_equal "$(state_value HTTPS)" true 'Collision recovery lost HTTPS mode'
pass 'safe remote correction allows recovery while preserving local configuration'

LEGACY="$TASK_TMP_DIR/legacy"
git clone --single-branch --branch main "$FIXED_REPO" "$LEGACY" >/dev/null 2>&1
mkdir -p "$LEGACY/deploy/certs"
printf '%s\n' 'LEGACY_FIXTURE=harmless-test-only-value' > "$LEGACY/.env"
printf '%s\n' 'single legacy certificate sentinel' > "$LEGACY/deploy/certs/fullchain.pem"
LEGACY_COMMIT="$(git -C "$LEGACY" rev-parse HEAD)"
BEFORE="$(count_deploys)"
expect_rejection_at "$LEGACY"
assert_equal "$(git -C "$LEGACY" rev-parse HEAD)" "$LEGACY_COMMIT" 'Incomplete legacy certificate advanced commit'
assert_equal "$(count_deploys)" "$BEFORE" 'Incomplete legacy certificate silently deployed HTTP'
[[ ! -e "$LEGACY/.deployment-state" ]] || fail 'Incomplete legacy certificate created a success marker'
pass 'one certificate with no state is refused instead of silently downgrading to HTTP'

mv -- "$LEGACY/deploy/certs/fullchain.pem" "$LEGACY/deploy/certs/privkey.pem"
cat > "$LEGACY/.deployment-state" <<EOF
COMMIT=$LEGACY_COMMIT
VERSION=1.0.5
HTTPS=false
BRANCH=main
DEPLOYED_AT=2000-01-01T00:00:00Z
EOF
LEGACY_STATE="$(file_hash "$LEGACY/.deployment-state")"
expect_rejection_at "$LEGACY"
assert_equal "$(git -C "$LEGACY" rev-parse HEAD)" "$LEGACY_COMMIT" 'Incomplete certificate with HTTP state advanced commit'
assert_equal "$(count_deploys)" "$BEFORE" 'Incomplete certificate with HTTP state silently ran deploy'
assert_equal "$(file_hash "$LEGACY/.deployment-state")" "$LEGACY_STATE" 'Incomplete certificate changed old HTTP state'
pass 'one private-key sentinel with old HTTP state is refused without changing state or commit'

printf 'Installer regression: %s checks passed; real local Git; fake Docker/deploy; real %s lock; no network or package installation.\n' "$PASSED" "$LOCK_MODE"
