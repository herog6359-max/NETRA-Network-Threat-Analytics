<#
.SYNOPSIS
    NETRA Local PostgreSQL Setup and Verification Script
.DESCRIPTION
    Checks for an existing PostgreSQL instance, starts the service if stopped,
    or guides/automates local installation via winget/choco, and initializes the netra database.
#>

param(
    [string]$DbName = "netra",
    [string]$DbUser = $(if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { "postgres" }),
    [string]$DbPass = $env:SPRING_DATASOURCE_PASSWORD,
    [int]$Port = 5432
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   NETRA - PostgreSQL Local Environment Setup & Check   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Check if port is already active
Write-Host "`n[1/4] Checking PostgreSQL on localhost:$Port..." -ForegroundColor Yellow
$portCheck = Test-NetConnection -ComputerName localhost -Port $Port -WarningAction SilentlyContinue

if ($portCheck.TcpTestSucceeded) {
    Write-Host " [+] PostgreSQL is active and listening on port $Port!" -ForegroundColor Green
} else {
    Write-Host " [-] Nothing is listening on port $Port." -ForegroundColor Red
    
    # Check portable instance first
    $scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
    $projectRoot = Split-Path -Parent $scriptRoot
    $portableBin = Join-Path $projectRoot "tools\pgsql\bin"
    $portableData = Join-Path $projectRoot "tools\pgsql\data"
    $portablePgCtl = Join-Path $portableBin "pg_ctl.exe"

    if (Test-Path $portablePgCtl) {
        Write-Host " [+] Found NETRA local portable PostgreSQL in tools\pgsql" -ForegroundColor Green
        if (-not $portCheck.TcpTestSucceeded) {
            Write-Host " Starting local portable PostgreSQL on port $Port..." -ForegroundColor Yellow
            Start-Process -FilePath $portablePgCtl -ArgumentList "-D `"$portableData`" -l `"$projectRoot\tools\pgsql\server.log`" start" -NoNewWindow
            Start-Sleep -Seconds 3
        }
    } else {
        # Check Windows Service
        Write-Host "`n[2/4] Checking for installed Windows PostgreSQL services..." -ForegroundColor Yellow
        $pgServices = Get-Service -Name "*postgres*" -ErrorAction SilentlyContinue
        if ($pgServices) {
            foreach ($svc in $pgServices) {
                Write-Host " Found service: $($svc.Name) (Status: $($svc.Status))"
                if ($svc.Status -ne "Running") {
                    Write-Host " Attempting to start service $($svc.Name)..." -ForegroundColor Yellow
                    try {
                        Start-Service $svc.Name
                        Start-Sleep -Seconds 3
                        Write-Host " Service started successfully!" -ForegroundColor Green
                    } catch {
                        Write-Host " Note: Starting Windows service requires Administrator privileges." -ForegroundColor Magenta
                    }
                }
            }
        } else {
            Write-Host " [-] No PostgreSQL Windows service detected." -ForegroundColor Yellow
        }
    }
}

# 3. Locate psql / createdb CLI tools
Write-Host "`n[4/4] Locating PostgreSQL CLI tools (psql)..." -ForegroundColor Yellow
$psqlPath = $null
if (Get-Command psql -ErrorAction SilentlyContinue) {
    $psqlPath = (Get-Command psql).Source
} else {
    # Check common installation locations
    $candidates = @(
        "$PSScriptRoot\..\tools\pgsql\bin\psql.exe",
        "C:\Program Files\PostgreSQL\17\bin\psql.exe",
        "C:\Program Files\PostgreSQL\16\bin\psql.exe",
        "C:\Program Files\PostgreSQL\15\bin\psql.exe",
        "C:\ProgramData\chocolatey\lib\postgresql\tools\pgsql\bin\psql.exe"
    )
    foreach ($cand in $candidates) {
        if (Test-Path $cand) {
            $psqlPath = $cand
            break
        }
    }
}

if ($psqlPath) {
    Write-Host " [+] Found psql: $psqlPath" -ForegroundColor Green
    Write-Host " Ensuring '$DbName' database exists..." -ForegroundColor Yellow
    
    if (-not $DbPass) { throw "Set SPRING_DATASOURCE_PASSWORD before running database setup." }
    $previousPassword = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $DbPass
        $checkDb = & $psqlPath -h localhost -p $Port -U $DbUser -d postgres -t -c "SELECT 1 FROM pg_database WHERE datname='$DbName'"

        if ($LASTEXITCODE -ne 0) { throw "PostgreSQL connection failed." }
        if (-not $checkDb -or $checkDb.Trim() -ne "1") {
            Write-Host " Creating database '$DbName'..." -ForegroundColor Yellow
            & $psqlPath -h localhost -p $Port -U $DbUser -d postgres -c "CREATE DATABASE $DbName;"
            if ($LASTEXITCODE -eq 0) {
                Write-Host " [+] Database '$DbName' created successfully!" -ForegroundColor Green
            } else {
                Write-Host " [!] Could not create database '$DbName'. Verify that PostgreSQL is running and that the supplied credentials are correct." -ForegroundColor Red
                exit $LASTEXITCODE
            }
        } else {
            Write-Host " [+] Database '$DbName' already exists." -ForegroundColor Green
        }
    } finally {
        $env:PGPASSWORD = $previousPassword
    }
} else {
    Write-Host " [!] 'psql' not currently in PATH. Once PostgreSQL is installed, ensure bin/ is added to PATH." -ForegroundColor Yellow
}

Write-Host "`nNETRA Configuration Summary:" -ForegroundColor Cyan
Write-Host "  JDBC URL: jdbc:postgresql://localhost:5432/$DbName" -ForegroundColor White
Write-Host "  User:     $DbUser" -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Cyan
