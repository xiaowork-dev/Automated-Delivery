#!/usr/bin/env bash
# First installation and subsequent updates use the same command:
# curl -fsSL https://raw.githubusercontent.com/xiaowork-dev/Automated-Delivery/main/install.sh | sudo bash
# Docker repositories: https://docs.docker.com/engine/install/ and /compose/install/linux/
set -Eeuo pipefail

REPOSITORY_URL='https://github.com/xiaowork-dev/Automated-Delivery.git'
INSTALL_DIR='/opt/Automated-Delivery'
INSTALL_BRANCH='main'
HTTPS_MODE='auto'
NO_DEPS=false
LOCK_DIRECTORY=''
INSTALL_TMP=''
STATE_TEMP=''

say() { printf '%s\n' "$*"; }
die() { printf '错误：%s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Automated Delivery 首次安装 / GitHub 更新
用法：bash install.sh [--dir /opt/Automated-Delivery] [--branch main] [--https|--http|--auto] [--no-deps]
默认自动保留已启用的 HTTPS。--https 要求目录中已有证书。
--no-deps 复用现有 Git / Docker / Compose，不安装软件，允许非 root 的隔离验收。
EOF
}

cleanup() {
  if [[ -n "$STATE_TEMP" && -f "$STATE_TEMP" ]]; then rm -f -- "$STATE_TEMP"; fi
  if [[ -n "$INSTALL_TMP" && -d "$INSTALL_TMP" ]]; then
    for file in docker.asc docker.sources docker-ce.repo; do rm -f -- "$INSTALL_TMP/$file"; done
    rmdir -- "$INSTALL_TMP" 2>/dev/null || true
  fi
  if [[ -n "$LOCK_DIRECTORY" ]]; then rmdir -- "$LOCK_DIRECTORY" 2>/dev/null || true; fi
}

failed() {
  local status="$1"
  printf '安装/更新失败（退出码 %s）。请检查上方错误；成功版本只在部署健康检查通过后记录。\n' "$status" >&2
  exit "$status"
}

