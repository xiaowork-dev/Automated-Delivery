. (Join-Path $PSScriptRoot 'lib.ps1')
Assert-Docker
if (-not (Test-Path -LiteralPath (Join-Path $script:ProjectRoot '.env'))) { throw '缺少 .env，请先部署。' }
$container = Invoke-Compose ps --status running -q mysql
if (-not $container) { throw 'MySQL 容器未运行，备份中止。' }
$retention = Get-BackupRetention
$directory = Join-Path $script:ProjectRoot 'backups'
[IO.Directory]::CreateDirectory($directory) | Out-Null
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$nonce = [Guid]::NewGuid().ToString('N')
$baseName = "database-$stamp-$nonce.sql"
$plainFile = Join-Path $directory $baseName
$backupFile = "$plainFile.gz"
$temporaryFile = "$backupFile.partial"
$containerFile = "/tmp/$baseName"
try {
    # docker cp preserves bytes: PowerShell 5.1 native stdout redirection can corrupt UTF-8 SQL.
    $command = 'umask 077; MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --user="$MYSQL_USER" --single-transaction --no-tablespaces --default-character-set=utf8mb4 "$MYSQL_DATABASE" > ' + $containerFile
    Invoke-Compose exec -T mysql sh -c $command
    & docker cp "${container}:$containerFile" $plainFile
    if ($LASTEXITCODE -ne 0) { throw '无法复制数据库备份。' }
    Protect-PrivateFile $plainFile
    $source = [IO.File]::OpenRead($plainFile)
    try {
        $target = [IO.File]::Create($temporaryFile)
        try {
            $gzip = [IO.Compression.GZipStream]::new($target, [IO.Compression.CompressionMode]::Compress)
            try { $source.CopyTo($gzip) } finally { $gzip.Dispose() }
        } finally { $target.Dispose() }
    } finally { $source.Dispose() }
    Move-Item -LiteralPath $temporaryFile -Destination $backupFile
    Protect-PrivateFile $backupFile
    $hash = (Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$backupFile.sha256", "$hash  $baseName.gz`n", [Text.UTF8Encoding]::new($false))
    $resolvedDirectory = [IO.Path]::GetFullPath($directory).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    Get-ChildItem -LiteralPath $directory -File | Where-Object {
        $_.Name -match '^database-.*\.sql\.gz(\.sha256)?$' -and $_.LastWriteTimeUtc -lt [DateTime]::UtcNow.AddDays(-$retention)
    } | ForEach-Object {
        $resolvedFile = [IO.Path]::GetFullPath($_.FullName)
        if (-not $resolvedFile.StartsWith($resolvedDirectory, [StringComparison]::OrdinalIgnoreCase)) { throw '备份清理路径超出项目备份目录。' }
        Remove-Item -LiteralPath $resolvedFile
    }
    Write-Host "备份已保存：$backupFile"
} finally {
    foreach ($path in @($plainFile, $temporaryFile)) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path }
    }
    & docker compose --env-file (Join-Path $script:ProjectRoot '.env') @script:ComposeFiles exec -T mysql rm -f $containerFile
}
