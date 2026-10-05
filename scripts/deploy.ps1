param([switch] $Https)
. (Join-Path $PSScriptRoot 'lib.ps1')

Assert-Docker
Initialize-Environment
if ($Https) { Enable-Https }
Invoke-Compose config --quiet
Write-Host '构建应用镜像……'
Invoke-Compose build --pull

Write-Host '启动数据库，并在应用更新前备份（首次部署也保存初始库）……'
Invoke-Compose up -d --wait --wait-timeout 240 mysql
& (Join-Path $PSScriptRoot 'backup.ps1')

Invoke-Compose up -d --wait --wait-timeout 300
Invoke-Compose exec -T frontend nginx -t
Write-Host '部署完成。请通过浏览器执行交易验收；脚本不会输出密钥。'
Invoke-Compose ps
