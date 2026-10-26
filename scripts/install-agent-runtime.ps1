param([string]$PythonExecutable = 'python')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $projectRoot 'agent-runtime'
& $PythonExecutable -c 'import sys; assert sys.version_info[:2] == (3,12), "Use Python 3.12"'
if ($LASTEXITCODE -ne 0) { throw 'Please pass -PythonExecutable with a Python 3.12 executable.' }
& $PythonExecutable -m venv (Join-Path $runtimeRoot '.venv')
if ($LASTEXITCODE -ne 0) { throw 'Virtual environment creation failed.' }
& (Join-Path $runtimeRoot '.venv\Scripts\python.exe') -m pip install --disable-pip-version-check -r (Join-Path $runtimeRoot 'requirements.lock.txt')
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
Write-Output 'Agent runtime installed. No model calls were made.'
