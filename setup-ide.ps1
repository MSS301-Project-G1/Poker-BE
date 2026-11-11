# Prepare Docker dependencies and generate local IntelliJ Run configurations.
# Run again after changing .env. Application processes are started by IntelliJ.
$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$env:PYTHONIOENCODING = 'utf-8'
Set-Location $PSScriptRoot
$compose = @('-f', 'docker-compose.yml', '-f', 'docker/compose.ide.yml', '--profile', 'mailpit')
$resolved = & docker compose @compose config --format json
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve Docker Compose configuration.' }
$resolved | & python scripts/setup_intellij.py
if ($LASTEXITCODE -ne 0) { throw 'Cannot generate IntelliJ configurations.' }
$model = ($resolved -join "`n") | ConvertFrom-Json
$apps = @($model.services.PSObject.Properties | Where-Object { $_.Value.build } | ForEach-Object { $_.Name })
$infra = @($model.services.PSObject.Properties | Where-Object { -not $_.Value.build } | ForEach-Object { $_.Name })
& docker compose @compose stop @apps
if ($LASTEXITCODE -ne 0) { throw 'Cannot stop Docker application copies.' }
& docker compose @compose up -d --wait --wait-timeout 120 @infra
if ($LASTEXITCODE -ne 0) { throw 'Docker dependencies are not ready.' }
Write-Host 'Dependencies ready. In IntelliJ, select Local - All Services and Run.' -ForegroundColor Green
