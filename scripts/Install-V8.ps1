[CmdletBinding()]
param(
    [string]$HostName = "localhost",
    [int]$Port = 5435,
    [string]$Database = "shopsphere",
    [string]$Username = "postgres",
    [string]$PgDumpPath = "pg_dump",
    [string]$PsqlPath = "psql"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Migration = Join-Path $ProjectRoot "database\migrations\V8__seller_inventory_and_payment_audit.sql"

if (-not (Test-Path $Migration)) {
    throw "Migration not found."
}

if ([string]::IsNullOrWhiteSpace($env:PGPASSWORD)) {
    throw "PGPASSWORD is not configured."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backup = Join-Path $ProjectRoot "shopsphere-before-v8-$stamp.dump"

Write-Host "Target: $HostName`:$Port / $Database / $Username" -ForegroundColor Cyan
Write-Host "Backup: $backup" -ForegroundColor Cyan

$confirm = Read-Host "Type APPLY to create backup and apply V8"
if ($confirm -cne "APPLY") {
    Write-Host "Cancelled. No database changes made."
    exit 0
}

& $PgDumpPath --host=$HostName --port=$Port --username=$Username --dbname=$Database --format=custom --file=$backup
if ($LASTEXITCODE -ne 0 -or !(Test-Path $backup) -or (Get-Item $backup).Length -lt 1024) {
    throw "Backup creation or size verification failed. Migration NOT applied."
}

& pg_restore --list $backup | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Backup archive validation failed. Migration NOT applied."
}

Write-Host "Backup successfully created and validated." -ForegroundColor Green

& $PsqlPath --host=$HostName --port=$Port --username=$Username --dbname=$Database --set=ON_ERROR_STOP=1 --file=$Migration
if ($LASTEXITCODE -ne 0) {
    throw "Migration failed. SQL transaction should have rolled back. Backup retained at $backup"
}

Write-Host "V8 migration applied successfully." -ForegroundColor Green
Write-Host "Backup retained at: $backup" -ForegroundColor Green

