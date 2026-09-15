[CmdletBinding()]
param(
    [string] $PostgresBin = 'C:\Program Files\PostgreSQL\17\bin',
    [int] $Port = 0
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$validationRoot = Join-Path $repoRoot 'target\email-migration-pg'
$runRoot = Join-Path $validationRoot ([guid]::NewGuid().ToString('N'))
$dataDir = Join-Path $runRoot 'data'
$serverLog = Join-Path $runRoot 'postgres.log'
$fixture = Join-Path $repoRoot 'src\test\resources\db\email-migration\legacy_fixture.sql'
$assertions = Join-Path $repoRoot 'src\test\resources\db\email-migration\assertions.sql'
$v44 = Join-Path $repoRoot 'src\main\resources\db\migration\V44__email_sender_profiles.sql'
$v45 = Join-Path $repoRoot 'src\main\resources\db\migration\V45__email_threading.sql'
$initDb = Join-Path $PostgresBin 'initdb.exe'
$pgCtl = Join-Path $PostgresBin 'pg_ctl.exe'
$psql = Join-Path $PostgresBin 'psql.exe'

foreach ($requiredFile in @($initDb, $pgCtl, $psql, $fixture, $assertions, $v44, $v45)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Required file not found: $requiredFile"
    }
}

if ($Port -eq 0) {
    $portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $portProbe.Start()
    $Port = ([Net.IPEndPoint] $portProbe.LocalEndpoint).Port
    $portProbe.Stop()
}

function Invoke-Checked([string] $Executable, [string[]] $Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Executable"
    }
}

$started = $false
$passed = $false
try {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
    Invoke-Checked $initDb @('-D', $dataDir, '-A', 'trust', '-U', 'postgres', '--encoding=UTF8', '--no-locale')
    Invoke-Checked $pgCtl @('-D', $dataDir, '-l', $serverLog, '-o', "-h 127.0.0.1 -p $Port", '-w', 'start')
    $started = $true

    $connection = @('-X', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', "$Port", '-U', 'postgres', '-d', 'postgres')
    Invoke-Checked $psql ($connection + @('-f', $fixture))
    Invoke-Checked $psql ($connection + @('-f', $v44))
    Invoke-Checked $psql ($connection + @('-f', $v45))
    Invoke-Checked $psql ($connection + @('-f', $assertions))

    $passed = $true
    Write-Output "Email migration validation passed on isolated PostgreSQL port $Port."
} finally {
    if ($started) {
        Invoke-Checked $pgCtl @('-D', $dataDir, '-m', 'fast', '-w', 'stop')
        $started = $false
    }

    if ($passed) {
        $validatedRoot = [IO.Path]::GetFullPath($validationRoot) + [IO.Path]::DirectorySeparatorChar
        $validatedRun = [IO.Path]::GetFullPath($runRoot)
        if (-not $validatedRun.StartsWith($validatedRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unexpected validation path: $validatedRun"
        }
        Remove-Item -LiteralPath $validatedRun -Recurse -Force
    } else {
        Write-Warning "Validation files retained for diagnosis: $runRoot"
    }
}
