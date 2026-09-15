param([int]$Port = 8080)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$maven = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $maven) {
    $distribution = Get-Content -LiteralPath '.mvn/wrapper/maven-wrapper.properties' |
        Where-Object { $_ -like 'distributionUrl=*' }
    $distributionName = [IO.Path]::GetFileName($distribution) -replace '-bin.zip$', ''
    $maven = Get-ChildItem -Path "$env:USERPROFILE/.m2/wrapper/dists/$distributionName/*/bin/mvn.cmd" |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $maven) { throw 'Install Maven or run mvnw.cmd once to cache the project Maven distribution.' }

# Compile only after a saved batch has settled. DevTools sees the trigger only
# after a successful compile, never a partially written set of classes.
$watcher = Start-Job -ArgumentList $projectRoot, $maven -ScriptBlock {
    param($projectRoot, $maven)
    Set-Location -LiteralPath $projectRoot
    function Get-SourceStamp {
        (Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main') -File -Recurse |
            Sort-Object FullName |
            ForEach-Object { '{0}|{1}|{2}' -f $_.FullName, $_.Length, $_.LastWriteTimeUtc.Ticks }) -join "`n"
    }
    $previous = Get-SourceStamp
    while ($true) {
        Start-Sleep -Seconds 2
        $current = Get-SourceStamp
        if ($current -eq $previous) { continue }
        Start-Sleep -Seconds 1
        if ((Get-SourceStamp) -ne $current) { continue }
        $previous = $current
        $log = Join-Path $projectRoot 'target/dev-watch.log'
        "[$(Get-Date -Format o)] Source changed; compiling." | Out-File -LiteralPath $log -Append -Encoding utf8
        & $maven '-Dmaven.test.skip=true' compile 2>&1 | Out-File -LiteralPath $log -Append -Encoding utf8
        if ($LASTEXITCODE -eq 0) {
            [IO.File]::WriteAllText((Join-Path $projectRoot 'target/classes/.reloadtrigger'), [DateTime]::UtcNow.Ticks.ToString())
            "[$(Get-Date -Format o)] Updated. DevTools will refresh connected browsers." | Out-File -LiteralPath $log -Append -Encoding utf8
        } else {
            "[$(Get-Date -Format o)] Compile failed; fix the source and save to retry." | Out-File -LiteralPath $log -Append -Encoding utf8
        }
    }
}

try {
    Write-Host "Development server: http://localhost:$Port (watching src/main)"
    & $maven '-Dmaven.test.skip=true' 'spring-boot:run' '-Dspring-boot.run.profiles=local' "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=$Port --app.base-url=http://localhost:$Port --app.mail.resend.api-key="
} finally {
    Stop-Job -Job $watcher
    Remove-Job -Job $watcher
}
