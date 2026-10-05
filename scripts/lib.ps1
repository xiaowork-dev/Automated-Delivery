$ErrorActionPreference = 'Stop'
$script:ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:ComposeFiles = @('-f', (Join-Path $script:ProjectRoot 'docker-compose.yml'))

function Assert-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw '请先安装并启动 Docker Desktop（Linux 容器）和 Compose v2。' }
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker 未启动，或当前账号没有访问权限。' }
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) { throw '需要 Docker Compose v2。' }
}

function Invoke-Compose {
    & docker compose --env-file (Join-Path $script:ProjectRoot '.env') @script:ComposeFiles @args
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose 执行失败，退出码 $LASTEXITCODE。" }
}

function New-PrivateSecret {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}

function Protect-PrivateFile([string] $Path) {
    if ($env:OS -eq 'Windows_NT') {
        $acl = New-Object Security.AccessControl.FileSecurity
        $acl.SetAccessRuleProtection($true, $false)
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl.SetOwner($sid)
        foreach ($identity in @($sid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'), [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))) {
            $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'Allow'))
        }
        Set-Acl -LiteralPath $Path -AclObject $acl
    }
}

function Initialize-Environment {
    $environmentPath = Join-Path $script:ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $environmentPath)) {
        $content = @(
            'DB_NAME=auto_delivery', 'DB_USER=autodelivery',
            ('DB_PASSWORD=' + (New-PrivateSecret)),
            ('DB_ROOT_PASSWORD=' + (New-PrivateSecret)),
            ('JWT_SECRET=' + (New-PrivateSecret)), 'ADMIN_USERNAME=admin',
            ('ADMIN_PASSWORD=' + (New-PrivateSecret)),
            'APP_SEED_DEMO=false', 'REDIS_ENABLED=true',
            'APP_BASE_URL=http://localhost', 'HTTP_PORT=80', 'HTTPS_PORT=443',
            'BACKUP_RETENTION_DAYS=7'
        ) -join "`n"
        $file = [IO.File]::Open($environmentPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $writer = [IO.StreamWriter]::new($file, [Text.UTF8Encoding]::new($false))
            try { $writer.Write($content + "`n") } finally { $writer.Dispose() }
        } finally { $file.Dispose() }
        Protect-PrivateFile $environmentPath
        Write-Host '已创建私密 .env，管理员密码由随机数生成。请在本地查看并登录后修改；脚本不输出密钥。'
    }
    if (Select-String -LiteralPath $environmentPath -Pattern '^([A-Z_]+)=.*CHANGE_ME' -Quiet) {
        throw '.env 仍含示例占位值，请填写。'
    }
    Invoke-Compose config --quiet
}

function Enable-Https {
    foreach ($name in @('fullchain.pem', 'privkey.pem')) {
        $path = Join-Path $script:ProjectRoot "deploy/certs/$name"
        if (-not (Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -eq 0) { throw "缺少非空证书 deploy/certs/$name。" }
    }
    $script:ComposeFiles += @('-f', (Join-Path $script:ProjectRoot 'deploy/docker-compose.https.yml'))
}

function Get-BackupRetention {
    $retention = $env:BACKUP_RETENTION_DAYS
    if (-not $retention) {
        $match = Select-String -LiteralPath (Join-Path $script:ProjectRoot '.env') -Pattern '^BACKUP_RETENTION_DAYS=(\d+)$' | Select-Object -Last 1
        if ($match) { $retention = $match.Matches[0].Groups[1].Value }
    }
    if (-not $retention) { $retention = '7' }
    if ($retention -notmatch '^\d+$' -or [int] $retention -lt 7) { throw 'BACKUP_RETENTION_DAYS 必须是至少 7 的整数。' }
    return [int] $retention
}