parse_arguments() {
  while (( $# )); do
    case "$1" in
      --dir)
        (( $# >= 2 )) && [[ -n "$2" ]] || die '--dir 缺少绝对目录。'
        INSTALL_DIR="$2"; shift 2 ;;
      --branch)
        (( $# >= 2 )) && [[ -n "$2" ]] || die '--branch 缺少分支名。'
        INSTALL_BRANCH="$2"; shift 2 ;;
      --https) HTTPS_MODE='https'; shift ;;
      --http) HTTPS_MODE='http'; shift ;;
      --auto) HTTPS_MODE='auto'; shift ;;
      --no-deps) NO_DEPS=true; shift ;;
      --help|-h) usage; exit 0 ;;
      *) die "未知参数：$1" ;;
    esac
  done
  [[ "$INSTALL_DIR" == /* && "$INSTALL_DIR" != '/' ]] || die '--dir 必须是非根目录的绝对路径。'
  [[ "$(basename -- "$INSTALL_DIR")" != '/' && "$(basename -- "$INSTALL_DIR")" != '.' && "$(basename -- "$INSTALL_DIR")" != '..' ]] || die '安装目录不能是根目录，或以 . / .. 结尾。'
  [[ "$INSTALL_DIR" != *$'\n'* && "$INSTALL_DIR" != *$'\r'* ]] || die '目录不能含换行。'
  [[ -n "$INSTALL_BRANCH" && "$INSTALL_BRANCH" != -* && "$INSTALL_BRANCH" != *$'\n'* ]] || die '分支名无效。'
}

check_directory() {
  [[ ! -L "$INSTALL_DIR" ]] || die '安装目录不能是符号链接。'
  if [[ -e "$INSTALL_DIR" ]]; then
    [[ -d "$INSTALL_DIR" ]] || die '目标路径已存在且不是目录。'
    if [[ ! -e "$INSTALL_DIR/.git" ]] && [[ -n "$(find "$INSTALL_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
      die '目标目录非空且不是项目 Git 仓库；不会覆盖已有文件。'
    fi
  fi
}

check_checkout() {
  [[ -e "$INSTALL_DIR/.git" ]] || return 0
  command -v git >/dev/null || die '已有仓库但缺少 Git，无法安全校验；请先安装 Git，再运行同一命令。'
  local origin branch dirty
  # Use the stored URL, so legitimate git url.insteadOf mirrors remain usable.
  origin="$(git -C "$INSTALL_DIR" config --get remote.origin.url)"
  case "$origin" in
    https://github.com/xiaowork-dev/Automated-Delivery|https://github.com/xiaowork-dev/Automated-Delivery.git|git@github.com:xiaowork-dev/Automated-Delivery.git|ssh://git@github.com/xiaowork-dev/Automated-Delivery.git) ;;
    *) die '现有 origin 不是指定的 Automated-Delivery 仓库；更新中止。' ;;
  esac
  branch="$(git -C "$INSTALL_DIR" symbolic-ref --quiet --short HEAD)" || die '现有仓库是 detached HEAD，请先切回安装分支。'
  [[ "$branch" == "$INSTALL_BRANCH" ]] || die "现有目录位于 $branch；请使用 --branch $branch，不自动切换已有分支。"
  dirty="$(git -C "$INSTALL_DIR" status --porcelain=v1 --untracked-files=normal)"
  [[ -z "$dirty" ]] || die '工作树存在本地修改或未跟踪文件；请先自行保存/提交，安装器不会覆盖。'
}

lock_installation() {
  local parent name lock
  parent="$(dirname -- "$INSTALL_DIR")"
  name="$(basename -- "$INSTALL_DIR")"
  mkdir -p -- "$parent"
  parent="$(cd -- "$parent" && pwd -P)"
  INSTALL_DIR="$parent/$name"
  lock="$parent/.$name.install.lock"
  [[ ! -L "$lock" && ! -L "$lock.d" ]] || die '安装锁路径不能是符号链接。'
  if command -v flock >/dev/null; then
    exec 9> "$lock"
    flock -n 9 || die '另一个安装/更新正在运行，请等待它完成后重试。'
  else
    if ! (umask 077; mkdir -- "$lock.d") 2>/dev/null; then
      die "另一个安装/更新正在运行，或遗留锁 $lock.d 尚未处理；不会强制终止其他任务。"
    fi
    LOCK_DIRECTORY="$lock.d"
  fi
}

read_linux_distribution() {
  [[ "$(uname -s)" == 'Linux' ]] || die '自动安装系统依赖只支持 Linux；隔离验收可使用 --no-deps。'
  [[ -r /etc/os-release ]] || die '无法识别 Linux 发行版，请先自行准备 Git / Docker / Compose 并使用 --no-deps。'
  # The system-owned distribution file is data provided by the operating system.
  source /etc/os-release
  case "${ID:-}" in
    ubuntu|debian) PACKAGE_FAMILY='apt'; DOCKER_DISTRIBUTION="$ID" ;;
    rhel) PACKAGE_FAMILY='rpm'; DOCKER_DISTRIBUTION='rhel' ;;
    centos|rocky|almalinux) PACKAGE_FAMILY='rpm'; DOCKER_DISTRIBUTION='centos' ;;
    fedora) PACKAGE_FAMILY='rpm'; DOCKER_DISTRIBUTION='fedora' ;;
    *) die "自动依赖安装暂不支持 ${ID:-未知发行版}，请准备依赖后使用 --no-deps。" ;;
  esac
}

download_checked() {
  local url="$1" destination="$2"
  curl --fail --show-error --silent --location --retry 3 --connect-timeout 20 \
    --proto '=https' --tlsv1.2 "$url" --output "$destination"
  [[ -s "$destination" ]] || die '官方依赖文件下载为空，中止安装。'
}

has_active_docker_apt_source() {
  local uri="https://download.docker.com/linux/$DOCKER_DISTRIBUTION" file
  # APT ignores backup files, commented lines and disabled Deb822 stanzas.
  for file in /etc/apt/sources.list /etc/apt/sources.list.d/*.list; do
    [[ -f "$file" ]] || continue
    if awk -v uri="$uri" '$1 == "deb" { sub(/#.*/, ""); if (index($0, uri)) found=1 } END { exit !found }' "$file"; then return 0; fi
  done
  for file in /etc/apt/sources.list.d/*.sources; do
    [[ -f "$file" ]] || continue
    if awk -v uri="$uri" '
      BEGIN { RS=""; FS="\n" }
      {
        enabled=1; types=0; repository=0; field=""
        for (line_number=1; line_number<=NF; line_number++) {
          line=$line_number; sub(/#.*/, "", line); sub(/\r$/, "", line)
          lower=tolower(line)
          if (lower ~ /^enabled:[[:space:]]*no[[:space:]]*$/) enabled=0
          if (lower ~ /^types:/ && lower ~ /(^|[[:space:]])deb([[:space:]]|$)/) types=1
          if (lower ~ /^uris:/) field="uris"
          else if (lower ~ /^[^[:space:]][^:]*:/) field=""
          if (field == "uris" && index(line, uri)) repository=1
        }
        if (enabled && types && repository) found=1
      }
      END { exit !found }
    ' "$file"; then return 0; fi
  done
  return 1
}

