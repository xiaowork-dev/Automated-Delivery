param([Parameter(Mandatory = $true)][string] $Backup, [switch] $Confirm)
. (Join-Path $PSScriptRoot 'lib.ps1')
if (-not $Confirm) { throw '恢复会覆盖同名表。确认后添加 -Confirm；脚本会先保存当前数据库备份。' }
$restorePath = (Resolve-Path -LiteralPath $Backup).Path
if ($restorePath -notlike '*.sql.gz') { throw '请选择 .sql.gz 数据库备份。' }
Assert-Docker
if (-not (Test-Path -LiteralPath (Join-Path $script:ProjectRoot '.env'))) { throw '缺少 .env。' }
if (Test-Path -LiteralPath "$restorePath.sha256") {
    $expected = (([IO.File]::ReadAllText("$restorePath.sha256")) -split '\s+')[0]
    $actual = (Get-FileHash -LiteralPath $restorePath -Algorithm SHA256).Hash
    if ($actual -ne $expected) { throw '备份 SHA256 校验失败，恢复中止。' }
}
$directory = Join-Path $script:ProjectRoot 'backups'
[IO.Directory]::CreateDirectory($directory) | Out-Null
$name = 'restore-' + [Guid]::NewGuid().ToString('N') + '.sql'
$plainFile = Join-Path $directory $name
$containerFile = "/tmp/$name"
$container = $null
try {
    $source = [IO.File]::OpenRead($restorePath)
    try {
        $gzip = [IO.Compression.GZipStream]::new($source, [IO.Compression.CompressionMode]::Decompress)
        try {
            $target = [IO.File]::Create($plainFile)
            try { $gzip.CopyTo($target) } finally { $target.Dispose() }
        } finally { $gzip.Dispose() }
    } finally { $source.Dispose() }
    Protect-PrivateFile $plainFile
    Invoke-Compose up -d --wait --wait-timeout 240 mysql
    & (Join-Path $PSScriptRoot 'backup.ps1')
    $container = Invoke-Compose ps --status running -q mysql
    Invoke-Compose stop frontend backend
    & docker cp $plainFile "${container}:$containerFile"
    if ($LASTEXITCODE -ne 0) { throw '无法复制待恢复的 SQL，服务保持停止。' }
    $command = 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user="$MYSQL_USER" --default-character-set=utf8mb4 "$MYSQL_DATABASE" < ' + $containerFile
    Invoke-Compose exec -T mysql sh -c $command
    Invoke-Compose start backend frontend
    $healthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker compose --env-file (Join-Path $script:ProjectRoot '.env') @script:ComposeFiles exec -T backend curl --fail --silent http://localhost:8080/actuator/health *> $null
        if ($LASTEXITCODE -eq 0) { $healthy = $true; break }
        Start-Sleep -Seconds 3
    }
    if (-not $healthy) { throw 'SQL 已恢复，但后端健康检查超时。请检查 docker compose logs backend。' }
    Write-Host '恢复完成，后端健康检查通过。请执行交易验收。'
} catch {
    Write-Warning '恢复失败时请检查服务状态，并使用升级前备份重试。'
    throw
} finally {
    if (Test-Path -LiteralPath $plainFile) { Remove-Item -LiteralPath $plainFile }
    if ($container) { & docker exec $container rm -f $containerFile }
}
