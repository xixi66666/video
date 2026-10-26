$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$recordPath = Join-Path $projectRoot '.run\processes.json'
if (-not (Test-Path -LiteralPath $recordPath)) { Write-Output 'No recorded processes.'; exit 0 }
foreach ($record in Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json) {
    $process = Get-Process -Id $record.pid -ErrorAction SilentlyContinue
    if ($process -and $process.Path -eq $record.executable -and $process.StartTime.ToUniversalTime().Ticks -eq $record.startTicks) {
        Stop-Process -Id $process.Id -ErrorAction Stop
        Write-Output ('Stopped owned process '+$process.Id)
    }
}
Write-Output 'Stopped matching recorded processes only.'
