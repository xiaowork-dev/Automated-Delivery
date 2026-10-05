param([Parameter(Mandatory = $true)][string] $Version, [Parameter(Mandatory = $true)][string] $Message)
. (Join-Path $PSScriptRoot 'lib.ps1')
if ($Version -notmatch '^\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?$') { throw '版本号格式应为 1.0.1。' }
function Invoke-Checked {
    param(
        [Parameter(Position = 0, Mandatory = $true)][string] $Command,
        [Parameter(Position = 1, ValueFromRemainingArguments = $true)][string[]] $Arguments
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command 失败，退出码 $LASTEXITCODE。" }
}
Push-Location $script:ProjectRoot
try {
    foreach ($command in @('git', 'mvn', 'npm', 'python')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "需要 $command（以及 Java 21）。" }
    }
    $branch = & git branch --show-current
    if ($LASTEXITCODE -ne 0 -or -not $branch) { throw '请先切换到一个 Git 分支。' }
    $remote = & git remote get-url origin
    if ($LASTEXITCODE -ne 0 -or $remote -notmatch 'github\.com[:/]xiaowork-dev/Automated-Delivery(\.git)?$') { throw 'origin 必须指向用户指定的仓库。' }
    & git rev-parse --verify --quiet "refs/tags/v$Version" *> $null
    if ($LASTEXITCODE -eq 0) { throw '该版本标签已存在。' }
    if (([IO.File]::ReadAllText((Join-Path $script:ProjectRoot 'VERSION'))).Trim() -eq $Version) { throw '版本号应不同于当前 VERSION。' }
    Invoke-Checked python @('scripts/set-version.py', $Version)
    Push-Location backend
    try { Invoke-Checked mvn @('-B', 'verify') } finally { Pop-Location }
    Push-Location frontend
    try {
        Invoke-Checked npm @('ci')
        Invoke-Checked npm @('run', 'build')
    } finally { Pop-Location }
    if ($env:TEST_BASE_URL) { Invoke-Checked python @('scripts/api-acceptance.py') }
    Invoke-Checked git @('diff', '--check')
    Invoke-Checked git @('add', '--all')
    Invoke-Checked python @('scripts/check-release.py')
    Write-Host '本版本提交文件：'
    Invoke-Checked git @('diff', '--cached', '--name-status')
    Invoke-Checked git @('commit', '-m', $Message)
    Invoke-Checked git @('tag', '-a', "v$Version", '-m', "Version $Version")
    Invoke-Checked git @('push', '--atomic', '--set-upstream', 'origin', "HEAD:refs/heads/$branch", "refs/tags/v$Version")
    Write-Host "版本 $Version 已提交并推送。"
} finally { Pop-Location }