configure_docker_repository() {
  [[ -n "$INSTALL_TMP" ]] || INSTALL_TMP="$(mktemp -d "${TMPDIR:-/tmp}/automated-delivery-install.XXXXXXXX")"
  if [[ "$PACKAGE_FAMILY" == 'apt' ]]; then
    local codename architecture
    # Reuse an existing official source to avoid conflicting Signed-By settings.
    if has_active_docker_apt_source; then
      DEBIAN_FRONTEND=noninteractive apt-get update
      return 0
    fi
    codename="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"
    [[ "$codename" =~ ^[a-z0-9-]+$ ]] || die '发行版没有有效 VERSION_CODENAME，无法配置官方 Docker 仓库。'
    architecture="$(dpkg --print-architecture)"
    download_checked "https://download.docker.com/linux/$DOCKER_DISTRIBUTION/gpg" "$INSTALL_TMP/docker.asc"
    grep -q 'BEGIN PGP PUBLIC KEY BLOCK' "$INSTALL_TMP/docker.asc" || die 'Docker 官方密钥内容无效。'
    install -d -m 0755 /etc/apt/keyrings
    install -m 0644 "$INSTALL_TMP/docker.asc" /etc/apt/keyrings/docker.asc
    cat > "$INSTALL_TMP/docker.sources" <<EOF
Types: deb
URIs: https://download.docker.com/linux/$DOCKER_DISTRIBUTION
Suites: $codename
Components: stable
Architectures: $architecture
Signed-By: /etc/apt/keyrings/docker.asc
EOF
    install -m 0644 "$INSTALL_TMP/docker.sources" /etc/apt/sources.list.d/docker.sources
    DEBIAN_FRONTEND=noninteractive apt-get update
  else
    download_checked "https://download.docker.com/linux/$DOCKER_DISTRIBUTION/docker-ce.repo" "$INSTALL_TMP/docker-ce.repo"
    grep -q '^\[docker-ce-stable\]' "$INSTALL_TMP/docker-ce.repo" || die 'Docker 官方 RPM 仓库文件内容无效。'
    grep -q '^gpgcheck=1$' "$INSTALL_TMP/docker-ce.repo" || die 'Docker 仓库未启用签名校验。'
    install -m 0644 "$INSTALL_TMP/docker-ce.repo" /etc/yum.repos.d/docker-ce.repo
  fi
}

