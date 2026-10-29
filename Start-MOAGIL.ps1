$ErrorActionPreference = 'Stop'

$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCommand) {
    $pythonLauncher = Get-Command py -ErrorAction SilentlyContinue
    if ($pythonLauncher) {
        & $pythonLauncher.Source -3 --version
        if ($LASTEXITCODE -eq 0) {
            & $pythonLauncher.Source -3 (Join-Path $PSScriptRoot 'backend/server.py')
            exit $LASTEXITCODE
        }
    }
    throw 'Python 3.12 이상이 필요합니다. https://www.python.org/downloads/ 에서 설치한 뒤 다시 실행해 주세요.'
}

$pythonPath = $pythonCommand.Source
if ($pythonPath -like '*WindowsApps*') {
    $bundledPython = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
    if (Test-Path -LiteralPath $bundledPython) { $pythonPath = $bundledPython }
}

& $pythonPath --version
if ($LASTEXITCODE -ne 0) { throw 'Python을 실행할 수 없습니다.' }
& $pythonPath (Join-Path $PSScriptRoot 'backend/server.py')
exit $LASTEXITCODE