install_dependencies() {
  if "$NO_DEPS"; then
    command -v git >/dev/null && command -v docker >/dev/null || die '--no-deps 需要已安装 Git 和 Docker。'
    return 0
  fi
  (( EUID == 0 )) || die '自动安装依赖需要 root，请使用 curl ... | sudo bash。'
  read_linux_distribution
  local prerequisites=() need_engine=false need_compose=false
  command -v git >/dev/null || prerequisites+=(git)
  command -v curl >/dev/null || prerequisites+=(curl)
  command -v docker >/dev/null || need_engine=true
  if ! "$need_engine" && ! docker compose version >/dev/null 2>&1; then need_compose=true; fi
  if (( ${#prerequisites[@]} )) || "$need_engine" || "$need_compose"; then
    if [[ "$PACKAGE_FAMILY" == 'apt' ]]; then
      DEBIAN_FRONTEND=noninteractive apt-get update
      DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates curl git
    else
      if command -v dnf >/dev/null; then RPM_MANAGER='dnf'; elif command -v yum >/dev/null; then RPM_MANAGER='yum'; else die '找不到 dnf/yum。'; fi
      "$RPM_MANAGER" install -y ca-certificates curl git
    fi
  fi
  if "$need_engine" || "$need_compose"; then
    configure_docker_repository
    if "$need_engine"; then
      say '从 Docker 官方签名仓库安装缺少的 Engine 和 Compose。'
      if [[ "$PACKAGE_FAMILY" == 'apt' ]]; then
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      else
        "$RPM_MANAGER" install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      fi
    else
      say '已有 Docker Engine，仅补装 Compose 插件。'
      if [[ "$PACKAGE_FAMILY" == 'apt' ]]; then
        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends --no-upgrade docker-compose-plugin
      else
        "$RPM_MANAGER" install -y --exclude=docker-ce --exclude=docker-ce-cli --exclude=containerd.io docker-compose-plugin
      fi
    fi
  fi
  if ! docker info >/dev/null 2>&1; then
    command -v systemctl >/dev/null || die 'Docker 未运行且没有 systemctl，请先启动 Docker。'
    systemctl enable --now docker
  fi
}

verify_dependencies() {
  command -v git >/dev/null && command -v docker >/dev/null || die 'Git 或 Docker 未正确安装。'
  docker info >/dev/null 2>&1 || die 'Docker daemon 不可用，安装/更新中止。'
  docker compose version >/dev/null 2>&1 || die 'Docker Compose 插件不可用。'
  git check-ref-format --branch "$INSTALL_BRANCH" >/dev/null || die 'Git 分支名无效。'
}

select_deployment_mode() {
  local recorded_https=''
  if [[ -f "$INSTALL_DIR/.deployment-state" ]]; then
    recorded_https="$(sed -n 's/^HTTPS=//p' "$INSTALL_DIR/.deployment-state" | tail -1)"
  fi
  USE_HTTPS=false
  case "$HTTPS_MODE" in
    https) USE_HTTPS=true ;;
    auto)
      if [[ "$recorded_https" == 'true' || -e "$INSTALL_DIR/deploy/certs/fullchain.pem" || -e "$INSTALL_DIR/deploy/certs/privkey.pem" ]]; then USE_HTTPS=true; fi ;;
    http) ;;
  esac
  if "$USE_HTTPS"; then
    [[ -s "$INSTALL_DIR/deploy/certs/fullchain.pem" && -s "$INSTALL_DIR/deploy/certs/privkey.pem" ]] || die 'HTTPS 已启用但有效证书文件缺失；拒绝自动降级为 HTTP。'
  fi
}

verify_deployment_health() {
  local service container health
  local files=(-f "$INSTALL_DIR/docker-compose.yml")
  if "$USE_HTTPS"; then files+=(-f "$INSTALL_DIR/deploy/docker-compose.https.yml"); fi
  for service in mysql redis backend frontend; do
    container="$(docker compose --env-file "$INSTALL_DIR/.env" "${files[@]}" ps -q "$service")"
    [[ -n "$container" && "$container" != *$'\n'* ]] || die "服务 $service 容器未正常启动。"
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
    [[ "$health" == 'healthy' ]] || die "服务 $service 未通过健康检查。"
  done
}

main() {
  parse_arguments "$@"
  # Git-created directories must remain readable by the Nginx ACME worker.
  # .env and backups set their own private umask; mktemp state files are 0600.
  umask 022
  export GIT_TERMINAL_PROMPT=0
  export GIT_SSH_COMMAND="${GIT_SSH_COMMAND:-ssh} -o BatchMode=yes"
  trap cleanup EXIT
  trap 'failed "$?"' ERR
  if ! "$NO_DEPS"; then (( EUID == 0 )) || die '请以 root 或 sudo bash 执行安装。'; fi
  check_directory
  check_checkout
  lock_installation
  # Recheck after acquiring the lock, since another installer may have just completed.
  check_directory
  check_checkout
  install_dependencies
  verify_dependencies

  local old_commit='未安装' old_version='未安装' new_commit new_version
  if [[ -e "$INSTALL_DIR/.git" ]]; then
    old_commit="$(git -C "$INSTALL_DIR" rev-parse HEAD)"
    if [[ -f "$INSTALL_DIR/VERSION" ]]; then old_version="$(tr -d '\r\n' < "$INSTALL_DIR/VERSION")"; fi
    select_deployment_mode
    git -C "$INSTALL_DIR" fetch --no-tags origin "+refs/heads/$INSTALL_BRANCH:refs/remotes/origin/$INSTALL_BRANCH"
    git -C "$INSTALL_DIR" merge-base --is-ancestor HEAD "refs/remotes/origin/$INSTALL_BRANCH" || die '本地分支领先或已分叉；拒绝覆盖本地提交，请先自行处理。'
    git -C "$INSTALL_DIR" merge --ff-only --no-edit --no-overwrite-ignore "refs/remotes/origin/$INSTALL_BRANCH"
  else
    git clone --single-branch --branch "$INSTALL_BRANCH" "$REPOSITORY_URL" "$INSTALL_DIR"
    select_deployment_mode
  fi
  [[ -f "$INSTALL_DIR/scripts/deploy.sh" && -f "$INSTALL_DIR/docker-compose.yml" && -f "$INSTALL_DIR/VERSION" ]] || die '拉取的版本缺少必要部署文件。'
  new_commit="$(git -C "$INSTALL_DIR" rev-parse HEAD)"
  new_version="$(tr -d '\r\n' < "$INSTALL_DIR/VERSION")"
  [[ "$new_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]] || die '项目 VERSION 格式无效。'
  printf 'Git 版本：%s → %s\n应用版本：%s → %s\n' "$old_commit" "$new_commit" "$old_version" "$new_version"
  if "$USE_HTTPS"; then
    bash "$INSTALL_DIR/scripts/deploy.sh" --https
  else
    bash "$INSTALL_DIR/scripts/deploy.sh"
  fi
  verify_deployment_health
  STATE_TEMP="$(mktemp "$INSTALL_DIR/.deployment-state.tmp.XXXXXXXX")"
  cat > "$STATE_TEMP" <<EOF
COMMIT=$new_commit
VERSION=$new_version
HTTPS=$USE_HTTPS
BRANCH=$INSTALL_BRANCH
DEPLOYED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
  chmod 600 "$STATE_TEMP"
  mv -f -- "$STATE_TEMP" "$INSTALL_DIR/.deployment-state"
  STATE_TEMP=''
  printf '安装/更新完成，全部服务健康。目录：%s；版本：%s；HTTPS：%s。\n' "$INSTALL_DIR" "$new_version" "$USE_HTTPS"
  say '以后再次运行同一条安装命令即可更新；管理员私密配置位于服务器本地 .env，脚本不输出密钥。'
}

# The whole program is parsed before it runs. Child commands must not consume
# the curl pipe carrying this script, and every operation is non-interactive.
main "$@" </dev/null
